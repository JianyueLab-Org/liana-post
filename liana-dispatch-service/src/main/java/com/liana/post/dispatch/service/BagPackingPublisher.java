package com.liana.post.dispatch.service;

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
public class BagPackingPublisher {
    private static final Logger log = LoggerFactory.getLogger(BagPackingPublisher.class);
    private final BagPackingService packing;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String internalToken;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public BagPackingPublisher(BagPackingService packing, ObjectMapper json,
            @Value("${oms.service.url:http://localhost:8083}") String omsUrl,
            @Value("${packing.internal-token:}") String internalToken) {
        this.packing = packing; this.json = json;
        this.internalToken = internalToken;
        this.endpoint = URI.create(omsUrl.replaceAll("/+$", "") + "/api/oms/internal/packings");
    }

    @Scheduled(fixedDelayString = "${packing.outbox.delay-ms:5000}")
    public void publish() {
        for (var delivery : packing.due()) {
            if (!packing.claim(delivery)) continue;
            try {
                if (internalToken.isBlank()) throw new IllegalStateException("packing.internal-token is not configured");
                var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("X-Packing-Token", internalToken)
                        .POST(HttpRequest.BodyPublishers.ofString(delivery.payload())).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    packing.failed(delivery, "OMS HTTP " + response.statusCode(), response.statusCode() == 409);
                    continue;
                }
                var result = json.readTree(response.body());
                if (result != null && result.path("code").asInt() == 409) {
                    packing.failed(delivery, result.path("message").asText("packing conflict"), true);
                } else if (result != null && result.path("code").asInt() == 200
                        && result.path("data").isBoolean() && result.path("data").booleanValue()) {
                    packing.confirmed(delivery);
                } else packing.failed(delivery, "OMS did not acknowledge complete packing", false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                packing.failed(delivery, "delivery interrupted", false);
                return;
            } catch (Exception e) {
                packing.failed(delivery, e.getMessage(), false);
                log.warn("Packing confirmation retained for retry: {}", delivery.bagNo());
            }
        }
    }
}
