package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.BagForwarding;
import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Reserves sealed custody and the durable dispatch command in one sorting transaction. */
@Service
public class BagForwardingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HttpServletRequest http;
    public record Request(String requestNo, String previousManifestNo, String facilityCode,
                          String destinationOrgCode, String facilityRouteCode, List<String> bagNos) { }
    public record State(BagForwarding.Command command, String status, String lastError) { }
    public record Candidate(String bagNo, String manifestNo) { }

    public BagForwardingService(JdbcTemplate jdbc, ObjectMapper json, HttpServletRequest http) {
        this.jdbc = jdbc; this.json = json; this.http = http;
    }

    @Transactional
    public State prepare(Request input) {
        if (input == null || input.bagNos() == null || input.bagNos().isEmpty()) throw new BusinessException(400, "received bags required");
        var user = user();
        String key = code(input.requestNo()), previous = code(input.previousManifestNo()), facility = code(input.facilityCode());
        if (!"ADMIN".equals(user.getRole()) && !facility.equals(user.getFacilityCode())) throw new BusinessException(403, "forwarding facility outside login scope");
        var bags = input.bagNos().stream().map(BagForwardingService::code).sorted().toList();
        if (new HashSet<>(bags).size() != bags.size()) throw new BusinessException(400, "duplicate forwarding bag");
        var request = new Request(key, previous, facility, code(input.destinationOrgCode()), code(input.facilityRouteCode()), bags);
        String actor = user.getUserId() == null ? user.getUsername() : user.getUserId().toString();
        String hash = hash(write(List.of(request, actor)));
        if (jdbc.queryForList("SELECT manifest_no FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", previous).size() != 1) throw new BusinessException(404, "previous manifest not found");
        var replay = jdbc.queryForList("SELECT request_hash FROM sorting_forward_request WHERE request_no=?", key);
        if (!replay.isEmpty()) {
            if (!hash.equals(replay.get(0).get("request_hash"))) throw new BusinessException(409, "forwarding key reused with different content");
            return state(key);
        }
        String manifestNo = "M" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        String batchNo = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        var received = new ArrayList<BagForwarding.Bag>();
        for (String bag : bags) {
            var rows = jdbc.queryForList("SELECT * FROM sorting_bag_position WHERE bag_no=? FOR UPDATE", bag);
            if (rows.size() != 1 || !facility.equals(rows.get(0).get("facility_code"))
                    || !previous.equals(rows.get(0).get("last_manifest_no")) || !"SEALED".equals(rows.get(0).get("container_status"))
                    || rows.get(0).get("outgoing_manifest_no") != null) throw new BusinessException(409, "forwarding requires unreserved sealed custody at this facility");
            String event = (String) rows.get(0).get("last_event_no");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_receipt_outbox WHERE event_no=? AND status='SENT'", Long.class, event) != 2) {
                throw new BusinessException(409, "wait for both transport and dispatch receipt confirmations");
            }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM discrepancy_verification WHERE package_no=? AND status<>'RESOLVED'", Long.class, bag) != 0) {
                throw new BusinessException(409, "resolve bag discrepancies before forwarding");
            }
            received.add(new BagForwarding.Bag(bag, event));
        }
        var command = new BagForwarding.Command(key, previous, manifestNo, batchNo, facility,
                request.destinationOrgCode(), request.facilityRouteCode(), actor, received).normalized();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        try {
            jdbc.update("INSERT INTO sorting_forward_request (request_no,manifest_no,previous_manifest_no,request_hash,payload_json,next_attempt_at,created_at) VALUES (?,?,?,?,?,?,?)",
                    key, command.manifestNo(), previous, hash, write(command), now, now);
        } catch (DuplicateKeyException e) { throw new BusinessException(409, "forwarding request already exists; retry original request"); }
        for (var bag : received) {
            jdbc.update("INSERT INTO sorting_bag_handoff (manifest_no,bag_no,request_no,previous_event_no,source_org_code,destination_org_code,created_at) VALUES (?,?,?,?,?,?,?)",
                    command.manifestNo(), bag.bagNo(), key, bag.receiptEventNo(), facility, command.destinationOrgCode(), now);
            if (jdbc.update("UPDATE sorting_bag_position SET outgoing_manifest_no=?,lock_version=lock_version+1,updated_at=? WHERE bag_no=? AND outgoing_manifest_no IS NULL AND container_status='SEALED'",
                    command.manifestNo(), now, bag.bagNo()) != 1) throw new BusinessException(409, "bag custody changed");
        }
        return state(key);
    }

    public List<State> list(String facilityCode) {
        var user = user(); String facility = code(facilityCode);
        if (!"ADMIN".equals(user.getRole()) && !facility.equals(user.getFacilityCode())) throw new BusinessException(403, "forwarding facility outside login scope");
        return jdbc.query("SELECT DISTINCT r.payload_json,r.status,r.last_error FROM sorting_forward_request r JOIN sorting_bag_handoff h ON h.request_no=r.request_no WHERE h.source_org_code=?",
                (rs, n) -> new State(read(rs.getString(1)), rs.getString(2), rs.getString(3)), facility);
    }
    public List<Candidate> candidates(String facilityCode) {
        var user = user(); String facility = code(facilityCode);
        if (!"ADMIN".equals(user.getRole()) && !facility.equals(user.getFacilityCode())) throw new BusinessException(403, "forwarding facility outside login scope");
        return jdbc.query("SELECT p.bag_no,p.last_manifest_no FROM sorting_bag_position p WHERE p.facility_code=? AND p.container_status='SEALED' AND p.outgoing_manifest_no IS NULL "
                        + "AND (SELECT COUNT(*) FROM sorting_receipt_outbox o WHERE o.event_no=p.last_event_no AND o.status='SENT')=2 "
                        + "AND NOT EXISTS (SELECT 1 FROM discrepancy_verification d WHERE d.package_no=p.bag_no AND d.status<>'RESOLVED') ORDER BY p.last_manifest_no,p.bag_no",
                (rs, n) -> new Candidate(rs.getString(1), rs.getString(2)), facility);
    }
    private State state(String key) {
        return jdbc.queryForObject("SELECT payload_json,status,last_error FROM sorting_forward_request WHERE request_no=?",
                (rs, n) -> new State(read(rs.getString(1)), rs.getString(2), rs.getString(3)), key);
    }
    private com.liana.post.common.model.JwtUserContext user() {
        String header = http.getHeader("Authorization");
        String token = header != null && header.matches("(?i)^Bearer\\s+\\S+$") ? header.replaceFirst("(?i)^Bearer\\s+", "") : null;
        if (!JwtTokenUtil.validateToken(token)) throw new BusinessException(401, "valid login required");
        return JwtTokenUtil.getUserContextFromToken(token);
    }
    private static String code(String value) { return ManifestAllocation.code(value); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private BagForwarding.Command read(String value) { try { return json.readValue(value, BagForwarding.Command.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
