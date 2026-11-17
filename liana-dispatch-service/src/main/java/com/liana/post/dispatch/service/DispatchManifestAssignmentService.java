package com.liana.post.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.dto.sorting.ManifestClosure;
import com.liana.post.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Source projection. Batch -> sorted bags is the common lock order for every source mutation. */
@Service
public class DispatchManifestAssignmentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ManifestOutbox outbox;

    public DispatchManifestAssignmentService(JdbcTemplate jdbc, ObjectMapper json, ManifestOutbox outbox) {
        this.jdbc = jdbc;
        this.json = json;
        this.outbox = outbox;
    }

    @Transactional
    public ManifestAllocation.Snapshot snapshot(String manifestNo) {
        String no = ManifestAllocation.code(manifestNo);
        lockBatch(no);
        outbox.requireSealed(no);
        return sealedSnapshot(no);
    }

    @Transactional
    public ManifestAllocation.Receipt apply(ManifestAllocation.Command input) {
        var command = input.normalized();
        lockBatch(command.manifestNo());
        var previous = jdbc.queryForList("SELECT request_json,receipt_json FROM dispatch_assignment_inbox WHERE request_no=?", command.requestNo());
        if (!previous.isEmpty()) {
            if (!command.equals(read((String) previous.get(0).get("request_json"), ManifestAllocation.Command.class))) {
                throw new BusinessException(409, "allocation request number has different content");
            }
            return read((String) previous.get(0).get("receipt_json"), ManifestAllocation.Receipt.class);
        }
        var history = jdbc.queryForList("SELECT request_json,action FROM dispatch_assignment_inbox WHERE assignment_no=?", command.assignmentNo());
        for (var entry : history) {
            var old = read((String) entry.get("request_json"), ManifestAllocation.Command.class);
            if (!sameAssignment(old, command)) throw new BusinessException(409, "assignment identity cannot change");
            if (old.action().equals(command.action())) throw new BusinessException(409, "reuse the original allocation request number");
            if ("ASSIGN".equals(command.action())) throw new BusinessException(409, "released assignment cannot be reactivated");
        }
        var snapshot = sealedSnapshot(command.manifestNo());
        if (snapshot.versionNo() != command.versionNo() || !snapshot.contentHash().equals(command.contentHash())) {
            throw new BusinessException(409, "allocation does not match sealed manifest version");
        }
        var current = jdbc.queryForList("SELECT assignment_no,task_code FROM dispatch_manifest_assignment WHERE manifest_no=?", command.manifestNo());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if ("ASSIGN".equals(command.action())) {
            outbox.requireSealed(command.manifestNo());
            if (!current.isEmpty()) throw new BusinessException(409, "manifest already assigned");
            for (var bag : snapshot.bags()) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag WHERE bag_no=? AND batch_no=? AND active_flag=1", Long.class,
                        bag.bagNo(), batchNo(command.manifestNo())) != 1) throw new BusinessException(409, "manifest no longer owns this bag's current leg");
                var row = jdbc.queryForMap("SELECT transport_task_code,status FROM dispatch_bag WHERE id=?", bag.dispatchBagId());
                if (row.get("transport_task_code") != null && !row.get("transport_task_code").toString().isBlank()) {
                    throw new BusinessException(409, "bag already assigned");
                }
                if (!List.of("REVIEWED", "DISPATCHED").contains(row.get("status"))) throw new BusinessException(409, "bag is not available for transport");
            }
            jdbc.update("INSERT INTO dispatch_manifest_assignment (manifest_no,assignment_no,task_code,version_no,content_hash,confirmed_at) VALUES (?,?,?,?,?,?)",
                    command.manifestNo(), command.assignmentNo(), command.taskCode(), command.versionNo(), command.contentHash(), LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            for (var bag : snapshot.bags()) jdbc.update("UPDATE dispatch_bag SET transport_task_code=? WHERE id=?", command.taskCode(), bag.dispatchBagId());
        } else if (!current.isEmpty() && command.assignmentNo().equals(current.get(0).get("assignment_no"))) {
            if (!command.taskCode().equals(current.get(0).get("task_code"))) throw new BusinessException(409, "release task mismatch");
            for (var bag : snapshot.bags()) {
                if (jdbc.update("UPDATE dispatch_bag SET transport_task_code=NULL WHERE id=? AND transport_task_code=?", bag.dispatchBagId(), command.taskCode()) != 1) {
                    throw new BusinessException(409, "source bag allocation changed");
                }
            }
            jdbc.update("DELETE FROM dispatch_manifest_assignment WHERE manifest_no=? AND assignment_no=?", command.manifestNo(), command.assignmentNo());
        }
        // Also acknowledge RELEASE before ASSIGN. This tombstone fences delayed assignment delivery.
        var receipt = new ManifestAllocation.Receipt(command, now);
        String payload = write(command);
        jdbc.update("INSERT INTO dispatch_assignment_inbox (request_no,assignment_no,manifest_no,action,request_hash,request_json,receipt_json,confirmed_at) VALUES (?,?,?,?,?,?,?,?)",
                command.requestNo(), command.assignmentNo(), command.manifestNo(), command.action(), sha256(payload), payload, write(receipt), LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        return receipt;
    }

    @Transactional
    public ManifestReceipt.Ack receive(ManifestReceipt.Event input) {
        var event = input.normalized();
        lockBatch(event.manifestNo());
        var previous = jdbc.queryForList("SELECT payload_json,receipt_json FROM dispatch_receipt_inbox WHERE event_no=?", event.eventNo());
        if (!previous.isEmpty()) {
            if (!event.equals(read((String) previous.get(0).get("payload_json"), ManifestReceipt.Event.class))) {
                throw new BusinessException(409, "receipt event content changed");
            }
            return read((String) previous.get(0).get("receipt_json"), ManifestReceipt.Ack.class);
        }
        var snapshot = sealedSnapshot(event.manifestNo());
        var current = jdbc.queryForList("SELECT * FROM dispatch_manifest_assignment WHERE manifest_no=?", event.manifestNo());
        if (current.size() != 1 || !event.assignmentNo().equals(current.get(0).get("assignment_no"))
                || !event.taskCode().equals(current.get(0).get("task_code")) || event.versionNo() != snapshot.versionNo()
                || !event.contentHash().equals(snapshot.contentHash()) || !event.sourceOrgCode().equals(snapshot.originFacilityCode())
                || !event.destinationOrgCode().equals(snapshot.destinationFacilityCode())
                || snapshot.bags().stream().noneMatch(b -> b.bagNo().equals(event.bagNo()))) {
            throw new BusinessException(409, "receipt differs from source manifest assignment");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_receipt_inbox WHERE manifest_no=? AND bag_no=?", Long.class, event.manifestNo(), event.bagNo()) != 0) {
            throw new BusinessException(409, "bag already received by another event");
        }
        var ack = new ManifestReceipt.Ack(event, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (jdbc.update("UPDATE dispatch_bag SET status='ARRIVED',transport_task_code=NULL,last_receipt_event_no=?,updated_at=? WHERE bag_no=? AND status='DISPATCHED' AND transport_task_code=?",
                event.eventNo(), LocalDateTime.ofInstant(event.receivedAt(), ZoneOffset.UTC), event.bagNo(), event.taskCode()) != 1) {
            throw new BusinessException(409, "receipt requires the physical handoff to this task");
        }
        if (jdbc.update("UPDATE dispatch_batch_bag SET active_flag=NULL WHERE bag_no=? AND active_flag=1 AND batch_no=?",
                event.bagNo(), batchNo(event.manifestNo())) != 1) throw new BusinessException(409, "current bag batch differs from receipt");
        String payload = write(event);
        jdbc.update("INSERT INTO dispatch_receipt_inbox (event_no,manifest_no,bag_no,request_hash,payload_json,receipt_json,confirmed_at) VALUES (?,?,?,?,?,?,?)",
                event.eventNo(), event.manifestNo(), event.bagNo(), sha256(payload), payload, write(ack), LocalDateTime.ofInstant(ack.confirmedAt(), ZoneOffset.UTC));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_receipt_inbox WHERE manifest_no=?", Long.class, event.manifestNo()) == snapshot.bags().size()) {
            jdbc.update("DELETE FROM dispatch_manifest_assignment WHERE manifest_no=? AND assignment_no=?", event.manifestNo(), event.assignmentNo());
        }
        return ack;
    }

    @Transactional
    public ManifestClosure.Ack close(ManifestClosure.Command input) {
        var command = input.normalized(); var a = command.arrival(); lockBatch(a.manifestNo());
        var saved = jdbc.queryForList("SELECT payload_json,receipt_json FROM dispatch_manifest_close_inbox WHERE manifest_no=? OR request_no=?", a.manifestNo(), command.requestNo());
        if (!saved.isEmpty()) {
            if (saved.size() != 1 || !command.equals(read((String) saved.get(0).get("payload_json"), ManifestClosure.Command.class))) throw new BusinessException(409, "closure content changed");
            return read((String) saved.get(0).get("receipt_json"), ManifestClosure.Ack.class);
        }
        var snapshot = sealedSnapshot(a.manifestNo());
        var assignment = jdbc.queryForList("SELECT * FROM dispatch_manifest_assignment WHERE manifest_no=? FOR UPDATE", a.manifestNo());
        if ((!command.missingBagNos().isEmpty() && assignment.size() != 1)
                || (assignment.size() == 1 && (!a.assignmentNo().equals(assignment.get(0).get("assignment_no")) || !a.taskCode().equals(assignment.get(0).get("task_code"))))
                || assignment.size() > 1
                || a.versionNo() != snapshot.versionNo() || !a.contentHash().equals(snapshot.contentHash())
                || !a.sourceOrgCode().equals(snapshot.originFacilityCode()) || !a.destinationOrgCode().equals(snapshot.destinationFacilityCode())
                || !snapshot.bags().stream().map(ManifestAllocation.Bag::bagNo).sorted().toList().equals(a.bagNos())) throw new BusinessException(409, "closure differs from dispatch assignment");
        var actual = jdbc.query("SELECT bag_no FROM dispatch_receipt_inbox WHERE manifest_no=? ORDER BY bag_no", (rs, n) -> rs.getString(1), a.manifestNo());
        if (!actual.equals(command.receivedBagNos())) throw new BusinessException(409, "received partition differs from physical dispatch receipts");
        for (String payload : jdbc.query("SELECT payload_json FROM dispatch_receipt_inbox WHERE manifest_no=?", (rs,n) -> rs.getString(1), a.manifestNo())) {
            var event = read(payload, ManifestReceipt.Event.class);
            if (!a.assignmentNo().equals(event.assignmentNo()) || !a.taskCode().equals(event.taskCode()) || a.versionNo() != event.versionNo()
                    || !a.contentHash().equals(event.contentHash()) || !a.sourceOrgCode().equals(event.sourceOrgCode()) || !a.destinationOrgCode().equals(event.destinationOrgCode())) throw new BusinessException(409, "closure receipt assignment differs");
        }
        for (String bagNo : command.missingBagNos()) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_receipt_inbox WHERE manifest_no=? AND bag_no=?", Long.class, a.manifestNo(), bagNo) != 0) throw new BusinessException(409, "physically received bag cannot be settled missing");
            if (jdbc.update("UPDATE dispatch_bag SET status='MISSING',transport_task_code=NULL,updated_at=? WHERE bag_no=? AND status='DISPATCHED' AND transport_task_code=?",
                    LocalDateTime.ofInstant(command.closedAt(), ZoneOffset.UTC), bagNo, a.taskCode()) != 1
                    || jdbc.update("UPDATE dispatch_batch_bag SET active_flag=NULL WHERE batch_no=? AND bag_no=? AND active_flag=1", batchNo(a.manifestNo()), bagNo) != 1) throw new BusinessException(409, "missing bag is no longer on this manifest");
        }
        if (assignment.size() == 1) jdbc.update("DELETE FROM dispatch_manifest_assignment WHERE manifest_no=? AND assignment_no=?", a.manifestNo(), a.assignmentNo());
        var ack = new ManifestClosure.Ack(command, "DISPATCH", Instant.now().truncatedTo(ChronoUnit.MILLIS));
        jdbc.update("INSERT INTO dispatch_manifest_close_inbox (manifest_no,request_no,payload_json,receipt_json,confirmed_at) VALUES (?,?,?,?,?)",
                a.manifestNo(), command.requestNo(), write(command), write(ack), LocalDateTime.ofInstant(ack.confirmedAt(), ZoneOffset.UTC));
        return ack;
    }

    private void lockBatch(String no) {
        if (jdbc.queryForList("SELECT batch_no FROM dispatch_batch WHERE manifest_no=? FOR UPDATE", no).size() != 1) {
            throw new BusinessException(404, "manifest not found");
        }
    }

    private String batchNo(String manifestNo) {
        return jdbc.queryForObject("SELECT batch_no FROM dispatch_batch WHERE manifest_no=?", String.class, manifestNo);
    }

    private ManifestAllocation.Snapshot sealedSnapshot(String no) {
        var seals = jdbc.queryForList("SELECT version_no,receipt_json FROM dispatch_manifest_outbox WHERE manifest_no=? AND event_type='SEAL' AND status='SENT' ORDER BY version_no DESC LIMIT 1", no);
        if (seals.isEmpty()) throw new BusinessException(409, "sealed receipt is required");
        int version = ((Number) seals.get(0).get("version_no")).intValue();
        try {
            var seal = json.readTree((String) seals.get(0).get("receipt_json"));
            var payload = json.readTree(jdbc.queryForObject("SELECT payload_json FROM dispatch_manifest_outbox WHERE manifest_no=? AND version_no=? AND event_type='PREALERT' AND status='SENT'", String.class, no, version));
            String origin = payload.path("originNode").asText(), destination = payload.path("targetNode").asText();
            String hash = seal.path("contentHash").asText();
            if (!hash.matches("[0-9a-f]{64}") || !no.equals(seal.path("manifestNo").asText()) || version != seal.path("versionNo").asInt()) {
                throw new BusinessException(409, "invalid sealed receipt");
            }
            var rows = jdbc.queryForList("SELECT b.id,b.bag_no,COALESCE(d.source_org_code,b.origin_facility_code) origin_facility_code,COALESCE(d.destination_org_code,b.destination_facility_code) destination_facility_code,COALESCE(d.facility_route_code,b.facility_route_code) facility_route_code FROM dispatch_bag b "
                    + "JOIN dispatch_batch_bag bb ON bb.bag_no=b.bag_no JOIN dispatch_batch d ON d.batch_no=bb.batch_no WHERE d.manifest_no=? ORDER BY b.bag_no FOR UPDATE", no);
            var bags = new ArrayList<ManifestAllocation.Bag>();
            String route = null;
            for (var row : rows) {
                String bagNo = (String) row.get("bag_no"), edge = (String) row.get("facility_route_code");
                if (edge == null || edge.isBlank() || (route != null && !route.equals(edge)) || !origin.equals(row.get("origin_facility_code")) || !destination.equals(row.get("destination_facility_code"))) {
                    throw new BusinessException(409, "manifest bags must share a facility route and endpoints");
                }
                route = edge;
                var matches = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                for (var p : payload.path("totalPackages")) if (bagNo.equals(p.path("packageNo").asText())) matches.add(p);
                if (matches.size() != 1) throw new BusinessException(409, "bag differs from sealed manifest");
                var p = matches.get(0);
                var mails = new ArrayList<String>();
                for (var item : p.path("items")) mails.add(item.path("itemNo").asText());
                Integer weight = p.path("packageWeight").isNumber() ? p.path("packageWeight").decimalValue().intValueExact() : null;
                bags.add(new ManifestAllocation.Bag(((Number) row.get("id")).longValue(), bagNo, weight, List.copyOf(mails)));
            }
            if (bags.isEmpty() || bags.size() != payload.path("totalPackages").size()) throw new BusinessException(409, "sealed bag membership differs");
            return new ManifestAllocation.Snapshot(no, version, hash, origin, destination, route, List.copyOf(bags));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private boolean sameAssignment(ManifestAllocation.Command a, ManifestAllocation.Command b) {
        return a.manifestNo().equals(b.manifestNo()) && a.versionNo() == b.versionNo() && a.contentHash().equals(b.contentHash()) && a.taskCode().equals(b.taskCode());
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
