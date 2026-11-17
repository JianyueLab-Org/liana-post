package com.liana.post.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/** Requires an explicitly opted-in EMPTY MySQL 8 server, never the existing demo server. */
@EnabledIfEnvironmentVariable(named = "LIANA_DB_TEST", matches = "true")
class DatabaseMySqlTest {
    @Test
    void freshInitializationIsCompleteRepeatableAndPreservesAdministratorEdits() throws Exception {
        var settings = DatabaseManager.Settings.fromEnvironment(System.getenv());
        var manager = new DatabaseManager(settings);
        try (var connection = manager.connect("")) {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN "
                    + "('auth_db','facility_db','oms_db','dispatch_db','sorting_db','transport_db','tracking_db','syncer_db','postage_db')"),
                    "Test needs an empty dedicated MySQL server");
            execute(connection, "CREATE DATABASE sorting_db");
            execute(connection, "CREATE TABLE sorting_db.p0_unversioned_sentinel (id INT PRIMARY KEY)");
            assertThrows(IllegalStateException.class, () -> manager.run("migrate"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='auth_db'"));
            execute(connection, "DROP TABLE sorting_db.p0_unversioned_sentinel");
        }
        manager.run("migrate");
        manager.run("migrate");
        manager.run("check");
        try (var connection = manager.connect("")) {
            // 87 business tables + nine demo-seed ledgers + nine Flyway ledgers.
            assertEquals(105, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN "
                    + "('auth_db','facility_db','oms_db','dispatch_db','sorting_db','transport_db','tracking_db','syncer_db','postage_db')"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM oms_db.mail"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM tracking_db.tracking_event"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM transport_db.transport_task"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sorting_db.sorting_total_package"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dispatch_db.dispatch_bag_packing"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM oms_db.oms_packing_inbox"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sorting_db.sorting_manifest_seal"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sorting_db.sorting_manifest_cancel"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM transport_db.transport_manifest_assignment"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM transport_db.transport_bag_current"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dispatch_db.dispatch_assignment_inbox"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sorting_db.sorting_receive_request"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sorting_db.sorting_bag_receipt"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sorting_db.sorting_bag_position"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sorting_db.sorting_receipt_outbox"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM transport_db.transport_receipt_inbox"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dispatch_db.dispatch_receipt_inbox"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='dispatch_db' "
                    + "AND table_name='dispatch_manifest_outbox' AND column_name='event_type' AND column_default='PREALERT'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='dispatch_db' "
                    + "AND table_name='dispatch_bag' AND column_name='total_weight_grams' AND is_nullable='YES' AND column_default IS NULL"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema LIKE '%\\_db' "
                    + "AND table_schema NOT IN ('mysql','sys','performance_schema','information_schema') AND engine <> 'InnoDB'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='transport_db' "
                    + "AND table_name='transport_task_bag' AND column_name='linked_at' AND datetime_precision=3"));
        }
        manager.run("seed-demo");
        String originalPasswordHash;
        try (var connection = manager.connect("")) {
            assertEquals(15, count(connection, "SELECT COUNT(*) FROM facility_db.facility"));
            assertEquals(12, count(connection, "SELECT COUNT(*) FROM facility_db.demo_postal_island"));
            assertEquals(8, count(connection, "SELECT COUNT(*) FROM auth_db.user"));
            assertEquals(12, count(connection, "SELECT COUNT(*) FROM transport_db.transport_schedule"));
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT password_hash FROM auth_db.user WHERE username='admin001'")) {
                assertTrue(result.next());
                originalPasswordHash = result.getString(1);
                assertTrue(new BCryptPasswordEncoder().matches(settings.demoPassword(), originalPasswordHash));
            }
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM transport_db.transport_schedule WHERE "
                    + "DATE(departure_time + INTERVAL 12 HOUR) <> DATE(UTC_TIMESTAMP() + INTERVAL 12 HOUR) + INTERVAL 1 DAY"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM facility_db.facility_route WHERE "
                    + "(origin_facility_code IN ('LN-OR-02','LN-SE-02') OR destination_facility_code IN ('LN-OR-02','LN-SE-02')) "
                    + "AND transport_mode <> 'SEA'"));
            execute(connection, "UPDATE facility_db.facility SET name='Administrator changed name' WHERE facility_code='LN-OR-01'");
            execute(connection, "UPDATE postage_db.postage_stamp_policy SET revision=2,max_face_value=50.00 WHERE id=1");
            execute(connection, "UPDATE auth_db.user SET password_hash='administrator-replaced-hash' WHERE username='admin001'");
        }
        // Re-running deployment never resets operator changes or adds demo operational facts.
        manager.run("migrate");
        manager.run("seed-demo");
        manager.run("check");
        try (var connection = manager.connect("")) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM facility_db.facility WHERE name='Administrator changed name'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM postage_db.postage_stamp_policy WHERE revision=2 AND max_face_value=50.00"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM auth_db.user WHERE password_hash='administrator-replaced-hash'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM oms_db.mail"));
            String checksum;
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT checksum FROM auth_db.demo_seed_history WHERE seed_version='demo/v1'")) {
                assertTrue(result.next());
                checksum = result.getString(1);
            }
            execute(connection, "UPDATE auth_db.demo_seed_history SET checksum=REPEAT('0',64) WHERE seed_version='demo/v1'");
            assertThrows(IllegalStateException.class, () -> manager.run("seed-demo"));
            // Leave the isolated server's seed history consistent for later diagnostics.
            execute(connection, "DELETE FROM auth_db.demo_seed_history WHERE seed_version='demo/v1'");
            assertThrows(RuntimeException.class, () -> manager.run("seed-demo"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM auth_db.demo_seed_history"));
            assertEquals(8, count(connection, "SELECT COUNT(*) FROM auth_db.user"), "A conflicting first import must roll back");
            try (var statement = connection.prepareStatement(
                    "INSERT INTO auth_db.demo_seed_history (seed_version,checksum) VALUES ('demo/v1',?)")) {
                statement.setString(1, checksum);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement(
                    "UPDATE auth_db.user SET password_hash=? WHERE username='admin001'")) {
                statement.setString(1, originalPasswordHash);
                statement.executeUpdate();
            }
        }
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
