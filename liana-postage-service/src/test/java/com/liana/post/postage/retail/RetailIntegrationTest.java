package com.liana.post.postage.retail;

import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.liana.post.postage.retail.RetailModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("standalone")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:retail-integration;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class RetailIntegrationTest {
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock retailTestClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-15T01:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Autowired RetailService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired TestRestTemplate http;
    @MockBean StampCodeGenerator codes;
    final JwtUserContext clerk = actor("clerk", "CLERK");
    final JwtUserContext admin = actor("admin", "ADMIN");

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM postage_retail_audit");
        jdbc.update("DELETE FROM postage_digital_stamp");
        jdbc.update("DELETE FROM postage_receipt");
        jdbc.update("DELETE FROM postage_sale");
        jdbc.update("UPDATE postage_stamp_policy SET revision=1,min_face_value=0.01,max_face_value=100,validity_days=365,updated_by='SYSTEM',updated_at=0 WHERE id=1");
        clock.now = Instant.parse("2026-10-15T01:00:00Z");
        var realCodes = new StampCodeGenerator();
        when(codes.generate()).thenAnswer(i -> realCodes.generate());
    }

    @Test
    void cashConfirmationIssuesExactValuesAndAnAuditableReceipt() {
        var draft = service.create(new Create("purchase", List.of(new Line(new BigDecimal("1.20"), 1), new Line(new BigDecimal("0.80"), 2))), clerk);
        assertEquals(new BigDecimal("2.80"), draft.sale().totalAmount());
        assertEquals("PENDING_PAYMENT", draft.sale().status());
        assertNull(draft.receipt());
        assertTrue(draft.stamps().isEmpty());
        var paid = service.confirm(draft.sale().saleId(), new Confirm("cash", new BigDecimal("2.80")), clerk);
        assertEquals("PAID", paid.sale().status());
        assertEquals(3, paid.stamps().size());
        assertEquals("CASH", paid.receipt().method());
        assertEquals("B1", paid.receipt().facilityCode());
        assertEquals(paid.sale().totalAmount(), paid.stamps().stream().map(Stamp::faceValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(3, paid.stamps().stream().map(Stamp::code).distinct().count());
        assertEquals(5, paid.audit().size());
        for (var stamp : paid.stamps()) {
            assertEquals(clock.now.plus(365, ChronoUnit.DAYS), stamp.expiresAt());
            assertEquals(stamp, service.lookup(stamp.code().toLowerCase().replace("-", " ")));
        }
    }

    @Test
    void createAndCashRetriesReturnOriginalResultWithoutAnotherReceiptOrIssuance() {
        var request = create("same", "5.00", 2);
        var draft = service.create(request, clerk);
        assertEquals(draft, service.create(create("same", "5", 2), clerk));
        var confirm = new Confirm("cash", new BigDecimal("10.00"));
        var first = service.confirm(draft.sale().saleId(), confirm, clerk);
        assertEquals(first, service.confirm(draft.sale().saleId(), confirm, clerk));
        assertEquals(first, service.create(request, clerk));
        assertEquals(1, count("postage_receipt"));
        assertEquals(2, count("postage_digital_stamp"));
        assertEquals(4, count("postage_retail_audit"));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create(create("same", "6.00", 2), clerk)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.confirm(draft.sale().saleId(), new Confirm("another", new BigDecimal("10.00")), clerk)).getStatusCode().value());
    }

    @Test
    void invalidAmountLimitsQuantitiesAndServiceFieldsCannotIssueStamps() {
        for (var request : List.of(create("low", "0.001", 1), create("high", "100.01", 1), create("zero", "0", 1), create("count", "1", 101),
                new Create("total-count", List.of(new Line(BigDecimal.ONE, 60), new Line(BigDecimal.ONE, 50))))) {
            assertThrows(IllegalArgumentException.class, () -> service.create(request, clerk));
        }
        assertEquals(0, count("postage_sale"));
        var draft = service.create(create("valid", "1", 1), clerk);
        assertThrows(IllegalArgumentException.class, () -> service.confirm(draft.sale().saleId(), new Confirm("wrong", new BigDecimal("0.99")), clerk));
        assertEquals("PENDING_PAYMENT", service.detail(draft.sale().saleId(), clerk).sale().status());
        assertEquals(0, count("postage_receipt"));
        assertEquals(0, count("postage_digital_stamp"));
    }

    @Test
    void receiptRequestCannotBeReusedForAnotherSale() {
        var first = service.create(create("first", "1", 1), clerk);
        var second = service.create(create("second", "1", 1), clerk);
        service.confirm(first.sale().saleId(), new Confirm("receipt-id", BigDecimal.ONE), clerk);
        assertThrows(ResponseStatusException.class, () -> service.confirm(second.sale().saleId(), new Confirm("receipt-id", BigDecimal.ONE), clerk));
        assertEquals("PENDING_PAYMENT", service.detail(second.sale().saleId(), clerk).sale().status());
        assertEquals(1, count("postage_receipt"));
    }

    @Test
    void concurrentCreateRetriesProduceOneSale() throws Exception {
        var request = create("concurrent-create", "1", 1);
        var results = parallel(6, () -> service.create(request, clerk).sale().saleId());
        assertEquals(1, results.stream().distinct().count());
        assertEquals(1, count("postage_sale"));
        assertEquals(1, count("postage_retail_audit"));
    }

    @Test
    void concurrentCashConfirmationsIssueOnce() throws Exception {
        var draft = service.create(create("concurrent-cash", "2.00", 5), clerk);
        var results = parallel(6, () -> service.confirm(draft.sale().saleId(), new Confirm("same-cash", new BigDecimal("10")), clerk));
        assertEquals(1, results.stream().map(d -> d.receipt().receiptId()).distinct().count());
        assertEquals(5, count("postage_digital_stamp"));
        assertEquals(1, count("postage_receipt"));
        assertEquals(7, count("postage_retail_audit"));
    }

    @Test
    void duplicateCodeRollsBackPaymentEveryStampAndAudit() {
        var draft = service.create(create("collision", "1", 2), clerk);
        when(codes.generate()).thenReturn("LP-00000000-00000000-00000000-00000000");
        assertThrows(DataAccessException.class, () -> service.confirm(draft.sale().saleId(), new Confirm("cash-collision", new BigDecimal("2")), clerk));
        assertEquals("PENDING_PAYMENT", service.detail(draft.sale().saleId(), clerk).sale().status());
        assertEquals(0, count("postage_receipt"));
        assertEquals(0, count("postage_digital_stamp"));
        assertEquals(1, count("postage_retail_audit"));
    }

    @Test
    void auditFailureAlsoRollsBackReceiptAndIssuanceAndCanBeRetried() {
        var draft = service.create(create("audit-failure", "3", 1), clerk);
        jdbc.execute("ALTER TABLE postage_retail_audit ADD CONSTRAINT reject_stamp_audit CHECK (event_type <> 'STAMP_ISSUED')");
        try {
            assertThrows(DataAccessException.class, () -> service.confirm(draft.sale().saleId(), new Confirm("cash-retry", new BigDecimal("3")), clerk));
            assertEquals(0, count("postage_receipt"));
            assertEquals(0, count("postage_digital_stamp"));
            assertEquals(1, count("postage_retail_audit"));
        } finally { jdbc.execute("ALTER TABLE postage_retail_audit DROP CONSTRAINT reject_stamp_audit"); }
        assertEquals("PAID", service.confirm(draft.sale().saleId(), new Confirm("cash-retry", new BigDecimal("3")), clerk).sale().status());
    }

    @Test
    void policyRevisionChecksAndSnapshotProtectPendingAndIssuedVouchers() {
        var draft = service.create(create("old-policy", "100", 1), clerk);
        var updated = service.updatePolicy(new UpdatePolicy(1, new BigDecimal("1"), new BigDecimal("50"), 30), admin);
        assertEquals(2, updated.revision());
        assertEquals(1, service.policyAudit(admin).size());
        assertThrows(ResponseStatusException.class, () -> service.updatePolicy(new UpdatePolicy(1, BigDecimal.ONE, BigDecimal.TEN, 10), admin));
        assertThrows(IllegalArgumentException.class, () -> service.create(create("new-high", "100", 1), clerk));
        var paid = service.confirm(draft.sale().saleId(), new Confirm("old-cash", new BigDecimal("100")), clerk);
        assertEquals(clock.now.plus(365, ChronoUnit.DAYS), paid.stamps().get(0).expiresAt());
        var fresh = service.create(create("fresh", "5", 1), clerk);
        assertEquals(30, fresh.sale().validityDays());
        assertEquals(2, fresh.sale().policyRevision());
    }

    @Test
    void expiryIsEffectiveAtExactBoundaryAndVoidPreservesPaidReceipt() {
        var paid = paid("expiry", "5", 1);
        var stamp = paid.stamps().get(0);
        clock.now = stamp.expiresAt().minusMillis(1);
        assertEquals("ISSUED", service.lookup(stamp.code()).displayStatus());
        clock.now = stamp.expiresAt();
        assertEquals("EXPIRED", service.lookup(stamp.code()).displayStatus());
        var request = new VoidRequest("void", "客户凭证遗失，停止使用");
        var voided = service.voidStamp(stamp.stampId(), request, admin);
        assertEquals("VOIDED", voided.displayStatus());
        assertEquals(voided, service.voidStamp(stamp.stampId(), request, admin));
        var reread = service.detail(paid.sale().saleId(), clerk);
        assertEquals(paid.receipt(), reread.receipt());
        assertEquals("PAID", reread.sale().status());
        assertEquals(4, reread.audit().size());
    }

    @Test
    void redeemedAndUnauthorizedOrBlankVoidRequestsAreRejected() {
        var paid = paid("void-guards", "1", 1);
        var stamp = paid.stamps().get(0);
        assertThrows(ResponseStatusException.class, () -> service.voidStamp(stamp.stampId(), new VoidRequest("void", "reason"), clerk));
        assertThrows(IllegalArgumentException.class, () -> service.voidStamp(stamp.stampId(), new VoidRequest("void", "  "), admin));
        jdbc.update("UPDATE postage_digital_stamp SET status='REDEEMED' WHERE stamp_id=?", stamp.stampId());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.voidStamp(stamp.stampId(), new VoidRequest("void", "reason"), admin)).getStatusCode().value());
        assertEquals(3, count("postage_retail_audit"));
    }

    @Test
    void cancellationIsIdempotentButPaidSalesCannotBeCancelled() {
        var draft = service.create(create("cancel", "1", 1), clerk);
        var cancelled = service.cancel(draft.sale().saleId(), clerk);
        assertEquals(cancelled, service.cancel(draft.sale().saleId(), clerk));
        assertThrows(ResponseStatusException.class, () -> service.confirm(draft.sale().saleId(), new Confirm("cash", BigDecimal.ONE), clerk));
        assertThrows(ResponseStatusException.class, () -> service.cancel(paid("paid-cancel", "1", 1).sale().saleId(), clerk));
    }

    @Test
    void ownersCanRecoverSalesButOtherWorkersCannotListOrReadThem() {
        var draft = service.create(create("private", "1", 1), clerk);
        var other = actor("another-clerk", "CLERK");
        assertTrue(service.sales(other, 0, 20).isEmpty());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.detail(draft.sale().saleId(), other)).getStatusCode().value());
        assertEquals(1, service.sales(admin, 0, 20).size());
        assertThrows(IllegalArgumentException.class, () -> service.sales(clerk, -1, 20));
    }

    @Test
    void httpRequiresSignedIdentityAndRestrictsOperationsAndInputs() {
        assertEquals(401, http.getForEntity("/api/postage/stamp-policy", String.class).getStatusCode().value());
        assertEquals(403, call("/sales", HttpMethod.POST, create("blocked", "1", 1), "SORTER", List.of("MAIL_CREATE")).getStatusCode().value());
        assertEquals(403, call("/sales", HttpMethod.POST, create("blocked", "1", 1), "CLERK", List.of()).getStatusCode().value());
        assertEquals(403, call("/stamp-policy", HttpMethod.PUT, new UpdatePolicy(1, BigDecimal.ONE, BigDecimal.TEN, 10), "CLERK", List.of("MAIL_CREATE")).getStatusCode().value());
        for (String body : List.of(
                "{\"requestId\":\"invalid\",\"lines\":[{\"faceValue\":1,\"quantity\":1.1}]}",
                "{\"requestId\":\"invalid\",\"lines\":[{\"faceValue\":1.001,\"quantity\":1}]}",
                "{\"requestId\":\"invalid\",\"lines\":[{\"faceValue\":1,\"quantity\":1}],\"totalAmount\":0}",
                "{\"requestId\":\"one\",\"requestId\":\"two\",\"lines\":[{\"faceValue\":1,\"quantity\":1}]}")) {
            assertEquals(400, call("/sales", HttpMethod.POST, body, "CLERK", List.of("MAIL_CREATE")).getStatusCode().value());
        }
        assertEquals(0, count("postage_sale"));
        assertEquals(200, call("/sales", HttpMethod.POST, create("http", "1.20", 2), "CLERK", List.of("MAIL_CREATE")).getStatusCode().value());
        assertEquals(200, call("/sales", HttpMethod.GET, null, "CLERK", List.of("MAIL_CREATE")).getStatusCode().value());
        assertEquals(200, call("/sales?page=0&size=1", HttpMethod.GET, null, "CLERK", List.of("MAIL_CREATE")).getStatusCode().value());
        assertEquals(400, call("/sales?page=-1&size=1", HttpMethod.GET, null, "CLERK", List.of("MAIL_CREATE")).getStatusCode().value());
    }

    private Detail paid(String request, String amount, int quantity) {
        var draft = service.create(create(request, amount, quantity), clerk);
        return service.confirm(draft.sale().saleId(), new Confirm("cash-" + request, draft.sale().totalAmount()), clerk);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private static Create create(String request, String amount, int quantity) { return new Create(request, List.of(new Line(new BigDecimal(amount), quantity))); }
    private static JwtUserContext actor(String username, String role) {
        var actor = new JwtUserContext(); actor.setUsername(username); actor.setRole(role); actor.setFacilityCode("B1"); actor.setPermissions(List.of("MAIL_CREATE")); return actor;
    }
    private <T> List<T> parallel(int count, Callable<T> task) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<Future<T>>();
            for (int n=0; n<count; n++) futures.add(executor.submit(() -> { start.await(); return task.call(); }));
            start.countDown();
            var results = new java.util.ArrayList<T>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
    }
    private ResponseEntity<String> call(String path, HttpMethod method, Object body, String role, List<String> permissions) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTokenUtil.generateToken("http-user", role, "B1", Map.of("permissions", permissions)));
        return http.exchange("/api/postage" + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
