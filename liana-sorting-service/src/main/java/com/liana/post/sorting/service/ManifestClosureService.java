package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.dto.sorting.ManifestClosure;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.sorting.client.TransportReceiptClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Manifest row fences receipt, opening, check completion and closure. */
@Service
public class ManifestClosureService {
    public record Request(String requestNo, String facilityCode) { }
    public record Operation(String requestNo, String manifestNo, String facilityCode, String actorId) { }
    public record CheckReceipt(String requestNo, String manifestNo, List<String> receivedBagNos, List<String> missingBagNos, Instant completedAt) { }
    public record DeliveryState(String destination, String status, int attempts, String lastError) { }
    public record State(CheckReceipt check, ManifestClosure.Command command, String status, List<DeliveryState> deliveries) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HttpServletRequest http;
    private final TransportReceiptClient transport;
    private final TransactionTemplate tx;
    private final String token;
    public ManifestClosureService(JdbcTemplate jdbc, ObjectMapper json, HttpServletRequest http, TransportReceiptClient transport,
            PlatformTransactionManager manager, @Value("${manifest.internal-token:}") String token) {
        this.jdbc = jdbc; this.json = json; this.http = http; this.transport = transport; this.token = token;
        tx = new TransactionTemplate(manager); tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public CheckReceipt finishCheck(String manifestNo, Request input) {
        var op = operation(manifestNo, input, false);
        var replay = checkReplay(op); if (replay != null) return replay;
        // Zero actual bags is supported. Remote lookup never runs inside a local business transaction.
        var result = transport.arrival(op.manifestNo(), token);
        if (result == null || !result.isSuccess() || result.getData() == null) throw new BusinessException(409, "arrived transport assignment required");
        try { return tx.execute(s -> finish(op, result.getData())); }
        catch (org.springframework.dao.DuplicateKeyException e) {
            var saved = checkReplay(op); if (saved != null) return saved;
            throw new BusinessException(409, "check request already used; retry original content");
        }
    }
    private CheckReceipt finish(Operation op, ManifestReceipt.Arrival arrival) {
        var manifest = lock(op);
        var replay = checkReplay(op); if (replay != null) return replay;
        if (!Set.of("SEALED", "RECEIVING", "CHECKED", "DISCREPANCY").contains(manifest.get("manifest_status"))) throw new BusinessException(409, "manifest is not awaiting check completion");
        var expected = jdbc.query("SELECT package_no FROM sorting_manifest_package WHERE manifest_no=? ORDER BY package_no", (rs, n) -> rs.getString(1), op.manifestNo());
        var seals = jdbc.queryForList("SELECT version_no,content_hash FROM sorting_manifest_seal WHERE manifest_no=?", op.manifestNo());
        if (seals.size() != 1 || !op.manifestNo().equals(arrival.manifestNo())
                || arrival.versionNo() != ((Number) manifest.get("version_no")).intValue()
                || arrival.versionNo() != ((Number) seals.get(0).get("version_no")).intValue()
                || !Objects.equals(arrival.contentHash(), manifest.get("content_hash")) || !Objects.equals(arrival.contentHash(), seals.get(0).get("content_hash"))
                || !op.facilityCode().equals(arrival.destinationOrgCode()) || !Objects.equals(manifest.get("source_org_code"), arrival.sourceOrgCode())
                || arrival.bagNos() == null || expected.isEmpty() || expected.size() != arrival.bagNos().size()
                || !new TreeSet<>(expected).equals(new TreeSet<>(arrival.bagNos()))) throw new BusinessException(409, "arrival differs from sealed manifest");
        var received = jdbc.query("SELECT bag_no FROM sorting_bag_receipt WHERE manifest_no=? ORDER BY bag_no", (rs, n) -> rs.getString(1), op.manifestNo());
        if (!expected.containsAll(received) || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_bag_receipt WHERE manifest_no=? AND (assignment_no<>? OR task_code<>?)", Long.class,
                op.manifestNo(), arrival.assignmentNo(), arrival.taskCode()) != 0) throw new BusinessException(409, "receipt assignment differs from check");
        var missing = expected.stream().filter(b -> !received.contains(b)).toList();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS); var at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        var receipt = new CheckReceipt(op.requestNo(), op.manifestNo(), received, missing, now);
        jdbc.update("INSERT INTO sorting_manifest_check (manifest_no,request_no,request_json,arrival_json,receipt_json,completed_at) VALUES (?,?,?,?,?,?)",
                op.manifestNo(), op.requestNo(), write(op), write(arrival), write(receipt), at);
        for (String bag : missing) {
            if (jdbc.update("UPDATE sorting_manifest_package SET item_status='MISSING' WHERE manifest_no=? AND package_no=? AND item_status='EXPECTED' AND received_at IS NULL", op.manifestNo(), bag) != 1) throw new BusinessException(409, "missing bag projection changed");
            jdbc.update("INSERT INTO discrepancy_verification (verification_no,manifest_no,package_no,discrepancy_type,discrepancy_source,expected_qty,actual_qty,evidence,created_at) VALUES (?,?,?,'MISSING','MANIFEST',1,0,?,?)",
                    UUID.randomUUID().toString().toUpperCase(Locale.ROOT), op.manifestNo(), bag,
                    write(Map.of("checkRequestNo", op.requestNo(), "assignmentNo", arrival.assignmentNo(), "scope", "BAG")), at);
            // Missing onward bags stay fenced rather than returning to sealed source custody.
            jdbc.queryForList("SELECT bag_no FROM sorting_bag_position WHERE bag_no=? FOR UPDATE", bag);
            jdbc.update("UPDATE sorting_bag_position SET container_status='MISSING',lock_version=lock_version+1,updated_at=? WHERE bag_no=? AND outgoing_manifest_no=?", at, bag, op.manifestNo());
            jdbc.update("UPDATE sorting_bag_handoff SET status='MISSING',completed_at=? WHERE manifest_no=? AND bag_no=? AND status='RESERVED'", at, op.manifestNo(), bag);
        }
        jdbc.update("UPDATE sorting_manifest SET manifest_status=?,updated_at=? WHERE manifest_no=?",
                missing.isEmpty() && !"DISCREPANCY".equals(manifest.get("manifest_status")) ? "CHECKED" : "DISCREPANCY", at, op.manifestNo());
        return receipt;
    }
    public State close(String manifestNo, Request input) {
        var op = operation(manifestNo, input, true);
        try { tx.executeWithoutResult(s -> prepareClose(op)); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new BusinessException(409, "closure request already used; retry original content"); }
        return state(op.manifestNo());
    }
    private void prepareClose(Operation op) {
        var manifest = lock(op);
        var previous = jdbc.queryForList("SELECT request_json FROM sorting_manifest_close WHERE manifest_no=? OR request_no=?", op.manifestNo(), op.requestNo());
        if (!previous.isEmpty()) {
            if (previous.size() != 1 || !op.equals(read((String) previous.get(0).get("request_json"), Operation.class))) throw new BusinessException(409, "reuse original closure request and content");
            return;
        }
        var rows = jdbc.queryForList("SELECT * FROM sorting_manifest_check WHERE manifest_no=?", op.manifestNo());
        if (rows.size() != 1 || !Set.of("CHECKED", "DISCREPANCY").contains(manifest.get("manifest_status"))) throw new BusinessException(409, "explicit check completion required before closure");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM discrepancy_verification WHERE manifest_no=? AND status<>'RESOLVED'", Long.class, op.manifestNo()) != 0) throw new BusinessException(409, "resolve all discrepancies before closure");
        var check = read((String) rows.get(0).get("receipt_json"), CheckReceipt.class);
        for (String bag : check.receivedBagNos()) {
            var receipt = jdbc.queryForMap("SELECT event_no FROM sorting_bag_receipt WHERE manifest_no=? AND bag_no=?", op.manifestNo(), bag);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_receipt_outbox WHERE event_no=? AND status='SENT'", Long.class, receipt.get("event_no")) != 2) throw new BusinessException(409, "wait for both receipt confirmations");
            // Final closure requires each received bag to finish opening or confirmed sealed forwarding.
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_unpack_request WHERE manifest_no=? AND bag_no=? AND status='SENT'", Long.class, op.manifestNo(), bag) == 0
                    && jdbc.queryForObject("SELECT COUNT(*) FROM sorting_bag_handoff h JOIN sorting_forward_request r ON r.request_no=h.request_no WHERE h.previous_event_no=? AND r.status='SENT' AND h.status IN ('RECEIVED','MISSING')", Long.class, receipt.get("event_no")) == 0) throw new BusinessException(409, "finish opening or next-leg receipt/check before closure");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_unpack_request WHERE manifest_no=? AND status<>'SENT'", Long.class, op.manifestNo()) != 0) throw new BusinessException(409, "wait for opening confirmation");
        var command = new ManifestClosure.Command(op.requestNo(), read((String) rows.get(0).get("arrival_json"), ManifestReceipt.Arrival.class),
                check.receivedBagNos(), check.missingBagNos(), op.actorId(), Instant.now().truncatedTo(ChronoUnit.MILLIS)).normalized();
        var at = LocalDateTime.ofInstant(command.closedAt(), ZoneOffset.UTC);
        jdbc.update("INSERT INTO sorting_manifest_close (manifest_no,request_no,request_json,payload_json,created_at) VALUES (?,?,?,?,?)", op.manifestNo(), op.requestNo(), write(op), write(command), at);
        for (String destination : List.of("DISPATCH", "TRANSPORT")) jdbc.update("INSERT INTO sorting_close_outbox (manifest_no,destination,next_attempt_at) VALUES (?,?,?)", op.manifestNo(), destination, at);
        jdbc.update("UPDATE sorting_manifest SET manifest_status='CLOSING',updated_at=? WHERE manifest_no=?", at, op.manifestNo());
    }
    public State state(String manifestNo) {
        String no = ManifestAllocation.code(manifestNo);
        var row = jdbc.queryForList("SELECT source_org_code,destination_org_code,manifest_status FROM sorting_manifest WHERE manifest_no=?", no);
        if (row.size() != 1) throw new BusinessException(404, "manifest not found");
        var user = user();
        if (!"ADMIN".equalsIgnoreCase(user.getRole()) && !Objects.equals(user.getFacilityCode(), row.get(0).get("source_org_code"))
                && !Objects.equals(user.getFacilityCode(), row.get(0).get("destination_org_code"))) throw new BusinessException(403, "closure history belongs to handoff facilities");
        var checks = jdbc.queryForList("SELECT receipt_json FROM sorting_manifest_check WHERE manifest_no=?", no);
        var commands = jdbc.queryForList("SELECT payload_json FROM sorting_manifest_close WHERE manifest_no=?", no);
        var deliveries = jdbc.query("SELECT destination,status,attempt_count,last_error FROM sorting_close_outbox WHERE manifest_no=? ORDER BY destination",
                (rs, n) -> new DeliveryState(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)), no);
        return new State(checks.isEmpty() ? null : read((String) checks.get(0).get("receipt_json"), CheckReceipt.class),
                commands.isEmpty() ? null : read((String) commands.get(0).get("payload_json"), ManifestClosure.Command.class), (String) row.get(0).get("manifest_status"), deliveries);
    }
    private CheckReceipt checkReplay(Operation op) {
        var rows = jdbc.queryForList("SELECT request_json,receipt_json FROM sorting_manifest_check WHERE manifest_no=? OR request_no=?", op.manifestNo(), op.requestNo());
        if (rows.isEmpty()) return null;
        if (rows.size() != 1 || !op.equals(read((String) rows.get(0).get("request_json"), Operation.class))) throw new BusinessException(409, "reuse original check request and content");
        return read((String) rows.get(0).get("receipt_json"), CheckReceipt.class);
    }
    private Map<String,Object> lock(Operation op) {
        var rows = jdbc.queryForList("SELECT * FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", op.manifestNo());
        if (rows.size() != 1) throw new BusinessException(404, "manifest not found");
        if (!op.facilityCode().equals(rows.get(0).get("destination_org_code"))) throw new BusinessException(403, "only receiving facility may finish manifest processing");
        return rows.get(0);
    }
    private Operation operation(String no, Request request, boolean admin) {
        if (request == null) throw new BusinessException(400, "fixed request required");
        String facility = ManifestAllocation.code(request.facilityCode());
        return new Operation(ManifestAllocation.code(request.requestNo()), ManifestAllocation.code(no), facility, authorize(facility, admin));
    }
    private String authorize(String facility, boolean admin) {
        var user = user();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        if (admin && !isAdmin) throw new BusinessException(403, "administrator closure required");
        if (!isAdmin && !facility.equals(user.getFacilityCode())) throw new BusinessException(403, "receiving facility outside login scope");
        return user.getUserId() == null ? user.getUsername() : user.getUserId().toString();
    }
    private com.liana.post.common.model.JwtUserContext user() {
        String header = http.getHeader("Authorization");
        String bearer = header != null && header.matches("(?i)^Bearer\\s+\\S+$") ? header.replaceFirst("(?i)^Bearer\\s+", "") : null;
        if (!JwtTokenUtil.validateToken(bearer)) throw new BusinessException(401, "valid login required");
        return JwtTokenUtil.getUserContextFromToken(bearer);
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
}
