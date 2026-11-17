package com.liana.post.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class UnpackingConfirmationServiceTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private UnpackingConfirmationService service;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        String ddl = Files.readString(Path.of("../database/migrations/oms_db/V4__unpacking_confirmation_inbox.sql"))
                .replaceAll("(?m)^--.*$", "").replaceAll(" COLLATE[= ]\\w+", "")
                .replaceAll(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "").replaceAll("\\bJSON\\b", "VARCHAR(1000000)");
        for (String statement : ddl.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        jdbc.execute("CREATE TABLE mail (waybill_no VARCHAR(32) PRIMARY KEY,bag_no VARCHAR(32),package_id VARCHAR(32),status VARCHAR(32),current_facility_code VARCHAR(64),current_slot VARCHAR(64),updated_at TIMESTAMP(3))");
        for (String no : List.of("CP000000001LN", "CP000000002LN")) jdbc.update("INSERT INTO mail VALUES (?,'B1','B1','DISPATCHED','LN-OR-01','OLD',CURRENT_TIMESTAMP)", no);
        service = new UnpackingConfirmationService(jdbc, json, null);
    }
    @Test
    void scannedMailMovesAfterSealedLegsButMissingMailKeepsOriginalBag() {
        var ack = accept(command("E1", "CP000000001LN"));
        assertNotNull(ack.appliedAt()); assertEquals("LN-OR-RC", field("CP000000001LN", "current_facility_code"));
        assertNull(field("CP000000001LN", "bag_no")); assertNull(field("CP000000001LN", "current_slot"));
        assertEquals("SORTED", field("CP000000001LN", "status")); assertEquals("B1", field("CP000000002LN", "bag_no"));
    }
    @Test
    void changedOwnershipRollsBackEarlierDetachAndInbox() {
        jdbc.update("UPDATE mail SET bag_no='OTHER',package_id='OTHER' WHERE waybill_no='CP000000002LN'");
        assertThrows(BusinessException.class, () -> accept(command("E1", "CP000000001LN", "CP000000002LN")));
        assertEquals("B1", field("CP000000001LN", "bag_no"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oms_unpacking_inbox", Integer.class));
    }
    @Test
    void lostAcknowledgementReplaysImmutableReceiptAfterMailHasBeenRepacked() throws Exception {
        var command = command("E1", "CP000000001LN"); var ack = accept(command);
        jdbc.update("UPDATE mail SET bag_no='B2',package_id='B2',status='DISPATCHED',current_facility_code='LN-NAM-HQ'");
        assertEquals(json.writeValueAsString(ack), json.writeValueAsString(accept(command)));
        assertEquals("B2", field("CP000000001LN", "bag_no"));
        assertThrows(BusinessException.class, () -> accept(command("E2", "CP000000001LN")));
        assertThrows(BusinessException.class, () -> accept(command("E1", "CP000000002LN")));
    }
    @Test
    void emptyActualSetDoesNotDetachExpectedMail() {
        assertNotNull(accept(command("E1")).appliedAt());
        assertEquals("B1", field("CP000000001LN", "bag_no"));
    }
    @Test
    void concurrentSameEventReturnsOneReceipt() throws Exception {
        var command = command("E1", "CP000000001LN"); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> accept(command)); var second = pool.submit(() -> accept(command));
            assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_unpacking_inbox", Integer.class));
        } finally { pool.shutdownNow(); }
    }
    private UnpackingConfirmation command(String event, String... mail) {
        return new UnpackingConfirmation(event, "M1", "B1", "LN-OR-RC", "SORTER", Instant.parse("2026-11-17T10:00:00Z"), List.of(mail));
    }
    private UnpackingConfirmation.Ack accept(UnpackingConfirmation command) { return tx.execute(s -> service.accept(command)); }
    private String field(String no, String field) { return jdbc.queryForObject("SELECT " + field + " FROM mail WHERE waybill_no=?", String.class, no); }
}
