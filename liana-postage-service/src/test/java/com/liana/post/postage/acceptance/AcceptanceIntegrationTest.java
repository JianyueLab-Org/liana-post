package com.liana.post.postage.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.postage.AcceptanceOutcome;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.model.Result;
import com.liana.post.postage.client.OmsAcceptanceClient;
import com.liana.post.postage.retail.RetailModels;
import com.liana.post.postage.retail.RetailService;
import com.liana.post.postage.tariff.QuoteModels;
import com.liana.post.postage.tariff.TariffService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.liana.post.postage.acceptance.AcceptanceModels.*;

@ActiveProfiles("standalone")
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:acceptance;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class AcceptanceIntegrationTest {
    @Autowired AcceptanceStore store;
    @Autowired AcceptanceCoordinator coordinator;
    @Autowired RetailService retail;
    @Autowired TariffService tariffs;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockBean OmsAcceptanceClient oms;
    JwtUserContext clerk;
    JsonNode mail;
    QuoteModels.Snapshot quote;
    @BeforeEach void reset() throws Exception {
        jdbc.update("DELETE FROM postage_acceptance_stamp");jdbc.update("DELETE FROM postage_acceptance");
        jdbc.update("DELETE FROM postage_retail_audit");jdbc.update("DELETE FROM postage_digital_stamp");
        jdbc.update("DELETE FROM postage_receipt");jdbc.update("DELETE FROM postage_sale");jdbc.update("DELETE FROM postage_quote");
        jdbc.update("UPDATE postage_tariff_version SET effective_from=0 WHERE status='PUBLISHED'");
        clerk=new JwtUserContext();clerk.setUsername("clerk");clerk.setRole("CLERK");clerk.setFacilityCode("B1");
        mail=json.readTree("{\"mailTypeCode\":\"U\",\"mailScope\":\"DOMESTIC\",\"serviceType\":\"AIR\",\"weightGrams\":20,\"additionalServices\":[]}");
        quote=tariffs.quote(new QuoteModels.Request("U","DOMESTIC","AIR","LN",20),"clerk");
        when(oms.validate(any(),anyString())).thenAnswer(i->Result.ok(i.getArgument(0)));
        when(oms.complete(anyString(),anyString())).thenReturn(Result.ok(new AcceptanceOutcome("CREATED","UU123456789LN")));
        when(oms.abort(anyString(),anyString())).thenReturn(Result.ok(new AcceptanceOutcome("CANCELLED",null)));
    }
    String code(String value) {
        var sale=retail.create(new RetailModels.Create(UUID.randomUUID().toString(),List.of(new RetailModels.Line(new BigDecimal(value),1))),clerk);
        return retail.confirm(sale.sale().saleId(),new RetailModels.Confirm(UUID.randomUUID().toString(),new BigDecimal(value)),clerk).stamps().get(0).code();
    }
    Request request(List<String> codes,List<Traditional> traditional) { return store.normalize(new Request(UUID.randomUUID().toString(),quote.quoteId(),mail,codes,traditional,true)); }
    @Test void strictClientReadsExistingResultEnvelopeWithoutRelaxingRequestValidation() throws Exception {
        var wire=json.writeValueAsString(Result.ok(mail));
        assertTrue(json.readTree(wire).path("success").asBoolean());
        Result<JsonNode> decoded=json.readValue(wire,json.getTypeFactory().constructParametricType(Result.class,JsonNode.class));
        assertEquals(200,decoded.getCode());assertEquals(mail,decoded.getData());
        assertThrows(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class,()->
                json.readValue("{\"requestId\":\"id\",\"quoteId\":\"q\",\"mail\":{},\"stampCodes\":[],\"traditionalStamps\":[],\"unknown\":true}",Request.class));
    }
    @Test void combinesTraditionalAndDigitalAndRecordsExcessWithoutBalance() {
        var code=code("1.00");var request=request(List.of(code),List.of(new Traditional(new BigDecimal("0.80"),1)));
        var v=coordinator.accept(request,clerk,"Bearer test");
        assertEquals("COMPLETED",v.status());assertEquals(new BigDecimal("1.80"),v.appliedAmount());assertEquals(new BigDecimal("0.30"),v.excessAmount());
        assertEquals("REDEEMED",retail.lookup(code).status());assertEquals(v,coordinator.accept(request,clerk,"Bearer test"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM postage_retail_audit WHERE event_type='STAMP_REDEEMED'",Integer.class));
        assertThrows(ResponseStatusException.class,()->retail.voidStamp(v.stamps().get(0).stampId(),new RetailModels.VoidRequest("void","reason"),admin()));
    }
    @Test void legacyRegisteredQuoteCanSettleWithCanonicalRegisteredMail() {
        var legacy=tariffs.quote(new QuoteModels.Request("R","DOMESTIC","AIR","LN",20),"clerk");
        var registered=mail.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)registered).put("mailTypeCode","R").putArray("additionalServices").add("REGISTRATION");
        var r=store.normalize(new Request("legacy-R",legacy.quoteId(),registered,List.of(),
                List.of(new Traditional(new BigDecimal("4.50"),1)),true));
        var v=store.prepare(r,registered,clerk);
        assertEquals(new BigDecimal("0.00"),v.excessAmount());
        assertEquals(new BigDecimal("4.50"),v.quote().calculation().totalAmount());
        assertTrue(v.quote().input().additionalServices().isEmpty());
    }
    @Test void shortageAndInvalidTraditionalNeverReserveOrCreate() {
        var code=code("1.00");assertThrows(ResponseStatusException.class,()->store.prepare(request(List.of(code),List.of()),mail,clerk));
        assertEquals("ISSUED",retail.lookup(code).status());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM postage_acceptance",Integer.class));
        assertThrows(IllegalArgumentException.class,()->store.normalize(new Request("bad",quote.quoteId(),mail,List.of(),List.of(new Traditional(new BigDecimal("1.2"),1)),false)));
        assertThrows(IllegalArgumentException.class,()->request(List.of(code,code),List.of()));
    }
    @Test void concurrentRequestsCannotReserveOneVoucherForTwoMailItems() throws Exception {
        String code=code("2.00");var requests=new ArrayList<Request>();
        for(int n=0;n<6;n++){var q=tariffs.quote(quote.input(),"clerk");requests.add(store.normalize(new Request(UUID.randomUUID().toString(),q.quoteId(),mail,List.of(code),List.of(),false)));}
        var pool=Executors.newFixedThreadPool(6);
        try {
            var jobs=requests.stream().map(r->pool.submit(()->{try{return store.prepare(r,mail,clerk).acceptanceId();}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return null;}})).toList();
            int success=0;for(var job:jobs)if(job.get()!=null)success++;assertEquals(1,success);
        } finally {pool.shutdownNow();}
        assertEquals("RESERVED",retail.lookup(code).status());
    }
    @Test void concurrentSameRequestReturnsOnePreparedRecord() throws Exception {
        var r=request(List.of(code("2.00")),List.of());var pool=Executors.newFixedThreadPool(6);
        try {var jobs=new ArrayList<Future<String>>();for(int n=0;n<6;n++)jobs.add(pool.submit(()->store.prepare(r,mail,clerk).acceptanceId()));var ids=new HashSet<String>();for(var job:jobs)ids.add(job.get());assertEquals(1,ids.size());}finally{pool.shutdownNow();}
    }
    @Test void responseLossKeepsReservationAndResumeUsesOriginalWaybill() {
        var r=request(List.of(code("2.00")),List.of());
        when(oms.complete(anyString(),anyString())).thenReturn(Result.error("timeout",503));
        assertThrows(ResponseStatusException.class,()->coordinator.accept(r,clerk,"Bearer test"));
        var v=store.previous(r,clerk);assertEquals("PREPARED",v.status());assertEquals("RESERVED",v.stamps().get(0).status());
        when(oms.complete(anyString(),anyString())).thenReturn(Result.ok(new AcceptanceOutcome("CREATED","UU123456789LN")));
        assertEquals("COMPLETED",coordinator.resume(v.acceptanceId(),clerk,"Bearer test").status());
    }
    @Test void cancelOnlyReleasesAfterOmsAcknowledgesAndCreatedMailWins() {
        var r=request(List.of(code("2.00")),List.of());var v=store.prepare(r,mail,clerk);
        when(oms.abort(anyString(),anyString())).thenReturn(Result.error("unavailable",503));
        assertThrows(ResponseStatusException.class,()->coordinator.cancel(v.acceptanceId(),clerk,"Bearer test"));assertEquals("RESERVED",retail.lookup(r.stampCodes().get(0)).status());
        when(oms.abort(anyString(),anyString())).thenReturn(Result.ok(new AcceptanceOutcome("CREATED","UU123456789LN")));
        assertEquals("COMPLETED",coordinator.cancel(v.acceptanceId(),clerk,"Bearer test").status());assertEquals("REDEEMED",retail.lookup(r.stampCodes().get(0)).status());
    }
    @Test void cancelledReservationReturnsWholeVoucherAndCannotFinish() {
        var r=request(List.of(code("2.00")),List.of());var v=store.prepare(r,mail,clerk);
        assertEquals("CANCELLED",coordinator.cancel(v.acceptanceId(),clerk,"Bearer test").status());assertEquals("ISSUED",retail.lookup(r.stampCodes().get(0)).status());
        assertThrows(ResponseStatusException.class,()->store.finish(v.acceptanceId(),"UU123456789LN",clerk));
    }
    @Test void auditFailureRollsBackAllRedemptionsAndCompletion() {
        var r=request(List.of(code("2.00")),List.of());var v=store.prepare(r,mail,clerk);
        jdbc.execute("ALTER TABLE postage_retail_audit ADD CONSTRAINT fail_completion CHECK(event_type <> 'ACCEPTANCE_COMPLETED')");
        try {assertThrows(org.springframework.dao.DataAccessException.class,()->store.finish(v.acceptanceId(),"UU123456789LN",clerk));assertEquals("PREPARED",store.get(v.acceptanceId(),clerk).status());assertEquals("RESERVED",retail.lookup(r.stampCodes().get(0)).status());}
        finally{jdbc.execute("ALTER TABLE postage_retail_audit DROP CONSTRAINT fail_completion");}
        assertEquals("COMPLETED",store.finish(v.acceptanceId(),"UU123456789LN",clerk).status());
    }
    @Test void changedRequestQuoteMismatchExpiredVoucherAndOwnershipAreRejected() throws Exception {
        var r=request(List.of(code("2.00")),List.of());var v=store.prepare(r,mail,clerk);
        assertThrows(ResponseStatusException.class,()->store.previous(new Request(r.requestId(),r.quoteId(),r.mail(),r.stampCodes(),List.of(new Traditional(new BigDecimal("1.00"),1)),true),clerk));
        var other=new JwtUserContext();other.setUsername("other");other.setRole("CLERK");assertThrows(ResponseStatusException.class,()->store.get(v.acceptanceId(),other));
        var q=tariffs.quote(quote.input(),"clerk");var wrong=mail.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)wrong).put("weightGrams",30);
        assertThrows(ResponseStatusException.class,()->store.prepare(new Request("mismatch",q.quoteId(),wrong,List.of(),List.of(new Traditional(new BigDecimal("9.00"),1)),true),wrong,clerk));
        var code=code("2.00");jdbc.update("UPDATE postage_digital_stamp SET expires_at=0 WHERE code=?",code);
        assertThrows(ResponseStatusException.class,()->store.prepare(new Request("expired",q.quoteId(),mail,List.of(code),List.of(),false),mail,clerk));
        var snapshot=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(jdbc.queryForObject("SELECT snapshot_json FROM postage_quote WHERE quote_id=?",String.class,q.quoteId()));
        snapshot.put("expiresAt","2000-01-01T00:00:00Z");jdbc.update("UPDATE postage_quote SET snapshot_json=? WHERE quote_id=?",json.writeValueAsString(snapshot),q.quoteId());
        assertThrows(ResponseStatusException.class,()->store.prepare(new Request("expired-quote",q.quoteId(),mail,List.of(),List.of(new Traditional(new BigDecimal("9.00"),1)),true),mail,clerk));
    }
    private JwtUserContext admin(){var a=new JwtUserContext();a.setUsername("admin");a.setRole("ADMIN");return a;}
}
