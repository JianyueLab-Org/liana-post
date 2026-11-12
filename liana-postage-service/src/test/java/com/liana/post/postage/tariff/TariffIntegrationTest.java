package com.liana.post.postage.tariff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("standalone")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:tariff-integration;MODE=MySQL;DB_CLOSE_DELAY=-1")
class TariffIntegrationTest {
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-15T01:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Autowired TariffService service;
    @Autowired TariffStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired TestRestTemplate http;
    TariffDefinition definition;

    @BeforeEach
    void reset() throws Exception {
        clock.now = Instant.parse("2026-10-15T01:00:00Z");
        jdbc.update("DELETE FROM postage_quote");
        jdbc.update("DELETE FROM postage_tariff_version");
        try (var input = getClass().getResourceAsStream("/initial-tariff.json")) {
            definition = json.readValue(input, TariffDefinition.class);
        }
        seed();
    }

    @Test
    void seedIsIdempotentAndNotEffectiveEarly() {
        seed();
        assertEquals(1, service.versions().size());
        clock.now = Instant.parse("2026-10-14T15:59:59Z");
        assertThrows(ResponseStatusException.class, service::current);
        clock.now = Instant.parse("2026-10-14T16:00:00Z");
        assertEquals("LIANA-TARIFF-2026-001", service.current().versionId());
    }

    @Test
    void persistedQuoteContainsAuditableInputsAndBreakdown() {
        var snapshot = service.quote(new QuoteModels.Request("R", "INTERNATIONAL", "AIR", "JP", 31), "clerk001");
        // New repository instance reads persisted data, rather than any service-local cache.
        var reread = new TariffStore(jdbc, json).quote(snapshot.quoteId());
        assertEquals(snapshot, reread);
        assertEquals("23.00", reread.calculation().totalAmount().toPlainString());
        assertEquals(2, reread.calculation().additionalUnits());
        assertEquals("REGISTRATION", reread.calculation().fees().get(0).code());
        assertTrue(service.quote(snapshot.quoteId()).valid());
        clock.now = snapshot.expiresAt();
        assertFalse(service.quote(snapshot.quoteId()).valid());
    }

    @Test
    void immediatePublicationInvalidatesPriorQuoteButKeepsOriginalSnapshot() {
        var original = service.quote(domesticParcel(), "clerk001");
        var rules = new ArrayList<>(definition.rules());
        var r = rules.get(1);
        rules.set(1, new TariffDefinition.Rule(r.id(), r.product(), r.scope(), r.mode(), r.zone(), r.algorithm(),
                r.maxWeightGrams(), r.firstWeightGrams(), r.stepWeightGrams(), new java.math.BigDecimal("9.00"),
                r.stepPrice(), r.tiers(), r.fees()));
        var changed = new TariffDefinition(definition.currency(), definition.domesticCountry(), definition.fallbackZone(),
                definition.countryZones(), definition.products(), rules);
        var draft = service.create("V2", changed, "admin001");
        service.publish("V2", draft.revision(), null, "admin001");
        assertFalse(service.quote(original.quoteId()).valid());
        assertEquals("11.00", store.quote(original.quoteId()).calculation().totalAmount().toPlainString());
        assertEquals("12.00", service.quote(domesticParcel(), "clerk001").calculation().totalAmount().toPlainString());
        assertEquals(definition, service.version("LIANA-TARIFF-2026-001").definition());
    }

    @Test
    void scheduledVersionCapsExpiryAndTakesEffectAtExactBoundary() {
        var draft = service.create("V2", definition, "admin001");
        var start = clock.now.plusSeconds(60);
        service.publish("V2", draft.revision(), start, "admin001");
        var snapshot = service.quote(domesticParcel(), "clerk001");
        assertEquals(start, snapshot.expiresAt());
        assertEquals("LIANA-TARIFF-2026-001", service.current().versionId());
        clock.now = start;
        assertFalse(service.quote(snapshot.quoteId()).valid());
        assertEquals("V2", service.current().versionId());
    }

    @Test
    void preventsPublishedEditsStaleDraftsAndBackdatedOrOutOfOrderPublication() {
        assertThrows(ResponseStatusException.class, () -> service.update("LIANA-TARIFF-2026-001", 2, definition, "admin001"));
        var v = service.create("V2", definition, "admin001");
        service.update("V2", v.revision(), definition, "admin001");
        assertThrows(ResponseStatusException.class, () -> service.update("V2", v.revision(), definition, "admin001"));
        assertThrows(IllegalArgumentException.class, () -> service.publish("V2", 2, clock.now.minusSeconds(1), "admin001"));
        service.publish("V2", 2, clock.now.plusSeconds(60), "admin001");
        var other = service.create("V3", definition, "admin001");
        assertThrows(ResponseStatusException.class, () -> service.publish("V3", other.revision(), null, "admin001"));
    }

