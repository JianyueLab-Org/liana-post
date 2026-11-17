package com.liana.post.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestDTO;
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

class ManifestOutboxTest {
    private JdbcTemplate jdbc;
    private ManifestOutbox outbox;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        outbox = new ManifestOutbox(jdbc, new ObjectMapper());
        jdbc.execute("CREATE TABLE dispatch_manifest_outbox (event_no VARCHAR(64) PRIMARY KEY,manifest_no VARCHAR(64),version_no INT,"
                + "payload_json VARCHAR(100000),content_hash CHAR(64),status VARCHAR(16) DEFAULT 'PENDING',attempt_count INT DEFAULT 0,"
                + "next_attempt_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,last_error VARCHAR(512),created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,"
                + "sent_at TIMESTAMP(3),event_type VARCHAR(16) DEFAULT 'PREALERT',receipt_json VARCHAR(10000),UNIQUE(manifest_no,version_no,event_type))");
        jdbc.execute("CREATE TABLE dispatch_batch (batch_no VARCHAR(64) PRIMARY KEY,manifest_no VARCHAR(64) UNIQUE,status VARCHAR(32),updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_batch_bag (batch_no VARCHAR(64),bag_no VARCHAR(64),active_flag INT DEFAULT 1)");
    }

    @Test
    void rollbackLeavesNoOrphanPrealertAndSameVersionCannotBeOverwritten() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            outbox.enqueue(request());
            throw new IllegalStateException("batch transaction failed");
        }));
        assertTrue(outbox.due().isEmpty());
        enqueue(); enqueue();
        assertEquals(1, outbox.due().size());
        var changed = request(); changed.setTargetNode("LN-NAM-AIR");
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueue(changed)));
        assertEquals(1, outbox.due().size());
    }

    @Test
    void leaseRecoveryRejectsStaleAcknowledgementAndRetainsFailedPayload() {
        enqueue();
        var first = outbox.due().get(0);
        assertTrue(outbox.claim(first));
        assertFalse(outbox.claim(first));
        assertTrue(outbox.due().isEmpty());
        expire();
        var second = outbox.due().get(0);
        assertTrue(outbox.claim(second));
        outbox.sent(first);
        assertEquals("SENDING", outbox.status("M-TEST"));
        outbox.failed(second, "unavailable");
        assertEquals("RETRY", outbox.status("M-TEST"));
        expire();
        var retry = outbox.due().get(0);
        assertEquals(first.payload(), retry.payload());
        assertTrue(outbox.claim(retry));
        outbox.sent(retry);
        assertEquals("SENT", outbox.status("M-TEST"));
        assertTrue(outbox.due().isEmpty());
    }

    @Test
    void httpErrorAndFalseAcknowledgementStayRetryableUntilConfirmed() throws Exception {
        var response = new AtomicReference<>("{\"code\":500,\"data\":true}");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/sorting/manifest/sync", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var publisher = new ManifestOutboxPublisher(outbox, new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-secret");
            enqueue(); publisher.publish();
            assertEquals("RETRY", outbox.status("M-TEST"));
            response.set("{\"code\":200,\"data\":false}"); expire(); publisher.publish();
            assertEquals("RETRY", outbox.status("M-TEST"));
            response.set("{\"code\":200,\"data\":true}"); expire(); publisher.publish();
            assertEquals("SENT", outbox.status("M-TEST"));
            assertEquals(3, jdbc.queryForObject("SELECT attempt_count FROM dispatch_manifest_outbox", Integer.class));
        } finally { server.stop(0); }
    }

    @Test
    void sealingNeedsPrealertAcknowledgementAndReusesOneCommandAcrossClicks() {
        enqueue();
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueueSeal("M-TEST", "first")));
        var prealert = outbox.due().get(0); assertTrue(outbox.claim(prealert)); outbox.sent(prealert);
        tx.executeWithoutResult(s -> outbox.enqueueSeal("M-TEST", "first"));
        var fixed = outbox.due().get(0);
        tx.executeWithoutResult(s -> outbox.enqueueSeal("M-TEST", "second"));
        assertEquals(fixed.payload(), outbox.due().get(0).payload());
        assertEquals("SENT", outbox.status("M-TEST"));
        assertEquals("PENDING", outbox.sealState("M-TEST").status());
        assertThrows(BusinessException.class, () -> outbox.requireSealed("M-TEST"));
        var newer = request(); newer.setVersionNo(2);
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueue(newer)));
        assertTrue(outbox.claim(fixed));
        outbox.sent(fixed, "{\"confirmed\":true}");
        assertDoesNotThrow(() -> outbox.requireSealed("M-TEST"));
    }

    @Test
    void sealPublisherRejectsFalseOrMismatchedReceiptsAndStopsOnBusinessConflict() throws Exception {
        var response = new AtomicReference<>("{\"code\":200,\"data\":true}");
        var received = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/sorting/internal/manifests/seal", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("test-secret", exchange.getRequestHeaders().getFirst("X-Manifest-Token"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            enqueue(); var prealert = outbox.due().get(0); outbox.claim(prealert); outbox.sent(prealert);
            tx.executeWithoutResult(s -> outbox.enqueueSeal("M-TEST", "clerk"));
            var publisher = new ManifestOutboxPublisher(outbox, new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-secret");
            publisher.publish(); assertEquals("RETRY", outbox.sealState("M-TEST").status());
            var json = new ObjectMapper();
            var receipt = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(received.get());
            receipt.put("contentHash", "a".repeat(64)); receipt.put("sealedAt", "2026-11-17T00:00:00Z");
            receipt.put("versionNo", 2);
            response.set("{\"code\":200,\"data\":" + receipt + "}");
            expire(); publisher.publish(); assertEquals("RETRY", outbox.sealState("M-TEST").status());
            receipt.put("versionNo", 1);
            response.set("{\"code\":200,\"data\":" + receipt + "}");
            expire(); publisher.publish(); assertEquals("SENT", outbox.sealState("M-TEST").status());
            assertNotNull(jdbc.queryForObject("SELECT receipt_json FROM dispatch_manifest_outbox WHERE event_type='SEAL'", String.class));
            // Exercise permanent conflicts with another fixed command.
            var second = request(); second.setManifestCode("M-SECOND");
            tx.executeWithoutResult(s -> outbox.enqueue(second));
            var next = outbox.due().get(0); outbox.claim(next); outbox.sent(next);
            tx.executeWithoutResult(s -> outbox.enqueueSeal("M-SECOND", "clerk"));
            response.set("{\"code\":409,\"message\":\"version changed\"}");
            publisher.publish();
            assertEquals("CONFLICT", outbox.sealState("M-SECOND").status());
            assertEquals("version changed", outbox.sealState("M-SECOND").lastError());
            expire(); assertTrue(outbox.due().isEmpty());
        } finally { server.stop(0); }
    }

    @Test
    void staleSealSenderCannotOverwriteNewerReceiptOrConflict() {
        enqueue(); var prealert = outbox.due().get(0); outbox.claim(prealert); outbox.sent(prealert);
        tx.executeWithoutResult(s -> outbox.enqueueSeal("M-TEST", "clerk"));
        var first = outbox.due().get(0); assertTrue(outbox.claim(first));
        expire(); var second = outbox.due().get(0); assertTrue(outbox.claim(second));
        outbox.conflict(first, "late failure");
        assertEquals("SENDING", outbox.sealState("M-TEST").status());
        outbox.sent(second, "{\"receipt\":2}");
        outbox.sent(first, "{\"receipt\":1}");
        assertEquals("{\"receipt\":2}", jdbc.queryForObject("SELECT receipt_json FROM dispatch_manifest_outbox WHERE event_type='SEAL'", String.class));
    }

    private void enqueue() { tx.executeWithoutResult(s -> outbox.enqueue(request())); }

    @Test
    void cancellationPreparationRollsBackWithBatchAndRepeatedClicksKeepOriginalIdentity() {
        enqueue();
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "first", "cancel")));
        acknowledgePrealert();
        jdbc.update("INSERT INTO dispatch_batch VALUES ('D-TEST','M-TEST','PENDING',CURRENT_TIMESTAMP)");
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            outbox.enqueueCancel("M-TEST", "first", "cancel");
            jdbc.update("UPDATE dispatch_batch SET status='CANCELLING'");
            throw new IllegalStateException("business transaction failed");
        }));
        assertEquals("NOT_REQUESTED", outbox.cancelState("M-TEST").status());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
        tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "first", "cancel"));
        var first = outbox.due().get(0);
        tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "second", " cancel "));
        assertEquals(first.payload(), outbox.due().get(0).payload());
        assertEquals("cancel", outbox.cancelState("M-TEST").reason());
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "first", "different")));
        assertThrows(BusinessException.class, () -> outbox.requireSealed("M-TEST"));
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueueSeal("M-TEST", "clerk")));
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueue(request())));
    }

    @Test
    void outstandingSealMustResolveBeforeCancellationAndMissingReasonIsRejected() {
        enqueue(); acknowledgePrealert();
        tx.executeWithoutResult(s -> outbox.enqueueSeal("M-TEST", "clerk"));
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "clerk", "cancel")));
        assertEquals("NOT_REQUESTED", outbox.cancelState("M-TEST").status());
        var seal = outbox.due().get(0); outbox.claim(seal); outbox.sent(seal, "{\"sealed\":true}");
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "clerk", "  ")));
        tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "clerk", "cancel"));
        assertEquals("PENDING", outbox.cancelState("M-TEST").status());
    }

    @Test
    void cancellationAcknowledgementAndBatchFinalizationAreAtomicAndFenceLateSenders() {
        prepareCancellation();
        var first = outbox.due().get(0); assertTrue(outbox.claim(first));
        jdbc.execute("ALTER TABLE dispatch_batch ADD CONSTRAINT reject_cancel CHECK(status<>'CANCELLED')");
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> outbox.sent(first, "{\"receipt\":1}")));
        assertEquals("SENDING", outbox.cancelState("M-TEST").status());
        assertEquals("CANCELLING", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
        assertNull(jdbc.queryForObject("SELECT receipt_json FROM dispatch_manifest_outbox WHERE event_type='CANCEL'", String.class));
        jdbc.execute("ALTER TABLE dispatch_batch DROP CONSTRAINT reject_cancel");
        expire(); var second = outbox.due().get(0); assertTrue(outbox.claim(second));
        tx.executeWithoutResult(s -> outbox.sent(first, "{\"receipt\":1}"));
        assertEquals("CANCELLING", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
        tx.executeWithoutResult(s -> outbox.sent(second, "{\"receipt\":2}"));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
        outbox.failed(first, "late failure"); outbox.conflict(first, "late conflict");
        assertEquals("SENT", outbox.cancelState("M-TEST").status());
        assertEquals("{\"receipt\":2}", jdbc.queryForObject("SELECT receipt_json FROM dispatch_manifest_outbox WHERE event_type='CANCEL'", String.class));
    }

    @Test
    void cancelledBatchWaitsForMatchingHttpReceiptAndConflictDoesNotUnlockIt() throws Exception {
        prepareCancellation();
        var response = new AtomicReference<>("{\"code\":200,\"data\":true}");
        var received = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/sorting/internal/manifests/cancel", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("test-secret", exchange.getRequestHeaders().getFirst("X-Manifest-Token"));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            // Wrap the real outbox in the same transactional boundary as the Spring bean.
            ManifestOutbox transactional = new ManifestOutbox(jdbc, new ObjectMapper()) {
                @Override public void sent(Delivery d, String r) { tx.executeWithoutResult(s -> super.sent(d, r)); }
            };
            var publisher = new ManifestOutboxPublisher(transactional, new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-secret");
            publisher.publish(); assertEquals("RETRY", outbox.cancelState("M-TEST").status());
            String fixed = received.get();
            var json = new ObjectMapper(); var receipt = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(fixed);
            receipt.put("contentHash", "b".repeat(64)); receipt.put("cancelledAt", "2026-11-17T00:00:00Z");
            receipt.put("reason", "mismatched"); response.set("{\"code\":200,\"data\":" + receipt + "}");
            expire(); publisher.publish(); assertEquals("RETRY", outbox.cancelState("M-TEST").status());
            assertEquals("CANCELLING", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
            receipt.put("reason", "cancel"); response.set("{\"code\":200,\"data\":" + receipt + "}");
            jdbc.execute("ALTER TABLE dispatch_batch ADD CONSTRAINT reject_cancel CHECK(status<>'CANCELLED')");
            expire(); publisher.publish(); assertEquals("RETRY", outbox.cancelState("M-TEST").status());
            jdbc.execute("ALTER TABLE dispatch_batch DROP CONSTRAINT reject_cancel");
            expire(); publisher.publish();
            assertEquals(fixed, received.get());
            assertEquals("SENT", outbox.cancelState("M-TEST").status());
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
            var second = request(); second.setManifestCode("M-SECOND");
            tx.executeWithoutResult(s -> outbox.enqueue(second));
            var prealert = outbox.due().get(0); outbox.claim(prealert); outbox.sent(prealert);
            tx.executeWithoutResult(s -> outbox.enqueueCancel("M-SECOND", "clerk", "cancel"));
            jdbc.update("INSERT INTO dispatch_batch VALUES ('D-SECOND','M-SECOND','CANCELLING',CURRENT_TIMESTAMP)");
            response.set("{\"code\":409,\"message\":\"already received\"}");
            publisher.publish();
            assertEquals("CONFLICT", outbox.cancelState("M-SECOND").status());
            assertEquals("CANCELLING", jdbc.queryForObject("SELECT status FROM dispatch_batch WHERE batch_no='D-SECOND'", String.class));
            expire(); assertTrue(outbox.due().isEmpty());
        } finally { server.stop(0); }
    }

    @Test
    void concurrentCancellationClicksCreateOneFixedRequest() throws Exception {
        enqueue(); acknowledgePrealert();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                String actor = "clerk-" + i;
                jobs.add(pool.submit(() -> tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", actor, "cancel"))));
            }
            for (var job : jobs) job.get();
            assertEquals(1, outbox.due().size());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_outbox WHERE event_type='CANCEL'", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    private void acknowledgePrealert() {
        var prealert = outbox.due().get(0); assertTrue(outbox.claim(prealert)); outbox.sent(prealert);
    }

    private void prepareCancellation() {
        enqueue(); acknowledgePrealert();
        tx.executeWithoutResult(s -> outbox.enqueueCancel("M-TEST", "clerk", "cancel"));
        jdbc.update("INSERT INTO dispatch_batch VALUES ('D-TEST','M-TEST','CANCELLING',CURRENT_TIMESTAMP)");
    }
    private void expire() { jdbc.update("UPDATE dispatch_manifest_outbox SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00'"); }
    private ManifestDTO request() {
        var r = new ManifestDTO(); r.setManifestCode("M-TEST"); r.setBatchNo("D-TEST"); r.setVersionNo(1);
        r.setOriginNode("LN-OR-01"); r.setTargetNode("LN-OR-RC"); r.setTotalPackages(List.of()); return r;
    }
}
