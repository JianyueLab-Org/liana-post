package com.liana.post.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.RepackingCommand;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.dispatch.client.FacilityRouteClient;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.repository.DispatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Consumer transaction, canonical packing/repacking DDL and real uniqueness constraints. */
class RepackingServiceTest {
    private JdbcTemplate jdbc; private RepackingService service; private FacilityRouteClient facility;
    private final Instant at=Instant.parse("2026-11-17T10:00:00Z");
    @BeforeEach
    void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE dispatch_bag (bag_no VARCHAR(32) PRIMARY KEY,status VARCHAR(32),transport_task_code VARCHAR(64),total_weight_grams INT,updated_at TIMESTAMP(3))");
        jdbc.execute("CREATE TABLE dispatch_batch_bag (bag_no VARCHAR(32),active_flag INT)");
        for(String file:List.of("V3__structured_bag_packing.sql","V9__repacking_inbox.sql")) {
            String sql=Files.readString(Path.of("../database/migrations/dispatch_db",file)).replaceAll("(?m)^--.*$","")
                    .replaceAll(" CHARACTER SET \\w+","").replaceAll(" COLLATE[= ]\\w+","")
                    .replaceAll(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","").replaceAll("\\bJSON\\b","VARCHAR(1000000)");
            for(String statement:sql.split(";")) if(!statement.isBlank()) jdbc.execute(statement);
        }
        var json=new ObjectMapper().findAndRegisterModules(); var packing=new BagPackingService(jdbc,json);
        jdbc.update("INSERT INTO dispatch_bag (bag_no,status) VALUES ('SOURCE','ARRIVED')");
        new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(s ->
                packing.prepare(new com.liana.post.common.dto.dispatch.PackingConfirmation("ORIGINAL","SOURCE","LN-OR-01",1,
                        List.of(new com.liana.post.common.dto.dispatch.PackingConfirmation.Item("CP000000001LN",125)))));
        var repository=mock(DispatchRepository.class);
        when(repository.saveDispatchBag(any())).thenAnswer(c->{DispatchBagEntity b=c.getArgument(0); jdbc.update("INSERT INTO dispatch_bag (bag_no,status,total_weight_grams) VALUES (?,?,?)",b.getBagNo(),b.getStatus(),b.getTotalWeightGrams()); return b;});
        facility=mock(FacilityRouteClient.class); var edge=new FacilityRouteClient.FacilityRouteView();
        edge.setRouteCode("RC-AIR"); edge.setOriginFacilityCode("LN-OR-RC"); edge.setDestinationFacilityCode("LN-NAM-AIR"); edge.setStatus(1);
        when(facility.listRoutes()).thenReturn(Result.ok(List.of(edge)));
        service=new RepackingService(jdbc,json,facility,repository,packing,new DataSourceTransactionManager(ds));
    }
    @Test
    void replayKeepsOriginalBagAndPreparedAcknowledgementDoesNotClaimOmsConfirmation() {
        var command=command("R1","NEW1"); var ack=service.accept(command);
        reset(facility); assertEquals(ack,service.accept(command)); verifyNoInteractions(facility);
        assertEquals("CREATED",jdbc.queryForObject("SELECT status FROM dispatch_bag WHERE bag_no='NEW1'",String.class));
        assertEquals("PENDING",jdbc.queryForObject("SELECT sync_status FROM dispatch_bag_packing WHERE bag_no='NEW1'",String.class));
        assertEquals(1,count("dispatch_repack_source")); assertEquals(2,count("dispatch_bag_item"));
        assertThrows(BusinessException.class,()->service.accept(command("R1","CHANGED")));
    }
    @Test
    void finalConstraintFailureRollsBackBagPackingSourcesAndInbox() {
        jdbc.execute("ALTER TABLE dispatch_repack_inbox ADD CONSTRAINT reject_inbox CHECK (request_no<>'R1')");
        assertThrows(RuntimeException.class,()->service.accept(command("R1","NEW1")));
        assertEquals(1,count("dispatch_bag")); assertEquals(1,count("dispatch_bag_packing")); assertEquals(1,count("dispatch_bag_item"));
        assertEquals(0,count("dispatch_repack_source")); assertEquals(0,count("dispatch_repack_inbox"));
    }
    @Test
    void sourceStillInTransitAndMailOutsideOriginalPackingCannotProduceNewBag() {
        jdbc.update("UPDATE dispatch_bag SET status='DISPATCHED'");
        assertThrows(BusinessException.class,()->service.accept(command("R1","NEW1")));
        jdbc.update("UPDATE dispatch_bag SET status='ARRIVED'");
        var wrong=new RepackingCommand("R1","NEW1","LN-OR-RC","LN-NAM-AIR","SORTER",at,List.of(new RepackingCommand.Item("CP000000002LN","SOURCE","OPEN1",125)));
        assertThrows(BusinessException.class,()->service.accept(wrong)); assertEquals(1,count("dispatch_bag"));
    }
    @Test
    void concurrentReservationsOfSameScanCommitOneCompleteNewBag() throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{start.await();return accept("R1","NEW1");});
            var second=pool.submit(()->{start.await();return accept("R2","NEW2");});
            start.countDown();assertNotEquals(first.get(),second.get());
        } finally {pool.shutdownNow();}
        assertEquals(2,count("dispatch_bag"));assertEquals(2,count("dispatch_bag_packing"));
        assertEquals(1,count("dispatch_repack_inbox"));assertEquals(1,count("dispatch_repack_source"));
    }
    private boolean accept(String request,String bag) {try{service.accept(command(request,bag));return true;}catch(BusinessException e){assertEquals(409,e.getCode());return false;}}
    private RepackingCommand command(String request,String bag){return new RepackingCommand(request,bag,"LN-OR-RC","LN-NAM-AIR","SORTER",at,List.of(new RepackingCommand.Item("CP000000001LN","SOURCE","OPEN1",125)));}
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
}
