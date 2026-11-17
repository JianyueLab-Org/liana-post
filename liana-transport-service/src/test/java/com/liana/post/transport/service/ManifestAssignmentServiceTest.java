package com.liana.post.transport.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.transport.client.DispatchClient;
import com.liana.post.transport.mapper.*;
import com.liana.post.transport.model.dto.*;
import com.liana.post.transport.repository.MyBatisTransportRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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

/** Real H2 transactions and MyBatis repository. MySQL collations/locking still need isolated acceptance. */
class ManifestAssignmentServiceTest {
    private JdbcTemplate jdbc;
    private ManifestAssignmentService service;
    private ManifestAssignmentPublisher publisher;
    private MyBatisTransportRepository repository;
    private DispatchClient dispatch;
    private HttpServletRequest http;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        // Read canonical migrations, removing only H2-incompatible MySQL storage/collation/JSON syntax.
        for (String file : List.of("V1__baseline.sql", "V2__manifest_allocations.sql", "V3__bag_receipt_confirmation.sql", "V4__manifest_closure_inbox.sql")) {
            String sql = Files.readString(Path.of("../database/migrations/transport_db", file))
                    .replaceAll("(?m)^--.*$", "")
                    .replaceAll(" CHARACTER SET \\w+", "").replaceAll(" COLLATE[= ]\\w+", "")
                    .replaceAll(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "")
                    .replaceAll("\\bJSON\\b", "VARCHAR(1000000)")
                    .replace("DROP CHECK", "DROP CONSTRAINT");
            for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        }
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(TransportAssetMapper.class, TransportRouteMapper.class, TransportScheduleMapper.class, TransportTaskMapper.class, TransportTaskBagMapper.class)) config.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config); factory.setTransactionFactory(new SpringManagedTransactionFactory());
        var session = new SqlSessionTemplate(factory.getObject());
        repository = new MyBatisTransportRepository(session.getMapper(TransportAssetMapper.class), session.getMapper(TransportRouteMapper.class), session.getMapper(TransportScheduleMapper.class), session.getMapper(TransportTaskMapper.class), session.getMapper(TransportTaskBagMapper.class));
        dispatch = mock(DispatchClient.class); http = mock(HttpServletRequest.class);
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("oro-clerk", "CLERK", "LN-OR-RC"));
        when(dispatch.manifest(anyString(), eq("secret"))).thenAnswer(call -> Result.ok(snapshot(call.getArgument(0), 100, 200)));
        when(dispatch.allocation(any(), eq("secret"))).thenAnswer(call -> Result.ok(new ManifestAllocation.Receipt(call.getArgument(0), Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))));
        var manager = new DataSourceTransactionManager(ds);
        service = new ManifestAssignmentService(jdbc, json, repository, dispatch, http, manager, "secret");
        publisher = new ManifestAssignmentPublisher(jdbc, json, dispatch, manager, "secret");
        jdbc.update("INSERT INTO transport_asset (id,code,name,type,capacity,status) VALUES (1,'ORO-AIR','Oro plane','AIRCRAFT',1,'AVAILABLE')");
        jdbc.update("INSERT INTO transport_route (id,route_code,facility_route_code,origin_facility_id,destination_facility_id,transport_type,status) VALUES (1,'ORO-NAM','ORO-AIR','LN-OR-RC','LN-NAM-AIR','AIR','ACTIVE')");
        jdbc.update("INSERT INTO transport_schedule (id,schedule_code,asset_id,route_id,departure_time,arrival_time,weekday,status) VALUES (1,'ORO-TOMORROW',1,1,?,?,?,'ACTIVE')", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), LocalDateTime.now(ZoneOffset.UTC).plusDays(1).plusHours(2), "MON");
    }

    @Test
    void wholeManifestIsAtomicAndOneTaskCarriesMultipleManifests() {
        var task = create("M1", 1L);
        var second = create("M2", 1L);
        assertEquals(task.getId(), second.getId());
        assertEquals(4, second.getBagNos().size());
        assertEquals(2, second.getManifestAssignments().size());
        assertEquals(4, count("transport_bag_current"));
        assertEquals(0, count("transport_task_bag"));
        assertThrows(BusinessException.class, () -> create("M1", 1L));
        assertEquals(2, count("transport_manifest_assignment"));
        assertEquals(2, publisher.publishDue());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM transport_manifest_assignment WHERE status='CONFIRMED'", Integer.class));
        assertEquals("oro-clerk", jdbc.queryForObject("SELECT actor_id FROM transport_manifest_assignment LIMIT 1", String.class));
    }

    @Test
    void unknownWeightIsRetainedAndCannotConsumeZeroCapacity() {
        when(dispatch.manifest(eq("UNKNOWN"), anyString())).thenReturn(Result.ok(snapshot("UNKNOWN", null, 200)));
        assertThrows(BusinessException.class, () -> create("UNKNOWN", 1L));
        assertEquals(0, count("transport_task"));
        var task = create("UNKNOWN", null);
        assertNull(jdbc.queryForObject("SELECT weight_grams FROM transport_assignment_bag WHERE bag_no='UNKNOWN-B1'", Integer.class));
        assertThrows(BusinessException.class, () -> service.assign(task.getTaskCode(), 1L));
        assertNull(repository.findTaskById(task.getId()).orElseThrow().getScheduleId());
    }

    @Test
    void cancellingKeepsCapacityUntilReleaseAcknowledgementAndPreservesHistory() {
        var task = create("M1", 1L); publisher.publishDue();
        service.status(task.getTaskCode(), status("CANCELLED", "cyclone warning"));
        assertEquals("CANCELLING", repository.findTaskById(task.getId()).orElseThrow().getStatus());
        assertEquals(2, count("transport_bag_current"));
        assertThrows(BusinessException.class, () -> create("M1", null));
        jdbc.update("UPDATE transport_asset SET capacity=0.4 WHERE id=1");
        assertThrows(BusinessException.class, () -> create("M2", 1L));
        publisher.publishDue();
        assertEquals("CANCELLED", repository.findTaskById(task.getId()).orElseThrow().getStatus());
        assertEquals(0, count("transport_bag_current"));
        var replacement = create("M1", null);
        assertNotEquals(task.getId(), replacement.getId());
        assertEquals(2, count("transport_manifest_assignment"));
        assertEquals("RELEASED", service.enrich(task).getManifestAssignments().get(0).get("status"));
        assertEquals("cyclone warning", task.getManifestAssignments().get(0).get("reason"));
    }

    @Test
    void lostAssignmentResponseIsRetriedBeforeCancellationRelease() {
        var task = create("M1", null);
        var saved = new ArrayList<ManifestAllocation.Command>();
        when(dispatch.allocation(any(), anyString())).thenAnswer(call -> { saved.add(call.getArgument(0)); throw new IllegalStateException("response lost after remote commit"); });
        publisher.publishDue();
        service.status(task.getTaskCode(), status("CANCELLED", "cancel"));
        assertTrue(publisher.due().isEmpty(), "release waits for a definitive assignment outcome");
        jdbc.update("UPDATE transport_assignment_outbox SET next_attempt_at=?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        doAnswer(call -> { saved.add(call.getArgument(0)); return Result.ok(new ManifestAllocation.Receipt(call.getArgument(0), Instant.now())); }).when(dispatch).allocation(any(), anyString());
        publisher.publishDue();
        assertEquals(saved.get(0), saved.get(1));
        assertEquals("RELEASING", jdbc.queryForObject("SELECT status FROM transport_manifest_assignment", String.class));
        publisher.publishDue();
        assertEquals("CANCELLED", repository.findTaskById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    void staleLeaseAndOldReleaseReceiptCannotClearReplacement() throws Exception {
        var task = create("M1", null);
        var first = publisher.due().get(0); assertTrue(publisher.claim(first));
        jdbc.update("UPDATE transport_assignment_outbox SET next_attempt_at=?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        var retry = publisher.due().get(0); assertTrue(publisher.claim(retry));
        var assign = json.readValue(first.payload(), ManifestAllocation.Command.class);
        publisher.confirm(first, new ManifestAllocation.Receipt(assign, Instant.now()));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM transport_manifest_assignment", String.class));
        publisher.confirm(retry, new ManifestAllocation.Receipt(assign, Instant.now()));
        service.status(task.getTaskCode(), status("CANCELLED", "cancel"));
        var release = publisher.due().get(0); publisher.claim(release);
        var receipt = new ManifestAllocation.Receipt(json.readValue(release.payload(), ManifestAllocation.Command.class), Instant.now());
        publisher.confirm(release, receipt);
        create("M1", null);
        publisher.confirm(release, receipt);
        publisher.confirm(retry, new ManifestAllocation.Receipt(assign, Instant.now()));
        assertEquals(2, count("transport_bag_current"));
        assertEquals(1, count("transport_manifest_current"));
    }

    @Test
    void cancelledSourceAssignmentConflictCanBeReleasedWithoutFabricatingConfirmation() {
        var task = create("M1", null);
        when(dispatch.allocation(any(), anyString())).thenReturn(Result.error("source manifest cancelled", 409));
        publisher.publishDue();
        assertEquals("CONFLICT", jdbc.queryForObject("SELECT status FROM transport_manifest_assignment", String.class));
        service.status(task.getTaskCode(), status("CANCELLED", "withdraw failed assignment"));
        when(dispatch.allocation(any(), anyString())).thenAnswer(call -> Result.ok(new ManifestAllocation.Receipt(call.getArgument(0), Instant.now())));
        publisher.publishDue();
        assertEquals(0, count("transport_bag_current"));
        assertNull(jdbc.queryForObject("SELECT confirmed_at FROM transport_manifest_assignment", java.sql.Timestamp.class));
    }

    @Test
    void concurrentReservationsHaveOnlyOneWinnerAndNoPartialRows() throws Exception {
        race(() -> create("M1", null), () -> create("M1", null));
        assertEquals(1, count("transport_task")); assertEquals(1, count("transport_manifest_assignment"));
        assertEquals(2, count("transport_assignment_bag")); assertEquals(2, count("transport_bag_current"));
    }

    @Test
    void taskRejectsForeignLegAndAggregatesCapacityAcrossEveryBag() {
        var task = create("M1", 1L);
        var otherLeg = new ManifestAllocation.Snapshot("M2", 1, "a".repeat(64), "LN-OR-RC", "LN-NAM-PORT", "ORO-SEA", snapshot("M2", 100, 200).bags());
        when(dispatch.manifest(eq("M2"), anyString())).thenReturn(Result.ok(otherLeg));
        assertThrows(BusinessException.class, () -> service.add(task.getTaskCode(), "M2"));
        jdbc.update("UPDATE transport_asset SET capacity=0.5 WHERE id=1");
        assertThrows(BusinessException.class, () -> service.add(task.getTaskCode(), "M3"));
        assertEquals(1, count("transport_manifest_assignment"));
        assertEquals(2, count("transport_assignment_bag"));
    }

    @Test
    void pendingOrMismatchedReceiptBlocksDepartureAndRetainsOccupation() {
        var task = create("M1", 1L);
        when(dispatch.getBagById(anyLong())).thenAnswer(call -> { var bag = new DispatchBagBriefResponse(); bag.setStatus("DISPATCHED"); bag.setTransportTaskCode(task.getTaskCode()); return Result.ok(bag); });
        assertThrows(BusinessException.class, () -> service.status(task.getTaskCode(), status("DEPARTED", null)));
        when(dispatch.allocation(any(), anyString())).thenAnswer(call -> {
            ManifestAllocation.Command c = call.getArgument(0);
            return Result.ok(new ManifestAllocation.Receipt(new ManifestAllocation.Command(c.requestNo(), c.assignmentNo(), c.manifestNo(), c.versionNo(), c.contentHash(), "WRONG-TASK", c.action(), c.actorId(), c.reason()), Instant.now()));
        });
        publisher.publishDue();
        assertEquals("CONFLICT", jdbc.queryForObject("SELECT status FROM transport_manifest_assignment", String.class));
        assertEquals(2, count("transport_bag_current"));
        assertThrows(BusinessException.class, () -> service.status(task.getTaskCode(), status("DEPARTED", null)));
    }

    @Test
    void cancellationAndDepartureCannotBothCommit() throws Exception {
        var task = create("M1", 1L); publisher.publishDue();
        when(dispatch.getBagById(anyLong())).thenAnswer(call -> {
            var bag = new DispatchBagBriefResponse(); bag.setStatus("DISPATCHED"); bag.setTransportTaskCode(task.getTaskCode()); return Result.ok(bag);
        });
        race(() -> service.status(task.getTaskCode(), status("CANCELLED", "weather")), () -> service.status(task.getTaskCode(), status("DEPARTED", null)));
        assertTrue(List.of("CANCELLING", "DEPARTED").contains(repository.findTaskById(task.getId()).orElseThrow().getStatus()));
        assertEquals(2, count("transport_bag_current"));
    }

    @Test
    void foreignFacilityAndMissingIdentityCannotMutateTasks() {
        var task = create("M1", null);
        when(http.getHeader("Authorization")).thenReturn("Bearer " + JwtTokenUtil.generateToken("nam-clerk", "CLERK", "LN-NAM-AIR"));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.status(task.getTaskCode(), status("CANCELLED", "cancel"))).getCode());
        assertEquals(403, assertThrows(BusinessException.class, () -> create("M2", null)).getCode());
        when(http.getHeader("Authorization")).thenReturn(null);
        assertEquals(401, assertThrows(BusinessException.class, () -> create("M2", null)).getCode());
    }

    private ManifestAllocation.Snapshot snapshot(String no, Integer first, Integer second) {
        return new ManifestAllocation.Snapshot(no, 1, "a".repeat(64), "LN-OR-RC", "LN-NAM-AIR", "ORO-AIR", List.of(
                new ManifestAllocation.Bag((long) no.hashCode() * 2, no + "-B1", first, List.of("CP000000001LN")),
                new ManifestAllocation.Bag((long) no.hashCode() * 2 + 1, no + "-B2", second, List.of("CP000000002LN"))));
    }

    @Test
    void partialReceiptsReleaseOnlyTheirBagsAndCompletionRequiresEveryManifest() {
        var task = create("M1", 1L); service.add(task.getTaskCode(), "M2"); publisher.publishDue();
        var arrivals = new ManifestArrivalService(jdbc, json);
        var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(BusinessException.class, () -> arrivals.arrival("M1"));
        jdbc.update("UPDATE transport_task SET status='ARRIVED' WHERE id=?", task.getId());
        var a = arrivals.arrival("M1");
        var first = receipt(a, "M1-B1", "E1", Instant.now().minusSeconds(10));
        var ack = tx.execute(s -> arrivals.receive(first));
        assertEquals(3, count("transport_bag_current")); assertEquals(2, count("transport_manifest_current"));
        assertEquals(ack, tx.execute(s -> arrivals.receive(first)));
        assertThrows(BusinessException.class, () -> service.status(task.getTaskCode(), status("COMPLETED", null)));
        var second = receipt(a, "M1-B2", "E2", first.receivedAt().minusSeconds(20));
        tx.execute(s -> arrivals.receive(second));
        assertEquals(1, count("transport_manifest_current"));
        assertEquals(first.receivedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), service.enrich(task).getManifestAssignments().stream().filter(row -> "M1".equals(row.get("manifestNo"))).findFirst().orElseThrow().get("receivedAt"));
        assertThrows(BusinessException.class, () -> service.status(task.getTaskCode(), status("COMPLETED", null)));
        var b = arrivals.arrival("M2");
        for (String bag : b.bagNos()) tx.execute(s -> arrivals.receive(receipt(b, bag, bag + "-EVENT", Instant.now())));
        assertEquals(0, count("transport_bag_current")); assertEquals(0, count("transport_manifest_current"));
        assertEquals("COMPLETED", service.status(task.getTaskCode(), status("COMPLETED", null)).getStatus());
        assertEquals(ack, tx.execute(s -> arrivals.receive(first)));
        assertThrows(BusinessException.class, () -> create("M1", null));
        assertEquals(2, count("transport_manifest_assignment")); assertEquals(4, count("transport_assignment_bag"));
    }

    @Test
    void forgedOrDuplicateDifferentReceiptCannotReleaseCurrentOccupation() {
        var task = create("M1", null); publisher.publishDue();
        var arrivals = new ManifestArrivalService(jdbc, json);
        var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        jdbc.update("UPDATE transport_task SET status='ARRIVED' WHERE id=?", task.getId());
        var a = arrivals.arrival("M1");
        var wrong = new com.liana.post.common.dto.sorting.ManifestReceipt.Arrival(a.assignmentNo(), "WRONG", a.manifestNo(), a.versionNo(), a.contentHash(), a.sourceOrgCode(), a.destinationOrgCode(), a.bagNos());
        assertThrows(BusinessException.class, () -> tx.execute(s -> arrivals.receive(receipt(wrong, "M1-B1", "E1", Instant.now()))));
        assertEquals(2, count("transport_bag_current")); assertEquals(0, count("transport_receipt_inbox"));
        var first = receipt(a, "M1-B1", "E1", Instant.now()); tx.execute(s -> arrivals.receive(first));
        assertThrows(BusinessException.class, () -> tx.execute(s -> arrivals.receive(receipt(a, "M1-B2", "E1", Instant.now()))));
        assertThrows(BusinessException.class, () -> tx.execute(s -> arrivals.receive(receipt(a, "M1-B1", "OTHER", Instant.now()))));
        assertEquals(1, count("transport_bag_current")); assertEquals(1, count("transport_receipt_inbox"));
    }

    @Test
    void missingSettlementReleasesOnlyOldAssignmentAndKeepsPhysicalReceiptsDistinct() {
        var task=create("M1",null); publisher.publishDue(); jdbc.update("UPDATE transport_task SET status='ARRIVED' WHERE id=?",task.getId());
        var arrivals=new ManifestArrivalService(jdbc,json); var a=arrivals.arrival("M1");
        var tx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var event=receipt(a,"M1-B1","E1",Instant.now()); var original=tx.execute(s->arrivals.receive(event));
        var command=new com.liana.post.common.dto.sorting.ManifestClosure.Command("CLOSE1",a,List.of("M1-B1"),List.of("M1-B2"),"admin",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        var ack=tx.execute(s->arrivals.close(command)); assertEquals(ack,tx.execute(s->arrivals.close(command)));
        assertEquals("SETTLED",jdbc.queryForObject("SELECT status FROM transport_manifest_assignment",String.class));
        assertNull(jdbc.queryForObject("SELECT received_at FROM transport_manifest_assignment",java.sql.Timestamp.class));
        assertEquals(0,count("transport_manifest_current")); assertEquals(0,count("transport_bag_current")); assertEquals(1,count("transport_receipt_inbox"));
        assertThrows(BusinessException.class,()->tx.execute(s->arrivals.receive(receipt(a,"M1-B2","LATE",Instant.now()))));
        assertEquals(original,tx.execute(s->arrivals.receive(event)));
        assertEquals("COMPLETED",service.status(task.getTaskCode(),status("COMPLETED",null)).getStatus());
        assertThrows(BusinessException.class,()->create("M1",null));
    }

    @Test
    void closureRejectsInventedReceiptPartitionAndLocalFailureRollsBackRelease() {
        var task=create("M1",null); publisher.publishDue(); jdbc.update("UPDATE transport_task SET status='ARRIVED' WHERE id=?",task.getId());
        var arrivals=new ManifestArrivalService(jdbc,json);var a=arrivals.arrival("M1");
        var tx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var wrong=new com.liana.post.common.dto.sorting.ManifestClosure.Command("CLOSE1",a,List.of("M1-B1"),List.of("M1-B2"),"admin",Instant.now());
        assertThrows(BusinessException.class,()->tx.execute(s->arrivals.close(wrong)));
        var correct=new com.liana.post.common.dto.sorting.ManifestClosure.Command("CLOSE1",a,List.of(),a.bagNos(),"admin",Instant.now());
        jdbc.execute("ALTER TABLE transport_manifest_close_inbox ADD CONSTRAINT fail_close CHECK (request_no<>'CLOSE1')");
        assertThrows(RuntimeException.class,()->tx.execute(s->arrivals.close(correct)));
        assertEquals(2,count("transport_bag_current"));assertEquals(1,count("transport_manifest_current"));
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM transport_manifest_assignment",String.class));
    }

    @Test
    void allReceivedClosurePreservesPhysicalCompletionTime() {
        var task=create("M1",null);publisher.publishDue();jdbc.update("UPDATE transport_task SET status='ARRIVED' WHERE id=?",task.getId());
        var arrivals=new ManifestArrivalService(jdbc,json);var a=arrivals.arrival("M1");
        var tx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        for(String bag:a.bagNos())tx.execute(s->arrivals.receive(receipt(a,bag,bag+"-EVENT",Instant.now().minusSeconds(10))));
        var receivedAt=jdbc.queryForObject("SELECT received_at FROM transport_manifest_assignment",java.sql.Timestamp.class);
        var command=new com.liana.post.common.dto.sorting.ManifestClosure.Command("CLOSE1",a,a.bagNos(),List.of(),"admin",Instant.now());
        var ack=tx.execute(s->arrivals.close(command));assertEquals(ack,tx.execute(s->arrivals.close(command)));
        assertEquals("RECEIVED",jdbc.queryForObject("SELECT status FROM transport_manifest_assignment",String.class));
        assertEquals(receivedAt,jdbc.queryForObject("SELECT received_at FROM transport_manifest_assignment",java.sql.Timestamp.class));
        assertEquals(2,count("transport_receipt_inbox"));assertEquals(0,count("transport_bag_current"));
    }

    private com.liana.post.common.dto.sorting.ManifestReceipt.Event receipt(com.liana.post.common.dto.sorting.ManifestReceipt.Arrival a, String bag, String event, Instant at) {
        return new com.liana.post.common.dto.sorting.ManifestReceipt.Event(event,"R1",a.manifestNo(),a.versionNo(),a.contentHash(),a.assignmentNo(),a.taskCode(),bag,a.sourceOrgCode(),a.destinationOrgCode(),"receiver",at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void concurrentFinalBagDeliveriesObserveEachOtherUnderTheTaskLock() throws Exception {
        var task = create("M1", null); publisher.publishDue();
        jdbc.update("UPDATE transport_task SET status='ARRIVED' WHERE id=?", task.getId());
        var arrivals = new ManifestArrivalService(jdbc, json); var a = arrivals.arrival("M1");
        var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return tx.execute(s -> arrivals.receive(receipt(a, "M1-B1", "E1", Instant.now()))); });
            var second = pool.submit(() -> { start.await(); return tx.execute(s -> arrivals.receive(receipt(a, "M1-B2", "E2", Instant.now()))); });
            start.countDown(); assertNotNull(first.get()); assertNotNull(second.get());
            assertEquals("RECEIVED", jdbc.queryForObject("SELECT status FROM transport_manifest_assignment", String.class));
            assertEquals(0, count("transport_manifest_current")); assertEquals(0, count("transport_bag_current"));
            assertEquals(2, count("transport_receipt_inbox"));
        } finally { pool.shutdownNow(); }
    }

    private com.liana.post.transport.model.entity.TransportTaskEntity create(String manifest, Long schedule) {
        var request = new TransportTaskRequest(); request.setManifestNo(manifest); request.setScheduleId(schedule); return service.create(request);
    }
    private TransportTaskStatusRequest status(String status, String reason) { var request = new TransportTaskStatusRequest(); request.setStatus(status); request.setReason(reason); return request; }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void race(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); return attempt(first); });
            var b = pool.submit(() -> { start.await(); return attempt(second); }); start.countDown();
            assertNotEquals(a.get(), b.get());
        } finally { pool.shutdownNow(); }
    }
    private boolean attempt(Runnable action) { try { action.run(); return true; } catch (BusinessException e) { assertEquals(409, e.getCode()); return false; } }
}
