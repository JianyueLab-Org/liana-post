package com.liana.post.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.ManifestSealCommand;
import com.liana.post.common.dto.sorting.ManifestCancelCommand;
import com.liana.post.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ManifestOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ManifestOutbox(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Delivery(String eventNo, String payload, int attempts, String eventType) { }
    public record SealState(String status, String lastError) { }
    public record CancelState(String status, String lastError, String reason) { }

    /** The batch and its prealert must commit or roll back together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(ManifestDTO request) {
        requireNotCancelling(request.getManifestCode());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='SEAL' AND version_no<>?",
                Long.class, request.getManifestCode(), request.getVersionNo()) > 0) {
            throw new BusinessException(409, "manifest has a fixed seal request; a new prealert version is forbidden");
        }
        String payload;
        try { payload = json.writeValueAsString(request); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode prealert", e); }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        LocalDateTime now = now();
        jdbc.update("INSERT INTO dispatch_manifest_outbox (event_no,manifest_no,version_no,payload_json,content_hash,created_at,next_attempt_at) "
                        + "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_no=event_no", UUID.randomUUID().toString(),
                request.getManifestCode(), request.getVersionNo(), payload, hash, now, now);
        String existing = jdbc.queryForObject("SELECT content_hash FROM dispatch_manifest_outbox WHERE manifest_no=? AND version_no=? AND event_type='PREALERT' FOR UPDATE",
                String.class, request.getManifestCode(), request.getVersionNo());
        if (!hash.equals(existing)) throw new BusinessException(409, "queued manifest version cannot be overwritten");
    }

    public String status(String manifestNo) {
        if (manifestNo == null) return "NOT_CREATED";
        List<String> states = jdbc.query("SELECT status FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='PREALERT' ORDER BY version_no DESC LIMIT 1",
                (rs, row) -> rs.getString(1), manifestNo);
        return states.isEmpty() ? "NOT_CREATED" : states.get(0);
    }

    /** A repeated UI request reuses the first fixed command, even if a different clerk retries it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSeal(String manifestNo, String actorId) {
        var prealerts = jdbc.queryForList("SELECT version_no,status,payload_json FROM dispatch_manifest_outbox "
                + "WHERE manifest_no=? AND event_type='PREALERT' ORDER BY version_no DESC LIMIT 1 FOR UPDATE", manifestNo);
        if (prealerts.isEmpty() || !"SENT".equals(prealerts.get(0).get("status"))) {
            throw new BusinessException(409, "prealert must be acknowledged before sealing");
        }
        requireNotCancelling(manifestNo);
        var prealert = prealerts.get(0);
        int version = ((Number) prealert.get("version_no")).intValue();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_outbox WHERE manifest_no=? AND version_no=? AND event_type='SEAL'",
                Long.class, manifestNo, version) > 0) return;
        try {
            var expected = json.readTree((String) prealert.get("payload_json"));
            var command = new ManifestSealCommand(UUID.randomUUID().toString(), manifestNo, version,
                    expected.path("originNode").asText(), actorId).normalized();
            String payload = json.writeValueAsString(command);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
            LocalDateTime now = now();
            jdbc.update("INSERT INTO dispatch_manifest_outbox (event_no,manifest_no,version_no,event_type,payload_json,content_hash,created_at,next_attempt_at) "
                            + "VALUES (?,?,?,'SEAL',?,?,?,?)", command.requestNo(), manifestNo, version, payload, hash, now, now);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public SealState sealState(String manifestNo) {
        if (manifestNo == null) return new SealState("NOT_REQUESTED", null);
        List<SealState> states = jdbc.query("SELECT status,last_error FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='SEAL' "
                        + "AND version_no=(SELECT MAX(version_no) FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='PREALERT')",
                (rs, row) -> new SealState(rs.getString(1), rs.getString(2)), manifestNo, manifestNo);
        return states.isEmpty() ? new SealState("NOT_REQUESTED", null) : states.get(0);
    }

    public void requireSealed(String manifestNo) {
        requireNotCancelling(manifestNo);
        if (!"SENT".equals(sealState(manifestNo).status())) {
            throw new BusinessException(409, "路单尚未封单确认，请在批次页面申请封单并等待确认");
        }
    }

    public void requireNotCancelling(String manifestNo) {
        if (manifestNo != null && jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='CANCEL'",
                Long.class, manifestNo) > 0) throw new BusinessException(409, "路单正在作废或已作废，禁止继续交接或关联任务");
    }

    public void requireAssigned(String manifestNo, String taskCode) {
        if (taskCode == null || jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_manifest_assignment WHERE manifest_no=? AND task_code=?", Long.class, manifestNo, taskCode) != 1) {
            throw new BusinessException(409, "路单尚未完成整单运输分配确认，禁止交接");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueCancel(String manifestNo, String actorId, String reason) {
        var prealerts = jdbc.queryForList("SELECT version_no,status,payload_json FROM dispatch_manifest_outbox "
                + "WHERE manifest_no=? AND event_type='PREALERT' ORDER BY version_no DESC LIMIT 1 FOR UPDATE", manifestNo);
        if (prealerts.isEmpty() || !"SENT".equals(prealerts.get(0).get("status"))) {
            throw new BusinessException(409, "prealert must be acknowledged before cancellation");
        }
        var prealert = prealerts.get(0);
        int version = ((Number) prealert.get("version_no")).intValue();
        try {
            var expected = json.readTree((String) prealert.get("payload_json"));
            var command = new ManifestCancelCommand(UUID.randomUUID().toString(), manifestNo, version,
                    expected.path("originNode").asText(), actorId, reason).normalized();
            var existing = jdbc.queryForList("SELECT payload_json FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='CANCEL'", manifestNo);
            if (!existing.isEmpty()) {
                if (!command.reason().equals(json.readTree((String) existing.get(0).get("payload_json")).path("reason").asText())) {
                    throw new BusinessException(409, "cancellation reason is already fixed by the first request");
                }
                return; // Keep original request, reason and authenticated actor.
            }
            String seal = sealState(manifestNo).status();
            if (!java.util.Set.of("NOT_REQUESTED", "SENT").contains(seal)) {
                throw new BusinessException(409, "wait for the outstanding seal command to be resolved before cancelling");
            }
            String payload = json.writeValueAsString(command);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
            LocalDateTime now = now();
            jdbc.update("INSERT INTO dispatch_manifest_outbox (event_no,manifest_no,version_no,event_type,payload_json,content_hash,created_at,next_attempt_at) "
                            + "VALUES (?,?,?,'CANCEL',?,?,?,?)", command.requestNo(), manifestNo, version, payload, hash, now, now);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public CancelState cancelState(String manifestNo) {
        if (manifestNo == null) return new CancelState("NOT_REQUESTED", null, null);
        var states = jdbc.query("SELECT status,last_error,payload_json FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='CANCEL' ORDER BY version_no DESC LIMIT 1",
                (rs, row) -> {
                    try { return new CancelState(rs.getString(1), rs.getString(2), json.readTree(rs.getString(3)).path("reason").asText()); }
                    catch (JsonProcessingException e) { throw new IllegalStateException(e); }
                }, manifestNo);
        return states.isEmpty() ? new CancelState("NOT_REQUESTED", null, null) : states.get(0);
    }

    public List<Delivery> due() {
        return jdbc.query("SELECT event_no,payload_json,attempt_count,event_type FROM dispatch_manifest_outbox "
                        + "WHERE status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=? ORDER BY created_at,event_no LIMIT 10",
                (rs, row) -> new Delivery(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)), now());
    }

    public boolean claim(Delivery delivery) {
        LocalDateTime now = now();
        // A lease allows recovery after process death. Conditional claiming prevents normal concurrent delivery.
        return jdbc.update("UPDATE dispatch_manifest_outbox SET status='SENDING',attempt_count=attempt_count+1,next_attempt_at=? "
                        + "WHERE event_no=? AND status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=? AND attempt_count=?",
                now.plusSeconds(60), delivery.eventNo(), now, delivery.attempts()) == 1;
    }

    public void sent(Delivery delivery) {
        sent(delivery, null);
    }

    @Transactional
    public void sent(Delivery delivery, String receipt) {
        if ("CANCEL".equals(delivery.eventType())) {
            // Always acquire the batch before its outbox, matching source commands and handoff.
            var batches = jdbc.queryForList("SELECT batch_no,status FROM dispatch_batch WHERE manifest_no="
                    + "(SELECT manifest_no FROM dispatch_manifest_outbox WHERE event_no=?) FOR UPDATE", delivery.eventNo());
            if (batches.size() != 1) throw new BusinessException(409, "cancellation batch is missing");
            if (receipt == null) throw new BusinessException(409, "cancellation requires its validated receipt");
            int changed = jdbc.update("UPDATE dispatch_manifest_outbox SET status='SENT',sent_at=?,last_error=NULL,receipt_json=? "
                            + "WHERE event_no=? AND event_type='CANCEL' AND status='SENDING' AND attempt_count=?", now(), receipt, delivery.eventNo(), delivery.attempts() + 1);
            if (changed == 1 && jdbc.update("UPDATE dispatch_batch SET status='CANCELLED',updated_at=? WHERE batch_no=? AND status='CANCELLING'",
                    now(), batches.get(0).get("batch_no")) != 1) {
                throw new BusinessException(409, "cancellation batch is not awaiting confirmation");
            }
            if (changed == 1) jdbc.update("UPDATE dispatch_batch_bag SET active_flag=NULL WHERE batch_no=? AND active_flag=1", batches.get(0).get("batch_no"));
            return;
        }
        jdbc.update("UPDATE dispatch_manifest_outbox SET status='SENT',sent_at=?,last_error=NULL,receipt_json=? "
                        + "WHERE event_no=? AND status='SENDING' AND attempt_count=?", now(), receipt, delivery.eventNo(), delivery.attempts() + 1);
    }

    public void conflict(Delivery delivery, String error) {
        jdbc.update("UPDATE dispatch_manifest_outbox SET status='CONFLICT',last_error=? WHERE event_no=? AND status='SENDING' AND attempt_count=?",
                error == null ? "seal conflict" : error.substring(0, Math.min(error.length(), 512)), delivery.eventNo(), delivery.attempts() + 1);
    }

    public void failed(Delivery delivery, String error) {
        String message = error == null ? "delivery failed" : error.substring(0, Math.min(error.length(), 512));
        long delay = Math.min(3600, 5L << Math.min(delivery.attempts(), 9));
        jdbc.update("UPDATE dispatch_manifest_outbox SET status='RETRY',next_attempt_at=?,last_error=? "
                        + "WHERE event_no=? AND status='SENDING' AND attempt_count=?", now().plusSeconds(delay), message,
                delivery.eventNo(), delivery.attempts() + 1);
    }

    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
}
