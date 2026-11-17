package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.sorting.client.DispatchLifecycleClient;
import com.liana.post.sorting.client.TransportReceiptClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Each destination retries independently; an expired worker cannot overwrite a newer attempt. */
@Service
public class SortingReceiptPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransportReceiptClient transport;
    private final DispatchLifecycleClient dispatch;
    private final String token;
    public record Delivery(String eventNo, String destination, String payload, int attempts) { }

    public SortingReceiptPublisher(JdbcTemplate jdbc, ObjectMapper json, TransportReceiptClient transport,
            DispatchLifecycleClient dispatch, @Value("${manifest.internal-token:}") String token) {
        this.jdbc = jdbc; this.json = json; this.transport = transport; this.dispatch = dispatch; this.token = token;
    }

    @Scheduled(fixedDelayString = "${manifest.receipt-publish-delay-ms:5000}")
    public void publish() { publishDue(); }

    public int publishDue() {
        int count = 0;
        for (var d : due()) {
            if (!claim(d)) continue;
            try {
                var event = json.readValue(d.payload(), ManifestReceipt.Event.class);
                var result = "TRANSPORT".equals(d.destination()) ? transport.receive(event, token) : dispatch.receive(event, token);
                if (result == null || !result.isSuccess()) {
                    fail(d, result == null ? "empty receipt response" : result.getMessage(), result != null && conflict(result.getCode())); continue;
                }
                var ack = result.getData();
                if (ack == null || !event.equals(ack.event()) || ack.confirmedAt() == null || ack.confirmedAt().isAfter(Instant.now().plusSeconds(60))) {
                    fail(d, "receipt does not match fixed bag event", true); continue;
                }
                count += confirm(d, ack);
            } catch (feign.FeignException e) { fail(d, e.getMessage(), conflict(e.status())); }
            catch (Exception e) { fail(d, e.getMessage(), false); }
        }
        return count;
    }

    public List<Delivery> due() {
        return jdbc.query("SELECT event_no,destination,payload_json,attempt_count FROM sorting_receipt_outbox WHERE status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=? ORDER BY created_at,event_no,destination LIMIT 20",
                (rs, n) -> new Delivery(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)), now());
    }
    public boolean claim(Delivery d) {
        return jdbc.update("UPDATE sorting_receipt_outbox SET status='SENDING',attempt_count=attempt_count+1,next_attempt_at=? WHERE event_no=? AND destination=? AND attempt_count=? AND status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=?",
                now().plusSeconds(60), d.eventNo(), d.destination(), d.attempts(), now()) == 1;
    }
    public int confirm(Delivery d, ManifestReceipt.Ack ack) throws Exception {
        if (!json.readValue(d.payload(), ManifestReceipt.Event.class).equals(ack.event()) || ack.confirmedAt() == null) throw new IllegalArgumentException("receipt mismatch");
        return jdbc.update("UPDATE sorting_receipt_outbox SET status='SENT',receipt_json=?,confirmed_at=?,last_error=NULL WHERE event_no=? AND destination=? AND status='SENDING' AND attempt_count=?",
                json.writeValueAsString(ack), LocalDateTime.ofInstant(ack.confirmedAt(), ZoneOffset.UTC), d.eventNo(), d.destination(), d.attempts() + 1);
    }
    public void fail(Delivery d, String error, boolean conflict) {
        String message = error == null ? "receipt delivery failed" : error.substring(0, Math.min(512, error.length()));
        jdbc.update("UPDATE sorting_receipt_outbox SET status=?,next_attempt_at=?,last_error=? WHERE event_no=? AND destination=? AND status='SENDING' AND attempt_count=?",
                conflict ? "CONFLICT" : "RETRY", now().plusSeconds(Math.min(3600, 5L << Math.min(9, d.attempts()))), message, d.eventNo(), d.destination(), d.attempts() + 1);
    }
    private boolean conflict(Integer code) { return code != null && (code == 403 || code == 409); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
}
