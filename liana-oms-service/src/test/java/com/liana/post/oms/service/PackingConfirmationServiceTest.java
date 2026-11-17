package com.liana.post.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** H2 exercises transaction contracts; authoritative DDL and locking still require MySQL verification. */
class PackingConfirmationServiceTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private PackingConfirmationService packing;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        packing = new PackingConfirmationService(jdbc, new ObjectMapper(), null);
        jdbc.execute("CREATE TABLE oms_packing_inbox (bag_no VARCHAR(32) PRIMARY KEY,event_no VARCHAR(64) UNIQUE,request_hash CHAR(64),"
                + "request_json VARCHAR(10000),recorded_at TIMESTAMP(3),applied_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE mail (waybill_no VARCHAR(32) PRIMARY KEY,bag_no VARCHAR(32),package_id VARCHAR(32),"
                + "current_facility_code VARCHAR(64),status VARCHAR(32),weight_grams INT,current_slot VARCHAR(64),updated_at TIMESTAMP(3))");
        jdbc.update("INSERT INTO mail (waybill_no,current_facility_code,status,weight_grams) VALUES ('CP000000001LN','LN-OR-01','CREATED',125)");
        jdbc.update("INSERT INTO mail (waybill_no,current_facility_code,status,weight_grams) VALUES ('CP000000002LN','LN-OR-01','SORTED',NULL)");
    }

    @Test
    void lostAcknowledgementReplaysReceiptAfterOpeningWithoutRepackingOrMovingMail() {
        var request = request("P1", "E1", List.of(item1()));
        assertTrue(accept(request));
        jdbc.update("UPDATE mail SET bag_no=NULL,package_id=NULL,current_facility_code='LN-OR-RC',status='SORTED' WHERE waybill_no=?", item1().mailNo());
        assertTrue(accept(request));
        assertNull(jdbc.queryForObject("SELECT package_id FROM mail WHERE waybill_no=?", String.class, item1().mailNo()));
        assertEquals("LN-OR-RC", jdbc.queryForObject("SELECT current_facility_code FROM mail WHERE waybill_no=?", String.class, item1().mailNo()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_packing_inbox", Integer.class));
    }

    @Test
    void unavailableMailRollsBackEarlierBindingsAndReceipt() {
        jdbc.update("UPDATE mail SET package_id='OTHER',bag_no='OTHER' WHERE waybill_no='CP000000002LN'");
        var request = request("P1", "E1", List.of(item1(), new PackingConfirmation.Item("CP000000002LN", null)));
        assertEquals(409, assertThrows(BusinessException.class, () -> accept(request)).getCode());
        assertNull(jdbc.queryForObject("SELECT package_id FROM mail WHERE waybill_no=?", String.class, item1().mailNo()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oms_packing_inbox", Integer.class));
    }

    @Test
    void changedIdentityLocationWeightAndDuplicateItemsAreRejected() {
        assertTrue(accept(request("P1", "E1", List.of(item1()))));
        assertThrows(BusinessException.class, () -> accept(request("P1", "E1", List.of(new PackingConfirmation.Item("CP000000002LN", null)))));
        assertThrows(BusinessException.class, () -> accept(request("P2", "E1", List.of(new PackingConfirmation.Item("CP000000002LN", null)))));
        assertThrows(BusinessException.class, () -> accept(new PackingConfirmation("E2", "P2", "LN-NAM-HQ", 1,
                List.of(new PackingConfirmation.Item("CP000000002LN", null)))));
        assertThrows(BusinessException.class, () -> accept(request("P2", "E2", List.of(new PackingConfirmation.Item("CP000000002LN", 0)))));
        assertThrows(BusinessException.class, () -> accept(request("P2", "E2", List.of(item1(), item1()))));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_packing_inbox", Integer.class));
    }

    @Test
    void concurrentBagsCanConfirmOnlyOneCurrentBinding() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(n -> pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
                try { return accept(request("P" + n, "E" + n, List.of(item1()))) ? 1 : 0; }
                catch (BusinessException e) { assertEquals(409, e.getCode()); return 0; }
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(1, tasks.get(0).get(10, TimeUnit.SECONDS) + tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_packing_inbox WHERE applied_at IS NOT NULL", Integer.class));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test
    void sameCommandConcurrentDeliveryCreatesOnlyOneReceipt() throws Exception {
        var request = request("P1", "E1", List.of(item1()));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> accept(request));
            var second = pool.submit(() -> accept(request));
            assertTrue(first.get(10, TimeUnit.SECONDS)); assertTrue(second.get(10, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oms_packing_inbox", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    private boolean accept(PackingConfirmation request) { return Boolean.TRUE.equals(tx.execute(s -> packing.accept(request))); }
    private PackingConfirmation.Item item1() { return new PackingConfirmation.Item("CP000000001LN", 125); }
    private PackingConfirmation request(String bag, String event, List<PackingConfirmation.Item> items) {
        return new PackingConfirmation(event, bag, "LN-OR-01", 1, items);
    }
}
