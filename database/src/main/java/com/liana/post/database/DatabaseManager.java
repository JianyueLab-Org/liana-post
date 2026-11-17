package com.liana.post.database;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Explicit deployment tool. It is never loaded by a business-service application. */
public final class DatabaseManager {
    // Dependency order is intentional. Cross-database writes are not needed by migrations or seeds.
    static final List<String> DATABASES = List.of("auth_db", "facility_db", "oms_db", "dispatch_db",
            "sorting_db", "transport_db", "tracking_db", "syncer_db", "postage_db");
    private static final Set<String> COMMANDS = Set.of("migrate", "validate", "seed-demo", "check", "initialize-demo");
    private final Settings settings;

    DatabaseManager(Settings settings) {
        this.settings = settings;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !COMMANDS.contains(args[0])) {
            throw new IllegalArgumentException("Usage: migrate | validate | seed-demo | check | initialize-demo. No reset/clean operation is provided.");
        }
        new DatabaseManager(Settings.fromEnvironment(System.getenv())).run(args[0]);
    }

    void run(String command) throws Exception {
        System.out.printf("Database operation %s on %s:%s; schemas=%s%n",
                command, settings.host(), settings.port(), DATABASES);
        try (var lock = connect("")) {
            try (var statement = lock.createStatement();
                 var result = statement.executeQuery("SELECT GET_LOCK('liana_database_initialization', 30)")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("Another database initialization is running");
                }
            }
            try {
                switch (command) {
                    case "initialize-demo" -> {
                        migrate();
                        validate();
                        seedDemo();
                        check();
                    }
                    case "migrate" -> migrate();
                    case "validate" -> validate();
                    case "seed-demo" -> {
                        validate();
                        seedDemo();
                    }
                    case "check" -> {
                        validate();
                        check();
                    }
                    default -> throw new IllegalArgumentException("Unknown operation");
                }
            } finally {
                try (var statement = lock.createStatement()) {
                    statement.execute("SELECT RELEASE_LOCK('liana_database_initialization')");
                }
            }
        }
    }

    private void migrate() throws SQLException {
        // Check ALL schemas before modifying any, so a legacy schema never triggers a partial conversion.
        try (var connection = connect("")) {
            for (String database : DATABASES) {
                try (var statement = connection.prepareStatement("SELECT COUNT(*), "
                        + "COALESCE(SUM(table_name = 'flyway_schema_history'), 0) "
                        + "FROM information_schema.tables WHERE table_schema = ?")) {
                    statement.setString(1, database);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        if (result.getInt(1) > 0 && result.getInt(2) == 0) {
                            throw new IllegalStateException("Refusing unversioned nonempty schema: " + database
                                    + ". Use an isolated empty server; legacy demo reset is a separate operation.");
                        }
                    }
                }
            }
            for (String database : DATABASES) {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE IF NOT EXISTS `" + database
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                flyway(database).migrate();
            }
        }
    }

    private void validate() {
        for (String database : DATABASES) {
            var migration = flyway(database);
            if (migration.info().current() == null) {
                throw new IllegalStateException("Schema has not been migrated: " + database);
            }
            migration.validate();
            if (migration.info().pending().length != 0) {
                throw new IllegalStateException("Pending migrations in " + database + "; run migrate first");
            }
        }
    }

    private Flyway flyway(String database) {
        return Flyway.configure().dataSource(settings.url(database), settings.user(), settings.password())
                .locations("classpath:migrations/" + database).encoding(StandardCharsets.UTF_8)
                .baselineOnMigrate(false).cleanDisabled(true).createSchemas(false)
                .validateMigrationNaming(true).validateOnMigrate(true).outOfOrder(false)
                .initSql("SET time_zone = '+00:00'").load();
    }

    private void seedDemo() throws Exception {
        for (String database : DATABASES) {
            String resource = "seeds/demo/v1/" + database + ".sql";
            byte[] source = readOptionalResource(resource);
            if (source == null) {
                continue;
            }
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
            try (var connection = connect(database)) {
                connection.setAutoCommit(false);
                try {
                    String previous = previousSeedChecksum(connection);
                    if (previous != null) {
                        if (!previous.equals(checksum)) {
                            throw new IllegalStateException("Demo seed checksum mismatch: " + resource);
                        }
                        System.out.println("Verified existing demo/v1: " + database);
                        connection.rollback();
                        continue;
                    }
                    String sql = new String(source, StandardCharsets.UTF_8);
                    if (sql.contains("${demo_password_hash}")) {
                        String password = settings.demoPassword();
                        if (password == null || password.length() < 8
                                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                            throw new IllegalArgumentException("Set LIANA_DEMO_PASSWORD (at least 8 characters, at most 72 UTF-8 bytes) for first demo account import");
                        }
                        sql = sql.replace("${demo_password_hash}", new BCryptPasswordEncoder().encode(password));
                    }
                    ScriptUtils.executeSqlScript(connection,
                            new org.springframework.core.io.support.EncodedResource(
                                    new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
                    try (var statement = connection.prepareStatement(
                            "INSERT INTO demo_seed_history (seed_version, checksum) VALUES ('demo/v1', ?)")) {
                        statement.setString(1, checksum);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    System.out.println("Imported demo/v1: " + database);
                } catch (Exception error) {
                    connection.rollback();
                    throw error;
                }
            }
        }
    }

    private String previousSeedChecksum(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT checksum FROM demo_seed_history WHERE seed_version = 'demo/v1'")) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private void check() throws Exception {
        // Each check is a SELECT count of violations. Checks may read across schemas, never write across them.
        String source = new String(readRequiredResource("checks/invariants.sql"), StandardCharsets.UTF_8);
        try (var connection = connect("")) {
            for (String check : source.split(";")) {
                if (check.isBlank()) {
                    continue;
                }
                try (var statement = connection.createStatement(); var result = statement.executeQuery(check)) {
                    if (!result.next() || result.getLong(1) != 0) {
                        throw new IllegalStateException("Database invariant failed: " + check.strip());
                    }
                }
            }
        }
        System.out.println("All database invariants passed");
    }

    Connection connect(String database) throws SQLException {
        var properties = new Properties();
        properties.setProperty("user", settings.user());
        properties.setProperty("password", settings.password());
        var connection = DriverManager.getConnection(settings.url(database), properties);
        try (var statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+00:00'");
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
        return connection;
    }

    private static byte[] readRequiredResource(String name) throws IOException {
        byte[] bytes = readOptionalResource(name);
        if (bytes == null) {
            throw new IOException("Missing database resource: " + name);
        }
        return bytes;
    }

    private static byte[] readOptionalResource(String name) throws IOException {
        try (var input = DatabaseManager.class.getClassLoader().getResourceAsStream(name)) {
            return input == null ? null : input.readAllBytes();
        }
    }

    record Settings(String host, int port, String user, String password, String demoPassword) {
        static Settings fromEnvironment(Map<String, String> environment) {
            String host = required(environment, "LIANA_DB_HOST");
            if (!host.matches("[a-zA-Z0-9.-]+")) {
                throw new IllegalArgumentException("LIANA_DB_HOST must be a hostname or IPv4 address");
            }
            int port = Integer.parseInt(required(environment, "LIANA_DB_PORT"));
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Invalid LIANA_DB_PORT");
            }
            return new Settings(host, port, required(environment, "LIANA_DB_USER"),
                    required(environment, "LIANA_DB_PASSWORD"), environment.get("LIANA_DEMO_PASSWORD"));
        }

        String url(String database) {
            if (!database.isEmpty() && !DATABASES.contains(database)) {
                throw new IllegalArgumentException("Unknown schema: " + database);
            }
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                    + "&connectTimeout=10000&socketTimeout=60000";
        }

        private static String required(Map<String, String> environment, String key) {
            String value = environment.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing explicit connection setting: " + key);
            }
            return value;
        }
    }
}