    @Test
    void onlyOneConcurrentPublicationSucceeds() throws Exception {
        var v = service.create("CONCURRENT", definition, "admin001");
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                try { service.publish("CONCURRENT", v.revision(), null, "admin001"); return true; }
                catch (ResponseStatusException e) { assertEquals(409, e.getStatusCode().value()); return false; }
            };
            var futures = executor.invokeAll(List.of(attempt, attempt), 10, TimeUnit.SECONDS);
            int success = 0;
            for (var f : futures) if (f.get()) success++;
            assertEquals(1, success);
            assertEquals(2, service.version("CONCURRENT").revision());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void httpRequiresSignedTokenAndRestrictsAdministration() throws Exception {
        assertEquals(401, http.getForEntity("/api/postage/tariffs/current", String.class).getStatusCode().value());
        var spoofed = new HttpHeaders();
        spoofed.set("X-User-Role", "ADMIN");
        assertEquals(401, http.exchange("/api/postage/tariffs", HttpMethod.GET, new HttpEntity<>(spoofed), String.class).getStatusCode().value());
        assertEquals(403, call("/tariffs", HttpMethod.GET, null, "CLERK", List.of("MAIL_CREATE")).getStatusCode().value());
        assertEquals(403, call("/quotes", HttpMethod.POST, domesticParcel(), "SORTER", List.of("MAIL_CREATE")).getStatusCode().value());
        assertEquals(403, call("/quotes", HttpMethod.POST, domesticParcel(), "CLERK", List.of()).getStatusCode().value());
        var ok = call("/quotes", HttpMethod.POST, domesticParcel(), "CLERK", List.of("MAIL_CREATE"));
        assertEquals(200, ok.getStatusCode().value());
        JsonNode data = json.readTree(ok.getBody()).get("data");
        assertEquals("LIANA-TARIFF-2026-001", data.get("tariffVersion").asText());
        assertEquals(11, data.get("calculation").get("totalAmount").asInt());
    }

    @Test
    void adminCanCreateUpdatePublishAndReadViaHttp() throws Exception {
        var created = call("/tariffs", HttpMethod.POST, new TariffController.CreateDraft("HTTP_V2", definition), "ADMIN", List.of());
        assertEquals(200, created.getStatusCode().value());
        assertEquals(409, call("/tariffs", HttpMethod.POST, new TariffController.CreateDraft("HTTP_V2", definition), "ADMIN", List.of()).getStatusCode().value());
        assertEquals(200, call("/tariffs/HTTP_V2", HttpMethod.PUT, new TariffController.UpdateDraft(1, definition), "ADMIN", List.of()).getStatusCode().value());
        assertEquals(200, call("/tariffs/HTTP_V2/publish", HttpMethod.POST, new TariffController.Publish(2, null), "ADMIN", List.of()).getStatusCode().value());
        var read = call("/tariffs/HTTP_V2", HttpMethod.GET, null, "ADMIN", List.of());
        assertEquals("PUBLISHED", json.readTree(read.getBody()).get("data").get("status").asText());
        assertEquals(409, call("/tariffs/HTTP_V2", HttpMethod.PUT, new TariffController.UpdateDraft(3, definition), "ADMIN", List.of()).getStatusCode().value());
        assertEquals(404, call("/quotes/missing", HttpMethod.GET, null, "ADMIN", List.of()).getStatusCode().value());
    }

    @Test
    void malformedQuoteCannotBeRoundedOrPersisted() {
        for (String body : List.of(
                "{\"product\":\"C\",\"scope\":\"DOMESTIC\",\"mode\":\"AIR\",\"destinationCountry\":\"LN\",\"weightGrams\":1000.9}",
                "{\"product\":\"C\",\"scope\":\"DOMESTIC\",\"mode\":\"AIR\",\"destinationCountry\":\"LN\",\"weightGrams\":0}",
                "{\"product\":\"C\",\"scope\":\"DOMESTIC\",\"mode\":\"AIR\",\"destinationCountry\":\"LN\",\"weightGrams\":1,\"weightGrams\":2}")) {
            assertEquals(400, call("/quotes", HttpMethod.POST, body, "ADMIN", List.of()).getStatusCode().value());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM postage_quote", Integer.class));
    }

    private QuoteModels.Request domesticParcel() { return new QuoteModels.Request("C", "DOMESTIC", "AIR", "LN", 1001); }
    private void seed() { service.seed("LIANA-TARIFF-2026-001", Instant.parse("2026-10-14T16:00:00Z"), definition); }
    private ResponseEntity<String> call(String path, HttpMethod method, Object body, String role, List<String> permissions) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTokenUtil.generateToken("test-user", role, Map.of("permissions", permissions)));
        return http.exchange("/api/postage" + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
