package com.liana.post.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BagPackingServiceTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private BagPackingService packing;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        packing = new BagPackingService(jdbc, new ObjectMapper());
        jdbc.execute("CREATE TABLE dispatch_bag (bag_no VARCHAR(32) PRIMARY KEY,status VARCHAR(32),updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_bag_packing (bag_no VARCHAR(32) PRIMARY KEY REFERENCES dispatch_bag(bag_no),packing_version INT,"
                + "event_no VARCHAR(64) UNIQUE,content_hash CHAR(64),payload_json VARCHAR(10000),sync_status VARCHAR(16) DEFAULT 'PENDING',"
                + "attempt_count INT DEFAULT 0,next_attempt_at TIMESTAMP(3),last_error VARCHAR(512),recorded_at TIMESTAMP(3),confirmed_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_bag_item (bag_no VARCHAR(32) REFERENCES dispatch_bag_packing(bag_no),item_no VARCHAR(32),"
                + "seq_no INT,weight_grams INT,PRIMARY KEY(bag_no,item_no),UNIQUE(bag_no,seq_no))");
        jdbc.update("INSERT INTO dispatch_bag (bag_no,status) VALUES ('P1','CREATED')");
    }

    @Test
    void packingAndDeliveryRollBackTogetherAndRetainImmutableUnknownWeight() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            packing.prepare(request()); throw new IllegalStateException("local batch failed");
        }));
        assertTrue(packing.items("P1").isEmpty()); assertTrue(packing.due().isEmpty());
        prepare(); prepare();
        assertEquals(List.of(new PackingConfirmation.Item("CP000000001LN", 125), new PackingConfirmation.Item("CP000000002LN", null)), packing.items("P1"));
        assertEquals(1, packing.due().size());
        assertThrows(BusinessException.class, () -> packing.requireConfirmed("P1"));
        var changed = new PackingConfirmation("E1", "P1", "LN-OR-01", 1, List.of(new PackingConfirmation.Item("CP000000001LN", 0)));
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> packing.prepare(changed)));
    }

    @Test
    void leaseRecoveryIgnoresStaleResultsAndAcknowledgementNeverRegressesDispatchedBag() {
        prepare();
        var first = packing.due().get(0); assertTrue(packing.claim(first)); assertFalse(packing.claim(first));
        expire(); var second = packing.due().get(0); assertTrue(packing.claim(second));
        tx.executeWithoutResult(s -> packing.confirmed(first));
        assertEquals("SENDING", packing.state("P1").syncStatus());
        packing.failed(second, "timeout", false); expire();
        var retry = packing.due().get(0); assertEquals(first.payload(), retry.payload()); assertTrue(packing.claim(retry));
        jdbc.update("UPDATE dispatch_bag SET status='DISPATCHED' WHERE bag_no='P1'");
        tx.executeWithoutResult(s -> packing.confirmed(retry));
        packing.requireConfirmed("P1");
        assertEquals("DISPATCHED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P1'", String.class));
    }

    @Test
    void httpFailureRetriesAndExplicitConflictStopsAutomaticDelivery() throws Exception {
        var response = new AtomicReference<>("{\"code\":500,\"data\":false}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/oms/internal/packings", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var publisher = new BagPackingPublisher(packing, new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-packing-token");
            prepare(); publisher.publish(); assertEquals("RETRY", packing.state("P1").syncStatus());
            response.set("{\"code\":200,\"data\":false}"); expire(); publisher.publish();
            assertEquals("RETRY", packing.state("P1").syncStatus());
            response.set("{\"code\":409,\"message\":\"mail already packed\"}"); expire(); publisher.publish();
            assertEquals("CONFLICT", packing.state("P1").syncStatus()); assertTrue(packing.due().isEmpty());
            assertEquals("CREATED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P1'", String.class));
            jdbc.update("INSERT INTO dispatch_bag (bag_no,status) VALUES ('P2','CREATED')");
            tx.executeWithoutResult(s -> packing.prepare(new PackingConfirmation("E2", "P2", "LN-OR-01", 1, request().items())));
            response.set("{\"code\":200,\"data\":true}"); publisher.publish();
            assertEquals("CONFIRMED", packing.state("P2").syncStatus());
            assertEquals("REVIEWED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P2'", String.class));
        } finally { server.stop(0); }
    }

    @Test
    void explicitAcknowledgementConfirmsPackingAndAdvancesBagInOneTransaction() {
        prepare(); var delivery = packing.due().get(0); assertTrue(packing.claim(delivery));
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            packing.confirmed(delivery); throw new IllegalStateException("crash");
        }));
        assertEquals("SENDING", packing.state("P1").syncStatus());
        assertEquals("CREATED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P1'", String.class));
        tx.executeWithoutResult(s -> packing.confirmed(delivery));
        assertEquals("CONFIRMED", packing.state("P1").syncStatus());
        assertEquals("REVIEWED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P1'", String.class));
    }

    private void prepare() { tx.executeWithoutResult(s -> packing.prepare(request())); }
    private void expire() { jdbc.update("UPDATE dispatch_bag_packing SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00' WHERE bag_no='P1'"); }
    private PackingConfirmation request() {
        return new PackingConfirmation("E1", "P1", "LN-OR-01", 1, List.of(
                new PackingConfirmation.Item("CP000000001LN", 125), new PackingConfirmation.Item("CP000000002LN", null)));
    }
}
