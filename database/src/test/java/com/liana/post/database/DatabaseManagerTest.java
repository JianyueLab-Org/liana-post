package com.liana.post.database;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    @Test
    void connectionRequiresExplicitTargetAndRejectsUrlInjection() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseManager.Settings.fromEnvironment(Map.of()));
        var environment = new java.util.HashMap<>(Map.of("LIANA_DB_HOST", "127.0.0.1", "LIANA_DB_PORT", "13317",
                "LIANA_DB_USER", "root", "LIANA_DB_PASSWORD", "isolated"));
        var settings = DatabaseManager.Settings.fromEnvironment(environment);
        assertTrue(settings.url("sorting_db").startsWith("jdbc:mysql://127.0.0.1:13317/sorting_db?"));
        assertTrue(settings.url("sorting_db").contains("forceConnectionTimeZoneToSession=true"));
        assertTrue(settings.url("sorting_db").contains("connectTimeout=10000&socketTimeout=60000"));
        assertThrows(IllegalArgumentException.class, () -> settings.url("mysql"));
        environment.put("LIANA_DB_HOST", "localhost/other?useSSL=false");
        assertThrows(IllegalArgumentException.class, () -> DatabaseManager.Settings.fromEnvironment(environment));
        environment.put("LIANA_DB_HOST", "127.0.0.1");
        environment.put("LIANA_DB_PORT", "0");
        assertThrows(IllegalArgumentException.class, () -> DatabaseManager.Settings.fromEnvironment(environment));
    }

    @Test
    void unknownOrDestructiveCommandIsRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseManager.main(new String[]{"clean"}));
        assertThrows(IllegalArgumentException.class, () -> DatabaseManager.main(new String[]{}));
    }
}
