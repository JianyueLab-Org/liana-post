package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.RepackingCommand;
import com.liana.post.sorting.client.DispatchLifecycleClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class RepackingPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DispatchLifecycleClient dispatch;
    private final String token;
    public record Delivery(String key, String payload, int attempts) { }
    public RepackingPublisher(JdbcTemplate jdbc, ObjectMapper json, DispatchLifecycleClient dispatch, @Value("${manifest.internal-token:}") String token) {
        this.jdbc = jdbc; this.json = json; this.dispatch = dispatch; this.token = token;
    }
    @Scheduled(fixedDelayString = "${packing.repack-publish-delay-ms:5000}")
    public void publish() { publishDue(); }
    public int publishDue() {
        int sent = 0;
        var due = jdbc.query("SELECT request_no,payload_json,attempt_count FROM sorting_repack_request WHERE status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=? ORDER BY occurred_at,request_no LIMIT 20",
                (rs, n) -> new Delivery(rs.getString(1), rs.getString(2), rs.getInt(3)), now());
        for (var d : due) {
            if (!claim(d)) continue;
            try {
                if (token.isBlank()) throw new IllegalStateException("manifest.internal-token is not configured");
                var command = json.readValue(d.payload(), RepackingCommand.class);
                var result = dispatch.repack(command, token);
                if (result == null || !result.isSuccess() || result.getData() == null) {
                    fail(d, result == null ? "empty repacking response" : result.getMessage(), result != null && conflict(result.getCode())); continue;
                }
                sent += confirm(d, result.getData());
            } catch (feign.FeignException e) { fail(d, e.getMessage(), conflict(e.status())); }
            catch (Exception e) { fail(d, e.getMessage(), false); }
        }
        return sent;
    }
    public boolean claim(Delivery d) {
        return jdbc.update("UPDATE sorting_repack_request SET status='SENDING',attempt_count=attempt_count+1,next_attempt_at=? WHERE request_no=? AND attempt_count=? AND status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=?", now().plusSeconds(60), d.key(), d.attempts(), now()) == 1;
    }
    public int confirm(Delivery d, RepackingCommand.Ack ack) throws Exception {
        var command = json.readValue(d.payload(), RepackingCommand.class);
        if (ack == null || !command.equals(ack.command()) || ack.preparedAt() == null || ack.preparedAt().isAfter(Instant.now().plusSeconds(60))) {
            fail(d, "repacking acknowledgement differs from reservation", true); return 0;
        }
        return jdbc.update("UPDATE sorting_repack_request SET status='SENT',receipt_json=?,confirmed_at=?,last_error=NULL WHERE request_no=? AND status='SENDING' AND attempt_count=?", json.writeValueAsString(ack), LocalDateTime.ofInstant(ack.preparedAt(), ZoneOffset.UTC), d.key(), d.attempts() + 1);
    }
    public void fail(Delivery d, String error, boolean conflict) {
        String message = error == null ? "repacking failed" : error.substring(0, Math.min(512, error.length()));
        jdbc.update("UPDATE sorting_repack_request SET status=?,last_error=?,next_attempt_at=? WHERE request_no=? AND status='SENDING' AND attempt_count=?", conflict ? "CONFLICT" : "RETRY", message, now().plusSeconds(Math.min(3600, 5L << Math.min(9, d.attempts()))), d.key(), d.attempts() + 1);
    }
    private boolean conflict(Integer code) { return code != null && (code == 400 || code == 403 || code == 409); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
