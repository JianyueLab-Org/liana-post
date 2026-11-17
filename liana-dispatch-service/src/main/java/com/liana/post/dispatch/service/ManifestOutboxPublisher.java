package com.liana.post.dispatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ManifestOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(ManifestOutboxPublisher.class);
    private final ManifestOutbox outbox;
    private final ObjectMapper json;
    private final URI endpoint;
    private final URI sealEndpoint;
    private final URI cancelEndpoint;
    private final String internalToken;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ManifestOutboxPublisher(ManifestOutbox outbox, ObjectMapper json,
            @Value("${sorting.service.url:http://localhost:8087}") String sortingServiceUrl,
            @Value("${manifest.internal-token:}") String internalToken) {
        this.outbox = outbox;
        this.json = json;
        this.endpoint = URI.create(sortingServiceUrl.replaceAll("/+$", "") + "/api/v1/sorting/manifest/sync");
        this.sealEndpoint = URI.create(sortingServiceUrl.replaceAll("/+$", "") + "/api/v1/sorting/internal/manifests/seal");
        this.cancelEndpoint = URI.create(sortingServiceUrl.replaceAll("/+$", "") + "/api/v1/sorting/internal/manifests/cancel");
        this.internalToken = internalToken;
    }

    @Scheduled(fixedDelayString = "${manifest.outbox.delay-ms:5000}")
    public void publish() {
        for (ManifestOutbox.Delivery delivery : outbox.due()) {
            if (!outbox.claim(delivery)) continue;
            try {
                boolean seal = "SEAL".equals(delivery.eventType());
                boolean cancel = "CANCEL".equals(delivery.eventType());
                boolean lifecycle = seal || cancel;
                if (!lifecycle && !"PREALERT".equals(delivery.eventType())) throw new IllegalStateException("unknown manifest event type");
                if (lifecycle && internalToken.isBlank()) throw new IllegalStateException("manifest service credential is not configured");
                var builder = HttpRequest.newBuilder(cancel ? cancelEndpoint : seal ? sealEndpoint : endpoint).timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json; charset=utf-8");
                if (lifecycle) builder.header("X-Manifest-Token", internalToken);
                HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(delivery.payload())).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode body = json.readTree(response.body());
                if (lifecycle && (response.statusCode() == 409 || response.statusCode() == 403
                        || (body != null && (body.path("code").asInt() == 409 || body.path("code").asInt() == 403)))) {
                    outbox.conflict(delivery, body == null ? "sorting lifecycle conflict" : body.path("message").asText("sorting lifecycle conflict"));
                    continue;
                }
                if (response.statusCode() != 200) throw new IllegalStateException("sorting HTTP " + response.statusCode());
                if (body == null || body.path("code").asInt() != 200) throw new IllegalStateException("sorting did not acknowledge the command");
                if (lifecycle) {
                    JsonNode sent = json.readTree(delivery.payload());
                    JsonNode receipt = body.path("data");
                    if (!sent.path("requestNo").equals(receipt.path("requestNo"))
                            || !sent.path("manifestNo").equals(receipt.path("manifestNo"))
                            || !sent.path("versionNo").equals(receipt.path("versionNo"))
                            || !sent.path("sourceOrgCode").equals(receipt.path("sourceOrgCode"))
                            || !sent.path("actorId").equals(receipt.path("actorId"))
                            || !receipt.path("contentHash").asText().matches("[0-9a-f]{64}")
                            || !receipt.path(cancel ? "cancelledAt" : "sealedAt").isTextual()
                            || (cancel && !sent.path("reason").equals(receipt.path("reason")))) {
                        throw new IllegalStateException("sorting lifecycle receipt does not match command");
                    }
                    java.time.Instant.parse(receipt.path(cancel ? "cancelledAt" : "sealedAt").asText());
                    outbox.sent(delivery, json.writeValueAsString(receipt));
                } else {
                    if (!body.path("data").isBoolean() || !body.path("data").booleanValue()) {
                        throw new IllegalStateException("sorting did not acknowledge the prealert");
                    }
                    outbox.sent(delivery);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                outbox.failed(delivery, "delivery interrupted");
                return;
            } catch (Exception e) {
                outbox.failed(delivery, e.getMessage());
                log.warn("Manifest {} delivery retained for retry: {}", delivery.eventType(), delivery.eventNo());
            }
        }
    }
}
