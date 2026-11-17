package com.liana.post.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.transport.client.DispatchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Durable at-least-once transport commands, with leased attempts and fenced receipts. */
@Service
public class ManifestAssignmentPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DispatchClient dispatch;
    private final TransactionTemplate tx;
    private final String token;
    public record Delivery(String requestNo, String assignmentNo, String payload, int attempts) { }

    public ManifestAssignmentPublisher(JdbcTemplate jdbc, ObjectMapper json, DispatchClient dispatch,
            PlatformTransactionManager manager, @Value("${manifest.internal-token:}") String token) {
        this.jdbc = jdbc; this.json = json; this.dispatch = dispatch; this.tx = new TransactionTemplate(manager); this.token = token;
    }

    @Scheduled(fixedDelayString = "${manifest.assignment-publish-delay-ms:5000}")
    public void publish() { publishDue(); }

    public int publishDue() {
        int count = 0;
        for (var delivery : due()) {
            if (!claim(delivery)) continue;
            try {
                var command = json.readValue(delivery.payload(), ManifestAllocation.Command.class);
                var result = dispatch.allocation(command, token);
                if (result == null || !result.isSuccess()) {
                    fail(delivery, result == null ? "empty allocation response" : result.getMessage(), result != null && Integer.valueOf(409).equals(result.getCode()));
                    continue;
                }
                var receipt = result.getData();
                if (receipt == null || !command.equals(receipt.command()) || receipt.confirmedAt() == null || receipt.confirmedAt().isAfter(java.time.Instant.now().plusSeconds(60))) {
                    fail(delivery, "allocation receipt does not match fixed command", true);
                    continue;
                }
                confirm(delivery, receipt); count++;
            } catch (feign.FeignException e) {
                fail(delivery, e.getMessage(), e.status() == 409);
            } catch (Exception e) { fail(delivery, e.getMessage(), false); }
        }
        return count;
    }

    public List<Delivery> due() {
        return jdbc.query("SELECT o.request_no,o.assignment_no,o.payload_json,o.attempt_count FROM transport_assignment_outbox o "
                + "WHERE o.status IN ('PENDING','RETRY','SENDING') AND o.next_attempt_at<=? AND (o.action='ASSIGN' OR EXISTS "
                + "(SELECT 1 FROM transport_assignment_outbox a WHERE a.assignment_no=o.assignment_no AND a.action='ASSIGN' AND a.status IN ('SENT','CONFLICT'))) "
                + "ORDER BY o.created_at,o.request_no LIMIT 20", (rs, n) -> new Delivery(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)), now());
    }

    public boolean claim(Delivery d) {
        return jdbc.update("UPDATE transport_assignment_outbox SET status='SENDING',attempt_count=attempt_count+1,next_attempt_at=? "
                + "WHERE request_no=? AND attempt_count=? AND status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=?", now().plusSeconds(60), d.requestNo(), d.attempts(), now()) == 1;
    }

    public void confirm(Delivery d, ManifestAllocation.Receipt receipt) {
        tx.executeWithoutResult(s -> {
            var task = lockTask(d.assignmentNo());
            var rows = jdbc.queryForList("SELECT payload_json FROM transport_assignment_outbox WHERE request_no=? AND assignment_no=? AND status='SENDING' AND attempt_count=? FOR UPDATE", d.requestNo(), d.assignmentNo(), d.attempts() + 1);
            if (rows.isEmpty()) return;
            var command = read((String) rows.get(0).get("payload_json"));
            if (!command.equals(receipt.command()) || receipt.confirmedAt() == null) throw new IllegalArgumentException("receipt mismatch");
            jdbc.update("UPDATE transport_assignment_outbox SET status='SENT',receipt_json=?,confirmed_at=?,last_error=NULL WHERE request_no=?", write(receipt), LocalDateTime.ofInstant(receipt.confirmedAt(), ZoneOffset.UTC), d.requestNo());
            if ("ASSIGN".equals(command.action())) {
                jdbc.update("UPDATE transport_manifest_assignment SET confirmed_at=?,status=CASE WHEN status='PENDING' THEN 'CONFIRMED' ELSE status END WHERE assignment_no=?",
                        LocalDateTime.ofInstant(receipt.confirmedAt(), ZoneOffset.UTC), d.assignmentNo());
            } else {
                jdbc.update("UPDATE transport_manifest_assignment SET status='RELEASED',released_at=? WHERE assignment_no=? AND status='RELEASING'", LocalDateTime.ofInstant(receipt.confirmedAt(), ZoneOffset.UTC), d.assignmentNo());
                jdbc.update("DELETE FROM transport_bag_current WHERE assignment_no=?", d.assignmentNo());
                jdbc.update("DELETE FROM transport_manifest_current WHERE assignment_no=?", d.assignmentNo());
                if (jdbc.queryForObject("SELECT COUNT(*) FROM transport_manifest_assignment WHERE task_id=? AND status<>'RELEASED'", Long.class, task) == 0) {
                    jdbc.update("UPDATE transport_task SET status='CANCELLED' WHERE id=? AND status='CANCELLING'", task);
                }
            }
        });
    }

    public void fail(Delivery d, String error, boolean conflict) {
        tx.executeWithoutResult(s -> {
            lockTask(d.assignmentNo());
            String message = error == null ? "allocation delivery failed" : error.substring(0, Math.min(512, error.length()));
            int changed = jdbc.update("UPDATE transport_assignment_outbox SET status=?,next_attempt_at=?,last_error=? WHERE request_no=? AND status='SENDING' AND attempt_count=?",
                    conflict ? "CONFLICT" : "RETRY", now().plusSeconds(Math.min(3600, 5L << Math.min(9, d.attempts()))), message, d.requestNo(), d.attempts() + 1);
            if (conflict && changed == 1) jdbc.update("UPDATE transport_manifest_assignment SET status='CONFLICT' WHERE assignment_no=? AND status NOT IN ('RELEASING','RELEASED')", d.assignmentNo());
        });
    }

    private Long lockTask(String assignment) {
        Long id = jdbc.queryForObject("SELECT task_id FROM transport_manifest_assignment WHERE assignment_no=?", Long.class, assignment);
        // Same schedule -> task lock order as the task service; retry if concurrent scheduling moved it.
        Long schedule = jdbc.queryForObject("SELECT schedule_id FROM transport_task WHERE id=?", Long.class, id);
        if (schedule != null) jdbc.queryForList("SELECT id FROM transport_schedule WHERE id=? FOR UPDATE", schedule);
        var task = jdbc.queryForMap("SELECT schedule_id FROM transport_task WHERE id=? FOR UPDATE", id);
        Long current = task.get("schedule_id") == null ? null : ((Number) task.get("schedule_id")).longValue();
        if (!java.util.Objects.equals(schedule, current)) throw new IllegalStateException("task schedule changed; retry receipt");
        return id;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    private ManifestAllocation.Command read(String payload) { try { return json.readValue(payload, ManifestAllocation.Command.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
}
