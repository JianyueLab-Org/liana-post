package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.sorting.client.UnpackingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class UnpackingPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UnpackingClient oms;
    private final String token;
    public record Delivery(String requestNo, String payload, int attempts) { }
    public UnpackingPublisher(JdbcTemplate jdbc, ObjectMapper json, UnpackingClient oms, @Value("${packing.internal-token:}") String token) {
        this.jdbc = jdbc; this.json = json; this.oms = oms; this.token = token;
    }
    @Scheduled(fixedDelayString = "${packing.unpack-publish-delay-ms:5000}")
    public void publish() { publishDue(); }
    public int publishDue() {
        int confirmed = 0;
        var due = jdbc.query("SELECT request_no,payload_json,attempt_count FROM sorting_unpack_request WHERE status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=? ORDER BY recorded_at,request_no LIMIT 20",
                (rs, n) -> new Delivery(rs.getString(1), rs.getString(2), rs.getInt(3)), now());
        for (var delivery : due) {
            if (!claim(delivery)) continue;
            try {
                if (token.isBlank()) throw new IllegalStateException("packing.internal-token is not configured");
                var command = json.readValue(delivery.payload(), UnpackingConfirmation.class);
                var result = oms.accept(command, token);
                if (result == null || !result.isSuccess() || result.getData() == null) {
                    fail(delivery, result == null ? "empty opening response" : result.getMessage(), result != null && conflict(result.getCode()));
                    continue;
                }
                confirmed += confirm(delivery, result.getData());
            } catch (feign.FeignException e) { fail(delivery, e.getMessage(), conflict(e.status())); }
            catch (Exception e) { fail(delivery, e.getMessage(), false); }
        }
        return confirmed;
    }
    public boolean claim(Delivery d) {
        return jdbc.update("UPDATE sorting_unpack_request SET status='SENDING',attempt_count=attempt_count+1,next_attempt_at=? WHERE request_no=? AND attempt_count=? AND status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=?",
                now().plusSeconds(60), d.requestNo(), d.attempts(), now()) == 1;
    }
    public int confirm(Delivery d, UnpackingConfirmation.Ack ack) throws Exception {
        var command = json.readValue(d.payload(), UnpackingConfirmation.class);
        if (ack == null || !command.equals(ack.command()) || ack.appliedAt() == null || ack.appliedAt().isAfter(Instant.now().plusSeconds(60))) {
            fail(d, "opening acknowledgement differs from saved command", true); return 0;
        }
        return jdbc.update("UPDATE sorting_unpack_request SET status='SENT',receipt_json=?,confirmed_at=?,last_error=NULL WHERE request_no=? AND status='SENDING' AND attempt_count=?",
                json.writeValueAsString(ack), LocalDateTime.ofInstant(ack.appliedAt(), ZoneOffset.UTC), d.requestNo(), d.attempts() + 1);
    }
    public void fail(Delivery d, String error, boolean conflict) {
        String message = error == null ? "opening delivery failed" : error.substring(0, Math.min(512, error.length()));
        jdbc.update("UPDATE sorting_unpack_request SET status=?,next_attempt_at=?,last_error=? WHERE request_no=? AND status='SENDING' AND attempt_count=?",
                conflict ? "CONFLICT" : "RETRY", now().plusSeconds(Math.min(3600, 5L << Math.min(9, d.attempts()))), message, d.requestNo(), d.attempts() + 1);
    }
    private boolean conflict(Integer code) { return code != null && (code == 400 || code == 403 || code == 409); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
