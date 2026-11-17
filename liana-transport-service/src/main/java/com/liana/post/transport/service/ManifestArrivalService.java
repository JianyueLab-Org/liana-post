package com.liana.post.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.dto.sorting.ManifestClosure;
import com.liana.post.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class ManifestArrivalService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ManifestArrivalService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public ManifestReceipt.Arrival arrival(String manifestNo) {
        var rows = jdbc.queryForList("SELECT a.*,t.task_code FROM transport_manifest_assignment a JOIN transport_task t ON t.id=a.task_id "
                + "WHERE a.manifest_no=? AND a.status IN ('CONFIRMED','RECEIVED') AND t.status IN ('ARRIVED','COMPLETED')", manifestNo);
        if (rows.size() != 1) throw new BusinessException(409, "manifest has no confirmed arrived transport task");
        var a = rows.get(0);
        var bags = jdbc.query("SELECT bag_no FROM transport_assignment_bag WHERE assignment_no=? ORDER BY bag_no", (rs, n) -> rs.getString(1), a.get("assignment_no"));
        return new ManifestReceipt.Arrival((String) a.get("assignment_no"), (String) a.get("task_code"), (String) a.get("manifest_no"),
                ((Number) a.get("version_no")).intValue(), (String) a.get("content_hash"), (String) a.get("source_org_code"), (String) a.get("destination_org_code"), bags);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ManifestReceipt.Ack receive(ManifestReceipt.Event input) {
        var event = input.normalized();
        var assignments = jdbc.queryForList("SELECT task_id FROM transport_manifest_assignment WHERE assignment_no=?", event.assignmentNo());
        if (assignments.isEmpty()) throw new BusinessException(409, "receipt assignment not found");
        Object taskId = assignments.get(0).get("task_id");
        Long schedule = jdbc.queryForObject("SELECT schedule_id FROM transport_task WHERE id=?", Long.class, taskId);
        if (schedule != null) jdbc.queryForList("SELECT id FROM transport_schedule WHERE id=? FOR UPDATE", schedule);
        var task = jdbc.queryForMap("SELECT * FROM transport_task WHERE id=? FOR UPDATE", taskId);
        Long currentSchedule = task.get("schedule_id") == null ? null : ((Number) task.get("schedule_id")).longValue();
        if (!Objects.equals(schedule, currentSchedule)) throw new BusinessException(409, "receipt task schedule changed");
        var previous = jdbc.queryForList("SELECT payload_json,receipt_json FROM transport_receipt_inbox WHERE event_no=?", event.eventNo());
        if (!previous.isEmpty()) {
            if (!event.equals(read((String) previous.get(0).get("payload_json"), ManifestReceipt.Event.class))) throw new BusinessException(409, "receipt event content changed");
            return read((String) previous.get(0).get("receipt_json"), ManifestReceipt.Ack.class);
        }
        var a = jdbc.queryForMap("SELECT * FROM transport_manifest_assignment WHERE assignment_no=?", event.assignmentNo());
        if (!List.of("ARRIVED", "COMPLETED").contains(task.get("status")) || !"CONFIRMED".equals(a.get("status"))
                || !event.taskCode().equals(task.get("task_code")) || !event.manifestNo().equals(a.get("manifest_no"))
                || event.versionNo() != ((Number) a.get("version_no")).intValue() || !event.contentHash().equals(a.get("content_hash"))
                || !event.sourceOrgCode().equals(a.get("source_org_code")) || !event.destinationOrgCode().equals(a.get("destination_org_code"))) {
            throw new BusinessException(409, "receipt differs from arrived assignment");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transport_assignment_bag WHERE assignment_no=? AND bag_no=?", Long.class, event.assignmentNo(), event.bagNo()) != 1
                || jdbc.queryForObject("SELECT COUNT(*) FROM transport_receipt_inbox WHERE assignment_no=? AND bag_no=?", Long.class, event.assignmentNo(), event.bagNo()) != 0) {
            throw new BusinessException(409, "bag not assigned or already received by another event");
        }
        var ack = new ManifestReceipt.Ack(event, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        String payload = write(event);
        jdbc.update("INSERT INTO transport_receipt_inbox (event_no,assignment_no,bag_no,manifest_no,request_hash,payload_json,receipt_json,confirmed_at) VALUES (?,?,?,?,?,?,?,?)",
                event.eventNo(), event.assignmentNo(), event.bagNo(), event.manifestNo(), hash(payload), payload, write(ack), LocalDateTime.ofInstant(ack.confirmedAt(), ZoneOffset.UTC));
        // A bag receipt releases only that bag. The manifest remains occupied until every bag is received.
        if (jdbc.update("DELETE FROM transport_bag_current WHERE bag_no=? AND assignment_no=?", event.bagNo(), event.assignmentNo()) != 1) {
            throw new BusinessException(409, "receipt cannot release a different current bag assignment");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transport_assignment_bag b WHERE b.assignment_no=? AND NOT EXISTS "
                + "(SELECT 1 FROM transport_receipt_inbox r WHERE r.assignment_no=b.assignment_no AND r.bag_no=b.bag_no)", Long.class, event.assignmentNo()) == 0) {
            // The physical time is obtained from all received events, independent of delivery order.
            var times = jdbc.query("SELECT payload_json FROM transport_receipt_inbox WHERE assignment_no=?", (rs, n) -> read(rs.getString(1), ManifestReceipt.Event.class).receivedAt(), event.assignmentNo());
            jdbc.update("UPDATE transport_manifest_assignment SET status='RECEIVED',received_at=? WHERE assignment_no=?", LocalDateTime.ofInstant(times.stream().max(Instant::compareTo).orElseThrow(), ZoneOffset.UTC), event.assignmentNo());
            if (jdbc.update("DELETE FROM transport_manifest_current WHERE manifest_no=? AND assignment_no=?", event.manifestNo(), event.assignmentNo()) != 1) {
                throw new BusinessException(409, "receipt cannot release a different current manifest assignment");
            }
        }
        return ack;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ManifestClosure.Ack close(ManifestClosure.Command input) {
        var command = input.normalized(); var a = command.arrival();
        var identity = jdbc.queryForList("SELECT task_id FROM transport_manifest_assignment WHERE assignment_no=?", a.assignmentNo());
        if (identity.size() != 1) throw new BusinessException(409, "closure assignment not found");
        Object taskId = identity.get(0).get("task_id");
        Long schedule = jdbc.queryForObject("SELECT schedule_id FROM transport_task WHERE id=?", Long.class, taskId);
        if (schedule != null) jdbc.queryForList("SELECT id FROM transport_schedule WHERE id=? FOR UPDATE", schedule);
        var task = jdbc.queryForMap("SELECT * FROM transport_task WHERE id=? FOR UPDATE", taskId);
        Long currentSchedule = task.get("schedule_id") == null ? null : ((Number) task.get("schedule_id")).longValue();
        if (!Objects.equals(schedule, currentSchedule)) throw new BusinessException(409, "closure task schedule changed");
        var prior = jdbc.queryForList("SELECT payload_json,receipt_json FROM transport_manifest_close_inbox WHERE manifest_no=? OR request_no=?", a.manifestNo(), command.requestNo());
        if (!prior.isEmpty()) {
            if (prior.size() != 1 || !command.equals(read((String) prior.get(0).get("payload_json"), ManifestClosure.Command.class))) throw new BusinessException(409, "closure content changed");
            return read((String) prior.get(0).get("receipt_json"), ManifestClosure.Ack.class);
        }
        var rows = jdbc.queryForList("SELECT * FROM transport_manifest_assignment WHERE assignment_no=? FOR UPDATE", a.assignmentNo());
        if (rows.size() != 1 || !a.manifestNo().equals(rows.get(0).get("manifest_no")) || !a.taskCode().equals(task.get("task_code"))
                || !List.of("ARRIVED", "COMPLETED").contains(task.get("status"))
                || !(command.missingBagNos().isEmpty() ? "RECEIVED" : "CONFIRMED").equals(rows.get(0).get("status"))
                || a.versionNo() != ((Number) rows.get(0).get("version_no")).intValue() || !a.contentHash().equals(rows.get(0).get("content_hash"))
                || !a.sourceOrgCode().equals(rows.get(0).get("source_org_code")) || !a.destinationOrgCode().equals(rows.get(0).get("destination_org_code"))) throw new BusinessException(409, "closure differs from transport assignment");
        var assigned = jdbc.query("SELECT bag_no FROM transport_assignment_bag WHERE assignment_no=? ORDER BY bag_no", (rs, n) -> rs.getString(1), a.assignmentNo());
        if (!assigned.equals(a.bagNos())) throw new BusinessException(409, "closure bag set differs from transport assignment");
        var actual = jdbc.query("SELECT bag_no FROM transport_receipt_inbox WHERE assignment_no=? ORDER BY bag_no", (rs, n) -> rs.getString(1), a.assignmentNo());
        if (!actual.equals(command.receivedBagNos())) throw new BusinessException(409, "received partition differs from physical transport receipts");
        for (String bag : command.missingBagNos()) {
            if (jdbc.update("DELETE FROM transport_bag_current WHERE bag_no=? AND assignment_no=?", bag, a.assignmentNo()) != 1) throw new BusinessException(409, "missing bag current assignment changed");
        }
        if (!command.missingBagNos().isEmpty()
                && jdbc.update("DELETE FROM transport_manifest_current WHERE manifest_no=? AND assignment_no=?", a.manifestNo(), a.assignmentNo()) != 1) throw new BusinessException(409, "manifest current assignment changed");
        if (!command.missingBagNos().isEmpty()) jdbc.update("UPDATE transport_manifest_assignment SET status='SETTLED' WHERE assignment_no=?", a.assignmentNo());
        var ack = new ManifestClosure.Ack(command, "TRANSPORT", Instant.now().truncatedTo(ChronoUnit.MILLIS));
        jdbc.update("INSERT INTO transport_manifest_close_inbox (manifest_no,request_no,assignment_no,payload_json,receipt_json,confirmed_at) VALUES (?,?,?,?,?,?)",
                a.manifestNo(), command.requestNo(), a.assignmentNo(), write(command), write(ack), LocalDateTime.ofInstant(ack.confirmedAt(), ZoneOffset.UTC));
        return ack;
    }

    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
