package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.dto.dispatch.BagForwarding;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.sorting.client.DispatchLifecycleClient;
import com.liana.post.sorting.client.TransportReceiptClient;
import com.liana.post.sorting.model.dto.SortingReceiveRequest;
import com.liana.post.sorting.model.dto.SortingUnpackItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Canonical V1-V5 with H2 syntax adapters; does not replace MySQL/InnoDB acceptance. */
class ManifestReceivingServiceTest {
    private JdbcTemplate jdbc;
    private ManifestReceivingService service;
    private SortingReceiptPublisher publisher;
    private TransportReceiptClient transport;
    private DispatchLifecycleClient dispatch;
    private HttpServletRequest http;
    private TransactionTemplate tx;
    private BagForwardingService forwarding;
    private BagForwardingPublisher forwardPublisher;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        try (var paths = Files.list(Path.of("../database/migrations/sorting_db"))) {
            for (var path : paths.sorted().toList()) {
                String sql = Files.readString(path).replaceAll("(?m)^--.*$", "")
                        .replaceAll(" CHARACTER SET \\w+", "").replaceAll(" COLLATE[= ]\\w+", "")
                        .replaceAll(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "").replaceAll("\\bJSON\\b", "VARCHAR(1000000)")
                        .replace("DROP CHECK", "DROP CONSTRAINT")
                        .replace(",\n  ADD COLUMN content_hash", ";\nALTER TABLE sorting_manifest ADD COLUMN content_hash");
                for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
            }
        }
        var manager = new DataSourceTransactionManager(ds); tx = new TransactionTemplate(manager);
        transport = mock(TransportReceiptClient.class); dispatch = mock(DispatchLifecycleClient.class); http = mock(HttpServletRequest.class);
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("oro-receiver", "SORTER", "LN-OR-RC"));
        service = new ManifestReceivingService(jdbc, json, transport, manager, http, "secret");
        publisher = new SortingReceiptPublisher(jdbc, json, transport, dispatch, "secret");
        forwarding = new BagForwardingService(jdbc, json, http);
        forwardPublisher = new BagForwardingPublisher(jdbc, json, dispatch, "secret");
        jdbc.update("INSERT INTO sorting_manifest (manifest_no,manifest_type,source_org_code,destination_org_code,batch_no,manifest_status,version_no,content_hash,expected_package_qty) VALUES ('M1','TRANSIT','LN-OR-01','LN-OR-RC','D1','SEALED',1,?,2)", "a".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_version (manifest_no,version_no,content_hash,expected_package_qty,expected_item_qty,snapshot_json) VALUES ('M1',1,?,2,1,'{}')", "a".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_seal (manifest_no,version_no,request_no,request_hash,request_json,content_hash,source_org_code,actor_id,sealed_at) VALUES ('M1',1,'S1',?,'{}',?,'LN-OR-01','source',CURRENT_TIMESTAMP)", "b".repeat(64), "a".repeat(64));
        for (String bag : List.of("B1","B2")) jdbc.update("INSERT INTO sorting_manifest_package (manifest_no,package_no,expected_seq_no,expected_weight,item_status) VALUES ('M1',?,1,100,'EXPECTED')", bag);
        jdbc.update("INSERT INTO sorting_manifest_item (manifest_no,item_no,expected_package_no,expected_seq_no,expected_qty,item_status) VALUES ('M1','CP000000001LN','B1',1,1,'EXPECTED')");
        when(transport.arrival("M1", "secret")).thenReturn(Result.ok(arrival()));
        when(transport.receive(any(), eq("secret"))).thenAnswer(c -> Result.ok(new ManifestReceipt.Ack(c.getArgument(0), Instant.now())));
        when(dispatch.receive(any(), eq("secret"))).thenAnswer(c -> Result.ok(new ManifestReceipt.Ack(c.getArgument(0), Instant.now())));
    }

    @Test
    void partialReceiptDoesNotTurnExpectedMailIntoActualAndOriginalRetrySurvivesUnpackAndRemoteOutage() throws Exception {
        var request = request("R1", "B1");
        var response = service.receive(request);
        assertEquals("oro-receiver", response.getReceiptEvents().get(0).actorId());
        assertEquals("RECEIVING", value("sorting_manifest", "manifest_status"));
        assertEquals("EXPECTED", value("sorting_manifest_item", "item_status"));
        assertEquals("LN-OR-01", value("sorting_total_package", "source_org_code"));
        assertEquals(2, count("sorting_receipt_outbox"));
        var unpack = new SortingUnpackItemRequest(); unpack.setPackageNo("B1"); unpack.setStationCode("LN-OR-RC");
        tx.executeWithoutResult(s -> { jdbc.queryForList("SELECT manifest_no FROM sorting_manifest WHERE manifest_no='M1' FOR UPDATE"); service.lockForUnpack(unpack, "M1"); service.opened("B1"); jdbc.update("UPDATE sorting_total_package SET package_status='OPENED'"); });
        reset(transport);
        assertEquals(json.writeValueAsString(response), json.writeValueAsString(service.receive(request)));
        verifyNoInteractions(transport);
        assertEquals("OPENED", value("sorting_bag_position", "container_status"));
        assertEquals("OPENED", value("sorting_total_package", "package_status"));
        assertThrows(BusinessException.class, () -> service.receive(request("R1", "B2")));
    }

    @Test
    void wholeRequestRollsBackWhenOneScannedBagConflicts() {
        service.receive(request("FIRST", "B2"));
        assertThrows(BusinessException.class, () -> service.receive(request("SECOND", "B1", "B2")));
        assertEquals(1, count("sorting_receive_request")); assertEquals(1, count("sorting_bag_receipt"));
        assertEquals(1, count("sorting_bag_position")); assertEquals(2, count("sorting_receipt_outbox"));
        assertEquals("EXPECTED", jdbc.queryForObject("SELECT item_status FROM sorting_manifest_package WHERE package_no='B1'", String.class));
    }

    @Test
    void unsealedOrCancelledManifestCannotCreateReceiptFacts() {
        jdbc.update("DELETE FROM sorting_manifest_seal");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.receive(request("R1", "B1"))).getCode());
        jdbc.update("UPDATE sorting_manifest SET manifest_status='CANCELLED'");
        assertEquals(409, assertThrows(BusinessException.class, () -> service.receive(request("R2", "B1"))).getCode());
        assertEquals(0, count("sorting_receive_request")); assertEquals(0, count("sorting_bag_receipt"));
    }

    @Test
    void arrivalVersionMembershipAndAuthorizationCannotBeBypassed() {
        when(transport.arrival(anyString(), anyString())).thenReturn(Result.ok(new ManifestReceipt.Arrival("A1","T1","M1",2,"a".repeat(64),"LN-OR-01","LN-OR-RC",List.of("B1","B2"))));
        assertThrows(BusinessException.class, () -> service.receive(request("R1", "B1")));
        when(transport.arrival(anyString(), anyString())).thenReturn(Result.ok(arrival()));
        assertThrows(BusinessException.class, () -> service.receive(request("R1", "EXTRA")));
        var blind = request("R1", "B1"); blind.setReceiveMode("FORCE");
        assertThrows(BusinessException.class, () -> service.receive(blind));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("foreign", "SORTER", "LN-NAM-AIR"));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.receive(request("R1", "B1"))).getCode());
        when(http.getHeader("Authorization")).thenReturn(null);
        assertEquals(401, assertThrows(BusinessException.class, () -> service.receive(request("R1", "B1"))).getCode());
        assertEquals(0, count("sorting_bag_receipt"));
    }

    @Test
    void concurrentOriginalRequestsReturnOneFixedReceiptAndOnlyOneSetOfEvents() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return service.receive(request("R1", "B1","B2")); });
            var b = pool.submit(() -> { start.await(); return service.receive(request("R1", "B2","B1")); });
            start.countDown();
            assertEquals(json.writeValueAsString(a.get()), json.writeValueAsString(b.get()));
            assertEquals(1, count("sorting_receive_request")); assertEquals(2, count("sorting_bag_receipt"));
            assertEquals(4, count("sorting_receipt_outbox")); assertEquals("CHECKED", value("sorting_manifest", "manifest_status"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void destinationsRetryIndependentlyAndLostResponseReusesPhysicalFact() {
        var response = service.receive(request("R1", "B1"));
        when(transport.receive(any(), anyString())).thenThrow(new IllegalStateException("response lost after remote commit"));
        assertEquals(1, publisher.publishDue());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sorting_receipt_outbox WHERE status='RETRY'", Integer.class));
        assertEquals(1, count("sorting_bag_receipt"));
        doAnswer(c -> { assertEquals(response.getReceiptEvents().get(0), c.getArgument(0)); return Result.ok(new ManifestReceipt.Ack(c.getArgument(0), Instant.now())); }).when(transport).receive(any(), anyString());
        jdbc.update("UPDATE sorting_receipt_outbox SET next_attempt_at=? WHERE status='RETRY'", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        assertEquals(1, publisher.publishDue());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sorting_receipt_outbox WHERE status='SENT'", Integer.class));
    }

    @Test
    void staleAttemptCannotConfirmOrFailANewerAttemptAndInvalidAckFreezes() throws Exception {
        service.receive(request("R1", "B1"));
        var old = publisher.due().get(0); assertTrue(publisher.claim(old));
        jdbc.update("UPDATE sorting_receipt_outbox SET next_attempt_at=?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        var current = publisher.due().stream().filter(d -> d.destination().equals(old.destination())).findFirst().orElseThrow();
        assertTrue(publisher.claim(current));
        var ack = new ManifestReceipt.Ack(json.readValue(old.payload(), ManifestReceipt.Event.class), Instant.now());
        assertEquals(0, publisher.confirm(old, ack)); publisher.fail(old, "stale", true);
        assertEquals(1, publisher.confirm(current, ack));
        when(transport.receive(any(), anyString())).thenReturn(Result.ok(new ManifestReceipt.Ack(null, Instant.now())));
        publisher.publishDue();
        // The other destination is TRANSPORT because sorted due() selected DISPATCH first.
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sorting_receipt_outbox WHERE status='CONFLICT'", Integer.class));
        assertEquals(1, count("sorting_bag_receipt"));
    }

    @Test
    void finishCheckFreezesMissingBagsAndOriginalReceiptCanStillReplay() throws Exception {
        var first = service.receive(request("R1", "B1"));
        var closure = closure(); var input = new ManifestClosureService.Request("CHECK1", "LN-OR-RC");
        var checked = closure.finishCheck("M1", input);
        assertTrue(service.isCheckFinished("M1"));
        assertEquals(List.of("B1"), checked.receivedBagNos()); assertEquals(List.of("B2"), checked.missingBagNos());
        assertEquals("MISSING", jdbc.queryForObject("SELECT item_status FROM sorting_manifest_package WHERE package_no='B2'", String.class));
        assertEquals(1, count("discrepancy_verification")); assertEquals(1, count("sorting_bag_receipt"));
        assertThrows(BusinessException.class, () -> service.receive(request("LATE", "B2")));
        reset(transport);
        assertEquals(checked, closure.finishCheck("M1", input));
        assertEquals(json.writeValueAsString(first), json.writeValueAsString(service.receive(request("R1", "B1"))));
        verifyNoInteractions(transport);
        assertThrows(BusinessException.class, () -> closure.finishCheck("M1", new ManifestClosureService.Request("OTHER", "LN-OR-RC")));
    }

    @Test
    void zeroReceiptClosureRequiresReviewAndBothMatchingAcknowledgements() {
        var closure = closure();
        closure.finishCheck("M1", new ManifestClosureService.Request("CHECK1", "LN-OR-RC"));
        assertEquals(2, count("discrepancy_verification")); assertEquals(0, count("sorting_bag_receipt")); assertEquals(0, count("sorting_bag_position"));
        var close = new ManifestClosureService.Request("CLOSE1", "LN-OR-RC");
        assertThrows(BusinessException.class, () -> closure.close("M1", close));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("admin", "ADMIN", "LN-OR-RC"));
        assertThrows(BusinessException.class, () -> closure.close("M1", close));
        var review = new DiscrepancyDispositionService(jdbc, json, http, new DataSourceTransactionManager(jdbc.getDataSource()));
        for (String no : jdbc.query("SELECT verification_no FROM discrepancy_verification", (rs,n)->rs.getString(1))) {
            review.apply(no, new DiscrepancyDispositionService.Request("REVIEW-" + no, "CONFIRMED_NOT_RECEIVED", "已核对到站交接清单，确认该总包未实际接收"));
        }
        assertEquals("CLOSING", closure.close("M1", close).status());
        assertEquals("CLOSING", closure.close("M1", close).status());
        var pub = new ManifestClosurePublisher(jdbc, json, dispatch, transport, "secret");
        when(dispatch.close(any(), eq("secret"))).thenAnswer(c -> Result.ok(new com.liana.post.common.dto.sorting.ManifestClosure.Ack(c.getArgument(0), "DISPATCH", Instant.now())));
        when(transport.close(any(), eq("secret"))).thenThrow(new IllegalStateException("response lost"));
        assertEquals(1, pub.publishDue()); assertEquals("CLOSING", closure.state("M1").status());
        jdbc.update("UPDATE sorting_close_outbox SET next_attempt_at=? WHERE status='RETRY'", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        when(transport.close(any(), eq("secret"))).thenAnswer(c -> Result.ok(new com.liana.post.common.dto.sorting.ManifestClosure.Ack(c.getArgument(0), "TRANSPORT", Instant.now())));
        assertEquals(1, pub.publishDue()); assertEquals("CLOSED", closure.state("M1").status());
        assertEquals(0, count("sorting_bag_receipt")); assertEquals(0, count("sorting_receive_request"));
        jdbc.update("UPDATE sorting_manifest SET manifest_status='CLOSING'"); pub.publishDue();
        assertEquals("CLOSED", closure.state("M1").status());
        assertThrows(BusinessException.class, () -> closure.close("M1", new ManifestClosureService.Request("CHANGED", "LN-OR-RC")));
    }

    @Test
    void closureBlocksUnfinishedPhysicalWorkAndReceiptSynchronization() {
        service.receive(request("R1", "B1", "B2"));
        var closure = closure(); closure.finishCheck("M1", new ManifestClosureService.Request("CHECK1", "LN-OR-RC"));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("admin", "ADMIN", "LN-OR-RC"));
        var input = new ManifestClosureService.Request("CLOSE1", "LN-OR-RC");
        assertThrows(BusinessException.class, () -> closure.close("M1", input));
        publisher.publishDue();
        assertThrows(BusinessException.class, () -> closure.close("M1", input));
        assertEquals(0, count("sorting_manifest_close"));
    }

    @Test
    void missingSecondLegFencesOldCustodyAndDoesNotCreateDestinationReceipt() {
        service.receive(request("FIRST", "B1")); publisher.publishDue();
        var state = tx.execute(s -> forwarding.prepare(forwardRequest("F1")));
        when(dispatch.forward(any(), anyString())).thenAnswer(c -> Result.ok(new BagForwarding.Ack(c.getArgument(0), Instant.now())));
        forwardPublisher.publishDue(); prepareNext(state.command());
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("nam-receiver", "SORTER", "LN-NAM-AIR"));
        closure().finishCheck(state.command().manifestNo(), new ManifestClosureService.Request("NEXT-CHECK", "LN-NAM-AIR"));
        assertEquals("MISSING", value("sorting_bag_position", "container_status"));
        assertEquals("LN-OR-RC", value("sorting_bag_position", "facility_code")); assertEquals(1, count("sorting_bag_receipt"));
        assertEquals("MISSING", value("sorting_bag_handoff", "status"));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("oro-receiver", "SORTER", "LN-OR-RC"));
        assertTrue(forwarding.candidates("LN-OR-RC").isEmpty());
    }

    @Test
    void concurrentFinishAndNewReceiptHaveOneConsistentPartition() throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var checked = pool.submit(() -> { start.await(); return closure().finishCheck("M1", new ManifestClosureService.Request("CHECK1", "LN-OR-RC")); });
            var received = pool.submit(() -> { start.await(); try { service.receive(request("R1", "B1")); return true; } catch (BusinessException e) { assertEquals(409,e.getCode()); return false; } });
            start.countDown(); var result = checked.get(); boolean actual = received.get();
            assertEquals(actual ? List.of("B1") : List.of(), result.receivedBagNos());
            assertEquals(actual ? 1 : 2, result.missingBagNos().size()); assertEquals(actual ? 1 : 0,count("sorting_bag_receipt"));
        } finally { pool.shutdownNow(); }
    }

    private ManifestClosureService closure() { return new ManifestClosureService(jdbc,json,http,transport,new DataSourceTransactionManager(jdbc.getDataSource()),"secret"); }
    private ManifestReceipt.Arrival arrival() { return new ManifestReceipt.Arrival("A1","T1","M1",1,"a".repeat(64),"LN-OR-01","LN-OR-RC",List.of("B1","B2")); }

    @Test
    void sealedBagTravelsTwoLegsAndOriginalReceiptRemainsReplayable() throws Exception {
        var original = request("FIRST", "B1");
        var first = service.receive(original); publisher.publishDue();
        var state = tx.execute(s -> forwarding.prepare(forwardRequest("F1")));
        assertEquals(state, tx.execute(s -> forwarding.prepare(forwardRequest("F1"))));
        assertEquals(1, count("sorting_bag_handoff"));
        assertThrows(BusinessException.class, () -> tx.execute(s -> forwarding.prepare(forwardRequest("OTHER"))));
        var unpack = new SortingUnpackItemRequest(); unpack.setPackageNo("B1"); unpack.setStationCode("LN-OR-RC");
        assertThrows(BusinessException.class, () -> tx.executeWithoutResult(s -> service.lockForUnpack(unpack, "M1")));
        when(dispatch.forward(any(), anyString())).thenAnswer(c -> Result.ok(new BagForwarding.Ack(c.getArgument(0), Instant.now())));
        assertEquals(1, forwardPublisher.publishDue());
        prepareNext(state.command());
        var next = nextReceive(state.command());
        var second = service.receive(next);
        assertEquals("LN-NAM-AIR", value("sorting_bag_position", "facility_code"));
        assertEquals(state.command().manifestNo(), value("sorting_bag_position", "last_manifest_no"));
        assertNull(value("sorting_bag_position", "outgoing_manifest_no"));
        assertEquals("RECEIVED", value("sorting_bag_handoff", "status"));
        assertEquals(2, count("sorting_bag_receipt")); assertEquals(1, count("sorting_total_package"));
        assertEquals(state.command().manifestNo(), second.getManifestNo());
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("oro-receiver", "SORTER", "LN-OR-RC"));
        reset(transport);
        assertEquals(json.writeValueAsString(first), json.writeValueAsString(service.receive(original)));
        verifyNoInteractions(transport);
        assertEquals("LN-NAM-AIR", value("sorting_bag_position", "facility_code"));
    }

    @Test
    void forwardingRequiresBothConfirmationsAndPreservesPendingCustodyOnFailure() {
        service.receive(request("FIRST", "B1"));
        assertThrows(BusinessException.class, () -> tx.execute(s -> forwarding.prepare(forwardRequest("F1"))));
        assertEquals(0, count("sorting_forward_request"));
        publisher.publishDue();
        var state = tx.execute(s -> forwarding.prepare(forwardRequest("F1")));
        when(dispatch.forward(any(), anyString())).thenThrow(new IllegalStateException("response lost"));
        assertEquals(0, forwardPublisher.publishDue());
        assertEquals("RETRY", value("sorting_forward_request", "status"));
        assertEquals(state.command().manifestNo(), value("sorting_bag_position", "outgoing_manifest_no"));
        assertThrows(BusinessException.class, () -> tx.execute(s -> forwarding.prepare(new BagForwardingService.Request("F1", "M1", "LN-OR-RC", "LN-NAM-HQ", "EDGE-OTHER", List.of("B1")))));
        prepareNext(state.command());
        assertThrows(BusinessException.class, () -> service.receive(nextReceive(state.command())));
        assertEquals(1, count("sorting_bag_receipt"));
        assertEquals("LN-OR-RC", value("sorting_bag_position", "facility_code"));
    }

    @Test
    void openedOrForeignBagsCannotBeForwardedAndCancellationRestoresSealedCustody() {
        service.receive(request("FIRST", "B1")); publisher.publishDue();
        tx.executeWithoutResult(s -> service.opened("B1"));
        assertThrows(BusinessException.class, () -> tx.execute(s -> forwarding.prepare(forwardRequest("F1"))));
        jdbc.update("UPDATE sorting_bag_position SET container_status='SEALED'");
        var state = tx.execute(s -> forwarding.prepare(forwardRequest("F1")));
        prepareNext(state.command());
        var cancel = new com.liana.post.common.dto.sorting.ManifestCancelCommand("C-NEXT", state.command().manifestNo(), 1, "LN-OR-RC", "clerk", "sailing cancelled");
        tx.execute(s -> new ManifestCancelService(jdbc, json).cancel(cancel));
        assertEquals("CANCELLED", value("sorting_bag_handoff", "status"));
        assertNull(value("sorting_bag_position", "outgoing_manifest_no"));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("oro-receiver", "SORTER", "LN-OR-RC"));
        assertNotNull(tx.execute(s -> forwarding.prepare(forwardRequest("F2"))));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("foreign", "SORTER", "LN-NAM-HQ"));
        assertEquals(403, assertThrows(BusinessException.class, () -> tx.execute(s -> forwarding.prepare(forwardRequest("F3")))).getCode());
    }

    @Test
    void concurrentForwardingRequestsHaveOnlyOneCurrentOutgoingLeg() throws Exception {
        service.receive(request("FIRST", "B1")); publisher.publishDue();
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); return reserve("F1"); });
            var b = pool.submit(() -> { start.await(); return reserve("F2"); });
            start.countDown(); assertNotEquals(a.get(), b.get());
        } finally { pool.shutdownNow(); }
        assertEquals(1, count("sorting_forward_request")); assertEquals(1, count("sorting_bag_handoff"));
    }

    private boolean reserve(String key) {
        try { tx.execute(s -> forwarding.prepare(forwardRequest(key))); return true; }
        catch (BusinessException e) { assertEquals(409, e.getCode()); return false; }
    }

    @Test
    void expiredForwardingWorkerCannotConfirmOrRejectANewerAttempt() throws Exception {
        service.receive(request("FIRST", "B1")); publisher.publishDue();
        var state = tx.execute(s -> forwarding.prepare(forwardRequest("F1")));
        String payload = jdbc.queryForObject("SELECT payload_json FROM sorting_forward_request", String.class);
        var first = new BagForwardingPublisher.Delivery("F1", payload, 0);
        assertTrue(forwardPublisher.claim(first));
        jdbc.update("UPDATE sorting_forward_request SET next_attempt_at=?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        var second = new BagForwardingPublisher.Delivery("F1", payload, 1);
        assertTrue(forwardPublisher.claim(second));
        var ack = new BagForwarding.Ack(state.command(), Instant.now());
        assertEquals(0, forwardPublisher.confirm(first, ack)); forwardPublisher.fail(first, "late failure", true);
        assertEquals("SENDING", value("sorting_forward_request", "status"));
        assertEquals(1, forwardPublisher.confirm(second, ack));
        assertEquals("SENT", value("sorting_forward_request", "status"));
    }
    private BagForwardingService.Request forwardRequest(String key) {
        return new BagForwardingService.Request(key, "M1", "LN-OR-RC", "LN-NAM-AIR", "OR-NAM-AIR", List.of("B1"));
    }
    private void prepareNext(BagForwarding.Command command) {
        jdbc.update("INSERT INTO sorting_manifest (manifest_no,manifest_type,source_org_code,destination_org_code,batch_no,manifest_status,version_no,content_hash,expected_package_qty) VALUES (?,'TRANSIT','LN-OR-RC','LN-NAM-AIR',?,'SEALED',1,?,1)", command.manifestNo(), command.batchNo(), "c".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_version (manifest_no,version_no,content_hash,expected_package_qty,expected_item_qty,snapshot_json) VALUES (?,1,?,1,1,'{}')", command.manifestNo(), "c".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_seal (manifest_no,version_no,request_no,request_hash,request_json,content_hash,source_org_code,actor_id,sealed_at) VALUES (?,1,?,?,'{}',?,'LN-OR-RC','source',CURRENT_TIMESTAMP)", command.manifestNo(), "S" + command.requestNo(), "b".repeat(64), "c".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_package (manifest_no,package_no,expected_seq_no,expected_weight,item_status) VALUES (?,'B1',1,100,'EXPECTED')", command.manifestNo());
        when(transport.arrival(command.manifestNo(), "secret")).thenReturn(Result.ok(new ManifestReceipt.Arrival("A2", "T2", command.manifestNo(), 1, "c".repeat(64), "LN-OR-RC", "LN-NAM-AIR", List.of("B1"))));
    }
    private SortingReceiveRequest nextReceive(BagForwarding.Command command) {
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("nam-receiver", "SORTER", "LN-NAM-AIR"));
        var request = request("NEXT", "B1"); request.setManifestNo(command.manifestNo()); request.setStationCode("LN-NAM-AIR"); return request;
    }
    private SortingReceiveRequest request(String key, String... bags) {
        var r = new SortingReceiveRequest(); r.setManifestNo("M1"); r.setStationCode("LN-OR-RC"); r.setOperatorId("FORGED");
        r.setReceiveMode("PREALERT"); r.setIdempotencyKey(key); r.setPackageNos(List.of(bags)); return r;
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private String value(String table, String column) { return jdbc.queryForObject("SELECT " + column + " FROM " + table, String.class); }
}
