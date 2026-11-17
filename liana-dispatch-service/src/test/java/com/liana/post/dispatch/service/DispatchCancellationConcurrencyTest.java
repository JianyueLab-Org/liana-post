package com.liana.post.dispatch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.dispatch.mapper.*;
import com.liana.post.dispatch.model.dto.*;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.repository.MyBatisDispatchRepository;
import com.liana.post.dispatch.service.impl.DispatchServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real repository SQL and service transactions on H2. Does not substitute for MySQL/InnoDB verification. */
class DispatchCancellationConcurrencyTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private DispatchServiceImpl service;
    private ManifestOutbox outbox;
    private String batchNo;
    private String manifestNo;
    private Long bagId;
    private DispatchManifestAssignmentService assignments;
    private BagForwardingService forwarding;
    private HttpServletRequest http;
    private com.liana.post.dispatch.client.FacilityRouteClient facility;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        // Only the columns used by these repositories; production DDL remains under database/migrations.
        jdbc.execute("CREATE TABLE dispatch_batch (id BIGINT AUTO_INCREMENT PRIMARY KEY,batch_no VARCHAR(32) UNIQUE,manifest_no VARCHAR(64) UNIQUE,"
                + "source_org_code VARCHAR(64),destination_org_code VARCHAR(64),facility_route_code VARCHAR(64),bag_no VARCHAR(32),route_code VARCHAR(64),status VARCHAR(32),approved_by BIGINT,approved_at TIMESTAMP(3),created_at TIMESTAMP(3),updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_batch_bag (id BIGINT AUTO_INCREMENT PRIMARY KEY,batch_no VARCHAR(32),bag_no VARCHAR(32),active_flag INT DEFAULT 1,created_at TIMESTAMP(3),UNIQUE(batch_no,bag_no),UNIQUE(bag_no,active_flag))");
        jdbc.execute("CREATE TABLE dispatch_bag (id BIGINT AUTO_INCREMENT PRIMARY KEY,bag_no VARCHAR(32) UNIQUE,origin_facility_code VARCHAR(64),destination_facility_code VARCHAR(64),"
                + "route_code VARCHAR(64),facility_route_code VARCHAR(64),transport_task_code VARCHAR(64),mail_no_list VARCHAR(1000),mail_type_code VARCHAR(64),"
                + "last_receipt_event_no VARCHAR(64),status VARCHAR(32),mail_count INT,total_weight_grams INT,created_at TIMESTAMP(3),updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE handoff_record (id BIGINT AUTO_INCREMENT PRIMARY KEY,handoff_no VARCHAR(32) UNIQUE,bag_no VARCHAR(32),batch_no VARCHAR(32),"
                + "from_facility_code VARCHAR(64),to_facility_code VARCHAR(64),handoff_time TIMESTAMP(3),receiver_id BIGINT,status VARCHAR(32),created_at TIMESTAMP(3),updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_manifest_outbox (event_no VARCHAR(64) PRIMARY KEY,manifest_no VARCHAR(64),version_no INT,"
                + "payload_json VARCHAR(100000),content_hash CHAR(64),status VARCHAR(16) DEFAULT 'PENDING',attempt_count INT DEFAULT 0,"
                + "next_attempt_at TIMESTAMP(3),last_error VARCHAR(512),created_at TIMESTAMP(3),sent_at TIMESTAMP(3),event_type VARCHAR(16) DEFAULT 'PREALERT',"
                + "receipt_json VARCHAR(10000),UNIQUE(manifest_no,version_no,event_type))");
        jdbc.execute("CREATE TABLE dispatch_manifest_assignment (manifest_no VARCHAR(64) PRIMARY KEY,assignment_no VARCHAR(64) UNIQUE,task_code VARCHAR(64),version_no INT,content_hash CHAR(64),confirmed_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_assignment_inbox (request_no VARCHAR(64) PRIMARY KEY,assignment_no VARCHAR(64),manifest_no VARCHAR(64),action VARCHAR(16),request_hash CHAR(64),request_json VARCHAR(10000),receipt_json VARCHAR(10000),confirmed_at TIMESTAMP(3),UNIQUE(assignment_no,action))");
        jdbc.execute("CREATE TABLE dispatch_receipt_inbox (event_no VARCHAR(64) PRIMARY KEY,manifest_no VARCHAR(64),bag_no VARCHAR(64),request_hash CHAR(64),payload_json VARCHAR(10000),receipt_json VARCHAR(10000),confirmed_at TIMESTAMP(3),UNIQUE(manifest_no,bag_no))");
        jdbc.execute("CREATE TABLE dispatch_forward_inbox (request_no VARCHAR(64) PRIMARY KEY,manifest_no VARCHAR(64) UNIQUE,previous_manifest_no VARCHAR(64),payload_json VARCHAR(10000),receipt_json VARCHAR(10000),confirmed_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_manifest_close_inbox (manifest_no VARCHAR(64) PRIMARY KEY,request_no VARCHAR(64) UNIQUE,payload_json VARCHAR(10000),receipt_json VARCHAR(10000),confirmed_at TIMESTAMP(3))");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(DispatchBagMapper.class); config.addMapper(DispatchBatchMapper.class);
        config.addMapper(DispatchBatchBagMapper.class); config.addMapper(HandoffRecordMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds); factory.setConfiguration(config); factory.setTransactionFactory(new SpringManagedTransactionFactory());
        var session = new SqlSessionTemplate(factory.getObject());
        var repository = new MyBatisDispatchRepository(null, session.getMapper(DispatchBagMapper.class), session.getMapper(DispatchBatchMapper.class),
                session.getMapper(DispatchBatchBagMapper.class), session.getMapper(HandoffRecordMapper.class), null);
        http = mock(HttpServletRequest.class);
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("oro-clerk", "CLERK", "LN-OR-01"));
        var packing = mock(BagPackingService.class);
        when(packing.state(any())).thenReturn(new BagPackingService.State(1, "CONFIRMED", null));
        when(packing.items("P1")).thenReturn(List.of(new PackingConfirmation.Item("CP000000001LN", 100)));
        when(packing.items("P2")).thenReturn(List.of(new PackingConfirmation.Item("CP000000002LN", 200)));
        outbox = new ManifestOutbox(jdbc, new ObjectMapper());
        assignments = new DispatchManifestAssignmentService(jdbc, new ObjectMapper().findAndRegisterModules(), outbox);
        service = new DispatchServiceImpl(repository, null, http, null, outbox, packing);
        facility = mock(com.liana.post.dispatch.client.FacilityRouteClient.class);
        var route = new com.liana.post.dispatch.client.FacilityRouteClient.FacilityRouteView();
        route.setRouteCode("RC-AIR"); route.setOriginFacilityCode("LN-OR-RC"); route.setDestinationFacilityCode("LN-NAM-AIR"); route.setStatus(1); route.setTransportMode("AIR");
        when(facility.listRoutes()).thenReturn(com.liana.post.common.model.Result.ok(List.of(route)));
        forwarding = new BagForwardingService(jdbc, new ObjectMapper().findAndRegisterModules(), repository, packing, outbox, facility, new DataSourceTransactionManager(ds));
        var batch = tx.execute(s -> {
            for (String no : List.of("P1", "P2")) {
                var bag = new DispatchBagEntity(); bag.setBagNo(no); bag.setOriginFacilityCode("LN-OR-01"); bag.setDestinationFacilityCode("LN-OR-RC");
                bag.setRouteCode("RULE-1"); bag.setMailTypeCode("C"); bag.setMailNoList("[]"); bag.setStatus("REVIEWED");
                bag.setFacilityRouteCode("LN-OR-LAND");
                repository.saveDispatchBag(bag);
                if ("P1".equals(no)) bagId = bag.getId();
            }
            var request = new DispatchBatchCreateRequest(); request.setBagNos(List.of("P1", "P2"));
            return service.createDispatchBatch(request);
        });
        batchNo = batch.getBatchNo(); manifestNo = batch.getManifestNo();
        var prealert = outbox.due().get(0); outbox.claim(prealert); outbox.sent(prealert);
        tx.execute(s -> service.sealDispatchBatch(batchNo));
        var seal = outbox.due().get(0); outbox.claim(seal); outbox.sent(seal, "{\"manifestNo\":\"" + manifestNo + "\",\"versionNo\":1,\"contentHash\":\"" + "a".repeat(64) + "\"}");
    }

    @Test
    void handoffWithoutAllocationCannotRacePastCancellation() throws Exception {
        var request = new HandoffCreateRequest(); request.setBagNo("P1"); request.setToFacilityCode("LN-OR-RC");
        boolean cancelled = race(() -> tx.execute(s -> service.cancelDispatchBatch(batchNo, new DispatchBatchCancelRequest("cancel"))),
                () -> tx.execute(s -> service.createHandoffRecord(request)));
        assertEquals(cancelled ? "CANCELLING" : "APPROVED", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
        assertEquals(cancelled ? 0 : 1, jdbc.queryForObject("SELECT COUNT(*) FROM handoff_record", Integer.class));
        assertEquals(cancelled ? "REVIEWED" : "DISPATCHED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P1'", String.class));
        assertEquals(cancelled ? "PENDING" : "NOT_REQUESTED", outbox.cancelState(manifestNo).status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag", Integer.class));
    }

    @Test
    void cancellingAndLinkingTransportTaskCannotBothCommit() throws Exception {
        var request = command("A1", "T1", "ASSIGN");
        boolean cancelled = race(() -> tx.execute(s -> service.cancelDispatchBatch(batchNo, new DispatchBatchCancelRequest("cancel"))),
                () -> tx.execute(s -> assignments.apply(request)));
        assertEquals(cancelled ? "CANCELLING" : "PENDING", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
        assertEquals(cancelled ? null : "T1", jdbc.queryForObject("SELECT transport_task_code FROM dispatch_bag WHERE bag_no='P1'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM handoff_record", Integer.class));
        assertEquals(cancelled ? 0 : 2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_bag WHERE transport_task_code='T1'", Integer.class));
    }

    @Test
    void releaseAndReassignmentRetainHistoryAndFenceDelayedCommands() {
        var first = command("A1", "T1", "ASSIGN");
        var receipt = tx.execute(s -> assignments.apply(first));
        assertEquals(receipt, tx.execute(s -> assignments.apply(first)));
        tx.execute(s -> assignments.apply(command("A1", "T1", "RELEASE")));
        tx.execute(s -> assignments.apply(command("A2", "T2", "ASSIGN")));
        tx.execute(s -> assignments.apply(first));
        tx.execute(s -> assignments.apply(command("A1", "T1", "RELEASE")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_bag WHERE transport_task_code='T2'", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_assignment_inbox", Integer.class));
        var changed = new com.liana.post.common.dto.dispatch.ManifestAllocation.Command(first.requestNo(), first.assignmentNo(), manifestNo, 1, "a".repeat(64), "OTHER", "ASSIGN", "clerk", "");
        assertThrows(BusinessException.class, () -> tx.execute(s -> assignments.apply(changed)));
    }

    @Test
    void releaseBeforeAssignmentCreatesTombstoneAndLegacyLinkIsClosed() {
        tx.execute(s -> assignments.apply(command("A1", "T1", "RELEASE")));
        assertThrows(BusinessException.class, () -> tx.execute(s -> assignments.apply(command("A1", "T1", "ASSIGN"))));
        var legacy = new DispatchTransportTaskLinkRequest(); legacy.setTransportTaskCode("T1");
        assertThrows(BusinessException.class, () -> service.linkTransportTask(bagId, legacy));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_assignment", Integer.class));
    }

    @Test
    void handoffRequiresAssignmentAndReleasePreservesPhysicalHandoff() {
        var handoff = new HandoffCreateRequest(); handoff.setBagNo("P1"); handoff.setToFacilityCode("LN-OR-RC");
        assertThrows(BusinessException.class, () -> tx.execute(s -> service.createHandoffRecord(handoff)));
        tx.execute(s -> assignments.apply(command("A1", "T1", "ASSIGN")));
        tx.execute(s -> service.createHandoffRecord(handoff));
        tx.execute(s -> assignments.apply(command("A1", "T1", "RELEASE")));
        assertEquals("DISPATCHED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P1'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM handoff_record", Integer.class));
        tx.execute(s -> assignments.apply(command("A2", "T2", "ASSIGN")));
        assertEquals("T2", jdbc.queryForObject("SELECT transport_task_code FROM dispatch_bag WHERE bag_no='P1'", String.class));
    }

    private com.liana.post.common.dto.dispatch.ManifestAllocation.Command command(String assignment, String task, String action) {
        return new com.liana.post.common.dto.dispatch.ManifestAllocation.Command(assignment + "-" + action, assignment, manifestNo, 1, "a".repeat(64), task, action, "clerk", "RELEASE".equals(action) ? "weather cancellation" : "");
    }

    @Test
    void bagReceiptRetainsHandoffAndHistoryThenClosesOnlyTheMatchingCurrentAssignment() {
        var assign = command("A1", "T1", "ASSIGN"); tx.execute(s -> assignments.apply(assign));
        var snapshot = tx.execute(s -> assignments.snapshot(manifestNo));
        var first = new com.liana.post.common.dto.sorting.ManifestReceipt.Event("E1", "R1", manifestNo, 1, "a".repeat(64), "A1", "T1", "P1", snapshot.originFacilityCode(), snapshot.destinationFacilityCode(), "receiver", java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThrows(BusinessException.class, () -> tx.execute(s -> assignments.receive(first)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_receipt_inbox", Integer.class));
        jdbc.update("UPDATE dispatch_bag SET status='DISPATCHED'");
        jdbc.update("INSERT INTO handoff_record (handoff_no,bag_no,batch_no,status) VALUES ('H1','P1',?,'DISPATCHED'),('H2','P2',?,'DISPATCHED')", batchNo, batchNo);
        var ack = tx.execute(s -> assignments.receive(first));
        assertEquals("ARRIVED", jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P1'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_assignment", Integer.class));
        var second = new com.liana.post.common.dto.sorting.ManifestReceipt.Event("E2", "R2", manifestNo, 1, "a".repeat(64), "A1", "T1", "P2", snapshot.originFacilityCode(), snapshot.destinationFacilityCode(), "receiver", first.receivedAt());
        tx.execute(s -> assignments.receive(second));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_assignment", Integer.class));
        assertEquals(ack, tx.execute(s -> assignments.receive(first)));
        tx.execute(s -> assignments.apply(assign)); // Lost ASSIGN response cannot resurrect completed allocation.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_assignment", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_bag WHERE status='ARRIVED' AND transport_task_code IS NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag WHERE active_flag=1", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag", Integer.class));
        assertThrows(BusinessException.class, () -> tx.execute(s -> assignments.apply(command("A2", "T2", "ASSIGN"))));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM handoff_record", Integer.class));
    }

    @Test
    void acknowledgementRetainsBatchMembershipAndMakesCancellationTerminal() {
        tx.execute(s -> service.cancelDispatchBatch(batchNo, new DispatchBatchCancelRequest("cancel")));
        var command = outbox.due().get(0); outbox.claim(command);
        tx.executeWithoutResult(s -> outbox.sent(command, "{\"stubbedReceiver\":true}"));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM dispatch_batch", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_bag WHERE status='REVIEWED'", Integer.class));
        var repeated = tx.execute(s -> service.cancelDispatchBatch(batchNo, new DispatchBatchCancelRequest("cancel")));
        assertEquals("CANCELLED", repeated.getStatus());
        assertEquals("SENT", repeated.getManifestCancelStatus());
        assertThrows(BusinessException.class, () -> tx.execute(s -> service.sealDispatchBatch(batchNo)));
        var request = new DispatchBatchCreateRequest(); request.setBagNo("P1");
        tx.execute(s -> service.createDispatchBatch(request));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag", Integer.class));
    }

    @Test
    void missingClosureReleasesOnlyMissingBagAndCannotRegressForwardedReceipt() {
        tx.execute(s->assignments.apply(command("A1","T1","ASSIGN")));jdbc.update("UPDATE dispatch_bag SET status='DISPATCHED'");
        var at=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var event=new com.liana.post.common.dto.sorting.ManifestReceipt.Event("E1","R1",manifestNo,1,"a".repeat(64),"A1","T1","P1","LN-OR-01","LN-OR-RC","receiver",at);
        var original=tx.execute(s->assignments.receive(event));
        // The received bag has already moved onward; closure must not touch it.
        jdbc.update("UPDATE dispatch_bag SET status='DISPATCHED',transport_task_code='T-NEXT' WHERE bag_no='P1'");
        var a=new com.liana.post.common.dto.sorting.ManifestReceipt.Arrival("A1","T1",manifestNo,1,"a".repeat(64),"LN-OR-01","LN-OR-RC",List.of("P1","P2"));
        var close=new com.liana.post.common.dto.sorting.ManifestClosure.Command("CLOSE1",a,List.of("P1"),List.of("P2"),"admin",at);
        var ack=tx.execute(s->assignments.close(close)); assertEquals(ack,tx.execute(s->assignments.close(close)));
        assertEquals("T-NEXT",jdbc.queryForObject("SELECT transport_task_code FROM dispatch_bag WHERE bag_no='P1'",String.class));
        assertEquals("MISSING",jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='P2'",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_assignment",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_receipt_inbox",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag",Integer.class));
        var late=new com.liana.post.common.dto.sorting.ManifestReceipt.Event("LATE","R2",manifestNo,1,"a".repeat(64),"A1","T1","P2","LN-OR-01","LN-OR-RC","receiver",at);
        assertThrows(BusinessException.class,()->tx.execute(s->assignments.receive(late)));
        assertEquals(original,tx.execute(s->assignments.receive(event)));
        assertThrows(BusinessException.class,()->tx.execute(s->assignments.apply(command("A2","T2","ASSIGN"))));
    }

    @Test
    void dispatchClosureRollbackKeepsMissingOccupationAndAllReceivedClosureWorksWithoutCurrentAssignment() {
        tx.execute(s->assignments.apply(command("A1","T1","ASSIGN")));jdbc.update("UPDATE dispatch_bag SET status='DISPATCHED'");
        var at=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var a=new com.liana.post.common.dto.sorting.ManifestReceipt.Arrival("A1","T1",manifestNo,1,"a".repeat(64),"LN-OR-01","LN-OR-RC",List.of("P1","P2"));
        var missing=new com.liana.post.common.dto.sorting.ManifestClosure.Command("FAIL",a,List.of(),List.of("P1","P2"),"admin",at);
        jdbc.execute("ALTER TABLE dispatch_manifest_close_inbox ADD CONSTRAINT reject_close CHECK (request_no<>'FAIL')");
        assertThrows(RuntimeException.class,()->tx.execute(s->assignments.close(missing)));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_bag WHERE transport_task_code='T1'",Integer.class));
        for(String bag:List.of("P1","P2")) tx.execute(s->assignments.receive(new com.liana.post.common.dto.sorting.ManifestReceipt.Event(bag+"-E","R1",manifestNo,1,"a".repeat(64),"A1","T1",bag,"LN-OR-01","LN-OR-RC","receiver",at)));
        var complete=new com.liana.post.common.dto.sorting.ManifestClosure.Command("CLOSE1",a,List.of("P1","P2"),List.of(),"admin",at);
        assertNotNull(tx.execute(s->assignments.close(complete)));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_receipt_inbox",Integer.class));
    }

    /** Returns whether the first operation won. Both transactions must either succeed or report business conflict. */
    private boolean race(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); return attempt(first); });
            var b = pool.submit(() -> { start.await(); return attempt(second); });
            start.countDown();
            boolean one = a.get(), two = b.get();
            assertNotEquals(one, two);
            return one;
        } finally { pool.shutdownNow(); }
    }

    private boolean attempt(Runnable operation) {
        try { operation.run(); return true; }
        catch (BusinessException e) { assertEquals(409, e.getCode()); return false; }
    }

    @Test
    void forwardingReusesPackingButSealsAndHandsOffAnIndependentSecondLeg() throws Exception {
        tx.execute(s -> assignments.apply(command("A1", "T1", "ASSIGN")));
        var handoff = new HandoffCreateRequest(); handoff.setBagNo("P1"); handoff.setToFacilityCode("LN-OR-RC");
        tx.execute(s -> service.createHandoffRecord(handoff));
        var first = new com.liana.post.common.dto.sorting.ManifestReceipt.Event("E1", "R1", manifestNo, 1, "a".repeat(64), "A1", "T1", "P1", "LN-OR-01", "LN-OR-RC", "receiver", java.time.Instant.now());
        var firstAck = tx.execute(s -> assignments.receive(first));
        var command = new com.liana.post.common.dto.dispatch.BagForwarding.Command("F1", manifestNo, "M-NEXT", "D-NEXT", "LN-OR-RC", "LN-NAM-AIR", "RC-AIR", "clerk", List.of(new com.liana.post.common.dto.dispatch.BagForwarding.Bag("P1", "E1")));
        var forwarded = forwarding.accept(command);
        reset(facility);
        assertEquals(forwarded, forwarding.accept(command)); verifyNoInteractions(facility);
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag", Integer.class));
        assertEquals("LN-OR-01", jdbc.queryForObject("SELECT origin_facility_code FROM dispatch_bag WHERE bag_no='P1'", String.class));
        var prealert = outbox.due().get(0); outbox.claim(prealert); outbox.sent(prealert);
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("rc-clerk", "CLERK", "LN-OR-RC"));
        tx.execute(s -> service.sealDispatchBatch("D-NEXT"));
        var seal = outbox.due().get(0); outbox.claim(seal); outbox.sent(seal, "{\"manifestNo\":\"M-NEXT\",\"versionNo\":1,\"contentHash\":\"" + "b".repeat(64) + "\"}");
        var snapshot = tx.execute(s -> assignments.snapshot("M-NEXT"));
        assertEquals("LN-OR-RC", snapshot.originFacilityCode()); assertEquals("RC-AIR", snapshot.facilityRouteCode());
        tx.execute(s -> assignments.apply(new com.liana.post.common.dto.dispatch.ManifestAllocation.Command("A2-ASSIGN", "A2", "M-NEXT", 1, "b".repeat(64), "T2", "ASSIGN", "rc-clerk", "")));
        handoff.setToFacilityCode("LN-NAM-AIR"); tx.execute(s -> service.createHandoffRecord(handoff));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM handoff_record WHERE bag_no='P1'", Integer.class));
        assertEquals(firstAck, tx.execute(s -> assignments.receive(first)));
        assertEquals("T2", jdbc.queryForObject("SELECT transport_task_code FROM dispatch_bag WHERE bag_no='P1'", String.class));
        var second = new com.liana.post.common.dto.sorting.ManifestReceipt.Event("E-NEXT", "R-NEXT", "M-NEXT", 1, "b".repeat(64), "A2", "T2", "P1", "LN-OR-RC", "LN-NAM-AIR", "receiver", java.time.Instant.now());
        tx.execute(s -> assignments.receive(second));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_receipt_inbox WHERE bag_no='P1'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag WHERE bag_no='P1' AND active_flag=1", Integer.class));
        assertEquals("LN-OR-01", jdbc.queryForObject("SELECT origin_facility_code FROM dispatch_bag WHERE bag_no='P1'", String.class));
        assertThrows(BusinessException.class, () -> tx.execute(s -> assignments.apply(command("A3", "T3", "ASSIGN"))));
    }
}
