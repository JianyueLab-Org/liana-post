package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.sorting.client.UnpackingClient;
import com.liana.post.sorting.model.dto.SortingUnpackItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Exercises canonical migrations with H2 adapters; MySQL acceptance remains separate. */
class UnpackingServiceTest {
    private JdbcTemplate jdbc;
    private UnpackingService service;
    private UnpackingPublisher publisher;
    private UnpackingClient oms;
    private HttpServletRequest http;
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
        var manager = new DataSourceTransactionManager(ds);
        http = mock(HttpServletRequest.class);
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("sorter", "SORTER", "LN-OR-RC"));
        var receiving = new ManifestReceivingService(jdbc, json, null, manager, http, "secret");
        service = new UnpackingService(jdbc, json, receiving, http, manager);
        oms = mock(UnpackingClient.class);
        publisher = new UnpackingPublisher(jdbc, json, oms, "secret");
        jdbc.update("INSERT INTO sorting_manifest (manifest_no,manifest_type,source_org_code,destination_org_code,batch_no,manifest_status,version_no,content_hash) VALUES ('M1','TRANSIT','LN-OR-01','LN-OR-RC','D1','CHECKED',1,?)", "a".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_version (manifest_no,version_no,content_hash,expected_package_qty,expected_item_qty,snapshot_json) VALUES ('M1',1,?,1,2,'{}')", "a".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_seal (manifest_no,version_no,request_no,request_hash,request_json,content_hash,source_org_code,actor_id,sealed_at) VALUES ('M1',1,'SEAL',?,'{}',?,'LN-OR-01','source',CURRENT_TIMESTAMP)", "b".repeat(64), "a".repeat(64));
        jdbc.update("INSERT INTO sorting_manifest_package (manifest_no,package_no,expected_seq_no,item_status,received_at) VALUES ('M1','B1',1,'RECEIVED',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sorting_total_package (package_no,package_level,package_status,source_org_code,destination_org_code,manifest_no) VALUES ('B1','TRANSIT','RECEIVED','LN-OR-01','LN-OR-RC','M1')");
        jdbc.update("INSERT INTO sorting_receive_request (request_no,manifest_no,version_no,content_hash,request_hash,request_json,receipt_json,received_at) VALUES ('RECEIVE','M1',1,?,?,'{}','{}',CURRENT_TIMESTAMP)", "a".repeat(64), "b".repeat(64));
        jdbc.update("INSERT INTO sorting_bag_receipt (event_no,request_no,manifest_no,version_no,bag_no,assignment_no,task_code,source_org_code,destination_org_code,actor_id,received_at) VALUES ('RECEIVE','RECEIVE','M1',1,'B1','A1','T1','LN-OR-01','LN-OR-RC','SORTER',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sorting_bag_position (bag_no,facility_code,last_manifest_no,last_event_no,container_status,updated_at) VALUES ('B1','LN-OR-RC','M1','RECEIVE','SEALED',CURRENT_TIMESTAMP)");
        for (String mail : List.of("CP000000001LN", "CP000000002LN")) jdbc.update("INSERT INTO sorting_manifest_item (manifest_no,item_no,expected_package_no) VALUES ('M1',?,'B1')", mail);
        when(oms.accept(any(), eq("secret"))).thenAnswer(call -> Result.ok(new UnpackingConfirmation.Ack(call.getArgument(0), Instant.now())));
    }

    @Test
    void partialScanRecordsFactsAndQuarantinesSurplusWithoutSendingItToOms() throws Exception {
        var lines = service.unpack(request("R1", "cp000000001ln", "CP000000003LN"));
        assertEquals(2, lines.size()); assertEquals("SORTER", lines.get(0).getOperatorId());
        assertEquals("OPENED", value("sorting_total_package", "package_status"));
        assertEquals("OPENED", value("sorting_bag_position", "container_status"));
        assertEquals("DISCREPANCY", value("sorting_manifest", "manifest_status"));
        assertEquals(2, count("discrepancy_verification"));
        assertEquals("MISSING", jdbc.queryForObject("SELECT item_status FROM sorting_manifest_item WHERE item_no='CP000000002LN'", String.class));
        assertEquals(List.of("CP000000001LN"), service.receipt("R1").command().mailNos());
        assertThrows(BusinessException.class, () -> service.requireMailReady("CP000000001LN", "LN-OR-RC"));
        verifyNoInteractions(oms);
        assertEquals(1, publisher.publishDue());
        assertDoesNotThrow(() -> service.requireMailReady("CP000000001LN", "LN-OR-RC"));
        assertThrows(BusinessException.class, () -> service.requireMailReady("CP000000003LN", "LN-OR-RC"));
        assertEquals("SENT", service.receipt("R1").status());
    }

    @Test
    void emptyCompletionMarksAllExpectedMailMissingAndStillDeliversOpeningReceipt() {
        assertTrue(service.unpack(request("R1")).isEmpty());
        assertEquals(2, count("discrepancy_verification")); assertEquals(0, count("package_item_line"));
        assertTrue(service.receipt("R1").command().mailNos().isEmpty());
        assertEquals(1, publisher.publishDue());
    }

    @Test
    void lostResponseReplaysExactLinesAfterStateMovesButChangedContentCannotReuseKey() throws Exception {
        var request = request("R1", "CP000000001LN", "CP000000002LN");
        String original = json.writeValueAsString(service.unpack(request));
        jdbc.update("UPDATE sorting_manifest SET manifest_status='CLOSED'");
        assertEquals(original, json.writeValueAsString(service.unpack(request)));
        assertThrows(BusinessException.class, () -> service.unpack(request("R1", "CP000000001LN")));
        assertThrows(BusinessException.class, () -> service.unpack(request("R2", "CP000000001LN")));
        assertEquals(2, count("package_item_line")); assertEquals(1, count("sorting_unpack_request"));
    }

    @Test
    void duplicateScansReservedBagsAndCancelledManifestsCannotCommitAnything() {
        assertThrows(BusinessException.class, () -> service.unpack(request("R1", "CP000000001LN", "cp000000001ln")));
        jdbc.update("UPDATE sorting_bag_position SET outgoing_manifest_no='M2'");
        assertThrows(BusinessException.class, () -> service.unpack(request("R1", "CP000000001LN")));
        jdbc.update("UPDATE sorting_bag_position SET outgoing_manifest_no=NULL");
        jdbc.update("UPDATE sorting_manifest SET manifest_status='CANCELLED'");
        assertThrows(BusinessException.class, () -> service.unpack(request("R1", "CP000000001LN")));
        assertEquals(0, count("sorting_unpack_request")); assertEquals(0, count("package_item_line"));
        assertEquals("SEALED", value("sorting_bag_position", "container_status"));
    }

    @Test
    void localFailureRollsBackScanDifferencesAndOpeningTogether() {
        jdbc.execute("ALTER TABLE package_item_line ADD CONSTRAINT fail_scan CHECK (item_no <> 'CP000000002LN')");
        assertThrows(RuntimeException.class, () -> service.unpack(request("R1", "CP000000001LN", "CP000000002LN")));
        assertEquals(0, count("sorting_unpack_request")); assertEquals(0, count("sorting_unpack_item"));
        assertEquals(0, count("package_item_line")); assertEquals(0, count("discrepancy_verification"));
        assertEquals("RECEIVED", value("sorting_total_package", "package_status"));
    }

    @Test
    void failedDeliveryRetriesAndStaleWorkerCannotConfirmNewLease() throws Exception {
        service.unpack(request("R1", "CP000000001LN"));
        when(oms.accept(any(), anyString())).thenThrow(new RuntimeException("offline"));
        assertEquals(0, publisher.publishDue()); assertEquals("RETRY", service.receipt("R1").status());
        String payload = value("sorting_unpack_request", "payload_json");
        var old = new UnpackingPublisher.Delivery("R1", payload, 1);
        jdbc.update("UPDATE sorting_unpack_request SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00'");
        assertTrue(publisher.claim(old));
        jdbc.update("UPDATE sorting_unpack_request SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00'");
        var current = new UnpackingPublisher.Delivery("R1", payload, 2);
        assertTrue(publisher.claim(current));
        var ack = new UnpackingConfirmation.Ack(service.receipt("R1").command(), Instant.now());
        assertEquals(0, publisher.confirm(old, ack)); assertEquals(1, publisher.confirm(current, ack));
    }

    @Test
    void mismatchedAcknowledgementStaysBlockedAndCannotReleaseMail() {
        service.unpack(request("R1", "CP000000001LN"));
        var c = service.receipt("R1").command();
        var wrong = new UnpackingConfirmation("OTHER", c.manifestNo(), c.bagNo(), c.facilityCode(), c.actorId(), c.occurredAt(), c.mailNos());
        when(oms.accept(any(), anyString())).thenReturn(Result.ok(new UnpackingConfirmation.Ack(wrong, Instant.now())));
        assertEquals(0, publisher.publishDue()); assertEquals("CONFLICT", service.receipt("R1").status());
        assertThrows(BusinessException.class, () -> service.requireMailReady("CP000000001LN", "LN-OR-RC"));
    }

    @Test
    void concurrentCompletionCreatesOnlyOneOpeningAndReplayStillRequiresFacilityIdentity() throws Exception {
        var request = request("R1", "CP000000001LN");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.unpack(request)); var second = pool.submit(() -> service.unpack(request));
            assertEquals(json.writeValueAsString(first.get(10, TimeUnit.SECONDS)), json.writeValueAsString(second.get(10, TimeUnit.SECONDS)));
        } finally { pool.shutdownNow(); }
        assertEquals(1, count("sorting_unpack_request")); assertEquals(1, count("package_item_line"));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("other", "SORTER", "LN-NAM-HQ"));
        assertThrows(BusinessException.class, () -> service.unpack(request));
        assertThrows(BusinessException.class, () -> service.receipt("R1"));
    }
    private SortingUnpackItemRequest request(String key, String... mails) {
        var r = new SortingUnpackItemRequest(); r.setStationCode("LN-OR-RC"); r.setManifestNo("M1"); r.setPackageNo("B1");
        r.setScanMode("PREALERT"); r.setIdempotencyKey(key); r.setOperatorId("UNTRUSTED");
        r.setItems(Arrays.stream(mails).map(no -> { var e = new SortingUnpackItemRequest.SortingUnpackItemEntry(); e.setItemNo(no); return e; }).toList());
        return r;
    }

    @Test
    void structuredRepackingReservesActualScanOnceAndReplaysWithoutOmsLookup() {
        for (String destination : List.of("DISPATCH", "TRANSPORT")) jdbc.update("INSERT INTO sorting_receipt_outbox (event_no,destination,payload_json,status,next_attempt_at,created_at) VALUES ('RECEIVE',?,'{}','SENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", destination);
        service.unpack(request("R1", "CP000000001LN", "CP000000002LN"));
        assertEquals(1, publisher.publishDue());
        var omsClient = mock(com.liana.post.sorting.client.OmsClient.class);
        for (String no : List.of("CP000000001LN", "CP000000002LN")) {
            var mail = new com.liana.post.oms.model.dto.MailResponse();
            mail.setWaybillNo(no); mail.setCurrentFacilityCode("LN-OR-RC"); mail.setStatus("SORTED"); mail.setWeightGrams(125);
            when(omsClient.getMail(no)).thenReturn(Result.ok(mail));
            jdbc.update("INSERT INTO package_item_line (biz_line_no,idempotency_key,item_no,action_type,event_type,scan_mode,to_package_no,target_center_code,station_code,event_time,ext) VALUES (?, ?,?,'LOAD','ROUTE','BLIND','SLOT1','SLOT1','LN-OR-RC',DATEADD('SECOND',1,CURRENT_TIMESTAMP),?)",
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(), no, "{\"nextHop\":\"LN-NAM-AIR\"}");
        }
        var manager = new DataSourceTransactionManager(jdbc.getDataSource());
        var repacking = new RepackingService(jdbc, json, omsClient, service, manager);
        var bag = repacking.reserve("REPACK1", "LN-OR-RC", "SLOT1", null, null, null);
        assertEquals("PACKING_PENDING", bag.getPackageStatus()); assertEquals("LN-NAM-AIR", bag.getDestinationOrgCode());
        assertEquals(2, count("sorting_repack_item"));
        reset(omsClient);
        assertEquals(bag.getPackageNo(), repacking.reserve("REPACK1", "LN-OR-RC", "SLOT1", null, null, null).getPackageNo());
        verifyNoInteractions(omsClient);
        assertThrows(BusinessException.class, () -> service.requireMailReady("CP000000001LN", "LN-OR-RC"));
        var dispatch = mock(com.liana.post.sorting.client.DispatchLifecycleClient.class);
        when(dispatch.repack(any(), eq("secret"))).thenAnswer(call -> Result.ok(new com.liana.post.common.dto.dispatch.RepackingCommand.Ack(call.getArgument(0), Instant.now())));
        var repackPublisher = new RepackingPublisher(jdbc, json, dispatch, "secret");
        assertEquals(1, repackPublisher.publishDue()); assertEquals("SENT", repacking.receipt("REPACK1").status());
        assertEquals(1, count("sorting_manifest"));
    }

    @Test
    void unresolvedDifferenceStopsRepackingAndReviewDoesNotInventReceipts() {
        service.unpack(request("R1", "CP000000001LN", "CP000000003LN")); publisher.publishDue();
        var manager = new DataSourceTransactionManager(jdbc.getDataSource());
        var disposition = new DiscrepancyDispositionService(jdbc, json, http, manager);
        var missing = jdbc.queryForObject("SELECT verification_no FROM discrepancy_verification WHERE discrepancy_type='MISSING'", String.class);
        var input = new DiscrepancyDispositionService.Request("REVIEW1", "CONFIRMED_NOT_RECEIVED", "已核对开拆清单及模拟扫码记录，确认本件未收到");
        assertThrows(BusinessException.class, () -> disposition.apply(missing, input));
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("admin", "ADMIN", "LN-OR-RC"));
        var original = disposition.apply(missing, input);
        assertEquals(original, disposition.apply(missing, input));
        assertEquals("MISSING", jdbc.queryForObject("SELECT item_status FROM sorting_manifest_item WHERE item_no='CP000000002LN'", String.class));
        assertEquals(2, count("package_item_line")); assertEquals(2, count("discrepancy_verification"));
        assertThrows(BusinessException.class, () -> disposition.apply(missing, new DiscrepancyDispositionService.Request("OTHER", "CONFIRMED_NOT_RECEIVED", input.reason())));
        String surplus = jdbc.queryForObject("SELECT verification_no FROM discrepancy_verification WHERE discrepancy_type='SURPLUS'", String.class);
        jdbc.update("UPDATE discrepancy_verification SET discrepancy_source='SECURITY' WHERE verification_no=?",surplus);
        assertThrows(BusinessException.class,()->disposition.apply(surplus,new DiscrepancyDispositionService.Request("REVIEW2","QUARANTINED","已登记预期外邮件并保持隔离，等待后续核查处理")));
        jdbc.update("UPDATE discrepancy_verification SET discrepancy_source='MANIFEST' WHERE verification_no=?",surplus);
        disposition.apply(surplus, new DiscrepancyDispositionService.Request("REVIEW2", "QUARANTINED", "已登记预期外邮件并保持隔离，等待后续核查处理"));
        assertThrows(BusinessException.class, () -> service.requireMailReady("CP000000003LN", "LN-OR-RC"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM discrepancy_verification WHERE status='OPEN'", Integer.class));
    }

    @Test
    void normalOpenedManifestCanCloseAfterFixedCheckAndBothReceiptConfirmations() {
        service.unpack(request("OPEN1","CP000000001LN","CP000000002LN")); publisher.publishDue();
        for (String destination : List.of("DISPATCH", "TRANSPORT")) jdbc.update("INSERT INTO sorting_receipt_outbox (event_no,destination,payload_json,status,next_attempt_at,created_at) VALUES ('RECEIVE',?,'{}','SENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", destination);
        var transport=mock(com.liana.post.sorting.client.TransportReceiptClient.class);
        var arrival=new com.liana.post.common.dto.sorting.ManifestReceipt.Arrival("A1","T1","M1",1,"a".repeat(64),"LN-OR-01","LN-OR-RC",List.of("B1"));
        when(transport.arrival("M1","secret")).thenReturn(Result.ok(arrival));
        var closure=new ManifestClosureService(jdbc,json,http,transport,new DataSourceTransactionManager(jdbc.getDataSource()),"secret");
        assertTrue(closure.finishCheck("M1",new ManifestClosureService.Request("CHECK1","LN-OR-RC")).missingBagNos().isEmpty());
        when(http.getHeader("Authorization")).thenReturn("Bearer "+JwtTokenUtil.generateToken("admin","ADMIN","LN-NAM-HQ"));
        var state=closure.close("M1",new ManifestClosureService.Request("CLOSE1","LN-OR-RC"));
        assertEquals("CLOSING",state.status()); assertEquals(List.of("B1"),state.command().receivedBagNos());
        assertEquals(0,count("discrepancy_verification")); assertEquals("OPENED",value("sorting_bag_position","container_status"));
        assertThrows(BusinessException.class,()->service.unpack(request("NEW","CP000000001LN")));
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private String value(String table, String field) { return jdbc.queryForObject("SELECT " + field + " FROM " + table, String.class); }
}
