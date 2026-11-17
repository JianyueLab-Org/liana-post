package com.liana.post.sorting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.sorting.model.dto.SortingLineResponse;
import com.liana.post.sorting.model.dto.SortingUnpackItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Local opening facts and OMS delivery intent commit together. No HTTP runs in this transaction. */
@Service
public class UnpackingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ManifestReceivingService receiving;
    private final HttpServletRequest http;
    private final TransactionTemplate tx;
    public record Request(String manifestNo, String bagNo, String facilityCode, String actorId,
            String scanBatchNo, String deviceId, List<String> mailNos) { }
    public record Receipt(String requestNo, UnpackingConfirmation command, String status, String lastError,
            Instant confirmedAt, List<SortingLineResponse> lines) { }
    public UnpackingService(JdbcTemplate jdbc, ObjectMapper json, ManifestReceivingService receiving,
            HttpServletRequest http, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.receiving = receiving; this.http = http;
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public List<SortingLineResponse> unpack(SortingUnpackItemRequest input) {
        if (input == null || !"PREALERT".equalsIgnoreCase(input.getScanMode()) || input.getItems() == null || input.getItems().size() > 10000) {
            throw new BusinessException(400, "complete simulated scan against a sealed manifest; empty scans are allowed");
        }
        String facility = code(input.getStationCode(), 32), actor = actor(facility);
        String key = code(input.getIdempotencyKey(), 64);
        var mailNos = input.getItems().stream().map(item -> {
            if (item == null) throw new BusinessException(400, "null scan entry");
            return code(item.getItemNo(), 32);
        }).sorted().toList();
        if (new HashSet<>(mailNos).size() != mailNos.size()) throw new BusinessException(400, "duplicate scanned mail");
        var request = new Request(code(input.getManifestNo(), 64), code(input.getPackageNo(), 32), facility, actor,
                optional(input.getScanBatchNo()), optional(input.getDeviceId()), mailNos);
        String payload = write(request), hash = hash(payload);
        var prior = replay(key, hash);
        if (prior != null) return prior;
        try { return tx.execute(s -> save(key, hash, payload, request)); }
        catch (DuplicateKeyException e) { throw new BusinessException(409, "opening already recorded; retry the original request"); }
    }

    private List<SortingLineResponse> save(String key, String hash, String payload, Request r) {
        var manifests = jdbc.queryForList("SELECT * FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", r.manifestNo());
        if (manifests.size() != 1) throw new BusinessException(404, "manifest not found");
        var prior = replay(key, hash);
        if (prior != null) return prior;
        var manifest = manifests.get(0);
        if (!r.facilityCode().equals(manifest.get("destination_org_code"))
                || !Set.of("SEALED", "RECEIVING", "CHECKED", "DISCREPANCY").contains(manifest.get("manifest_status"))
                || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_seal WHERE manifest_no=? AND version_no=? AND content_hash=?", Long.class,
                        r.manifestNo(), manifest.get("version_no"), manifest.get("content_hash")) != 1) {
            throw new BusinessException(409, "opening requires this facility's sealed receiving manifest");
        }
        var lock = new SortingUnpackItemRequest(); lock.setPackageNo(r.bagNo()); lock.setStationCode(r.facilityCode());
        receiving.lockForUnpack(lock, r.manifestNo());
        var bags = jdbc.queryForList("SELECT * FROM sorting_total_package WHERE package_no=? FOR UPDATE", r.bagNo());
        if (bags.size() != 1 || !"RECEIVED".equals(bags.get(0).get("package_status"))
                || !r.manifestNo().equals(bags.get(0).get("manifest_no"))
                || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_package WHERE manifest_no=? AND package_no=? AND item_status='RECEIVED'", Long.class, r.manifestNo(), r.bagNo()) != 1) {
            throw new BusinessException(409, "bag must actually be received before opening");
        }
        var expected = new TreeSet<>(jdbc.query("SELECT item_no FROM sorting_manifest_item WHERE manifest_no=? AND expected_package_no=? ORDER BY item_no",
                (rs, n) -> code(rs.getString(1), 32), r.manifestNo(), r.bagNo()));
        var actual = new HashSet<>(r.mailNos());
        var matched = r.mailNos().stream().filter(expected::contains).toList();
        String event = id();
        Instant occurred = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var at = LocalDateTime.ofInstant(occurred, ZoneOffset.UTC);
        var command = new UnpackingConfirmation(event, r.manifestNo(), r.bagNo(), r.facilityCode(), r.actorId(), occurred, matched).normalized();
        jdbc.update("INSERT INTO sorting_unpack_request (request_no,event_no,manifest_no,bag_no,facility_code,actor_id,request_hash,request_json,response_json,payload_json,next_attempt_at,occurred_at,recorded_at) VALUES (?,?,?,?,?,?,?,?,'[]',?,?,?,?)",
                key, event, r.manifestNo(), r.bagNo(), r.facilityCode(), r.actorId(), hash, payload, write(command), at, at, at);
        var lines = new ArrayList<SortingLineResponse>();
        boolean discrepancy = false;
        for (String no : r.mailNos()) {
            boolean known = expected.contains(no);
            String classification = known ? "EXPECTED" : "SURPLUS";
            jdbc.update("INSERT INTO sorting_unpack_item (event_no,mail_no,classification) VALUES (?,?,?)", event, no, classification);
            var line = new SortingLineResponse();
            line.setBizLineNo(id()); line.setItemNo(no); line.setActionType("UNLOAD"); line.setEventType("UNPACK"); line.setScanMode("PREALERT");
            line.setFromPackageNo(r.bagNo()); line.setManifestNo(r.manifestNo()); line.setScanBatchNo(r.scanBatchNo());
            line.setStationCode(r.facilityCode()); line.setOperatorId(r.actorId()); line.setDeviceId(r.deviceId()); line.setEventTime(at);
            line.setExt(write(Map.of("unpackEventNo", event, "classification", classification)));
            jdbc.update("INSERT INTO package_item_line (biz_line_no,idempotency_key,item_no,action_type,event_type,scan_mode,from_package_no,manifest_no,scan_batch_no,station_code,operator_id,device_id,event_time,ext) VALUES (?,?,?,'UNLOAD','UNPACK','PREALERT',?,?,?,?,?,?,?,?)",
                    line.getBizLineNo(), line.getBizLineNo(), no, r.bagNo(), r.manifestNo(), r.scanBatchNo(), r.facilityCode(), r.actorId(), r.deviceId(), at, line.getExt());
            line.setId(jdbc.queryForObject("SELECT id FROM package_item_line WHERE biz_line_no=?", Long.class, line.getBizLineNo()));
            lines.add(line);
            if (known) jdbc.update("UPDATE sorting_manifest_item SET item_status='RECEIVED' WHERE manifest_no=? AND item_no=?", r.manifestNo(), no);
            else { discrepancy = true; discrepancy(r, event, no, "SURPLUS", 0, 1, at); }
        }
        for (String no : expected) if (!actual.contains(no)) {
            discrepancy = true; discrepancy(r, event, no, "MISSING", 1, 0, at);
            jdbc.update("UPDATE sorting_manifest_item SET item_status='MISSING' WHERE manifest_no=? AND item_no=?", r.manifestNo(), no);
        }
        if (discrepancy) jdbc.update("UPDATE sorting_manifest SET manifest_status='DISCREPANCY',updated_at=? WHERE manifest_no=?", at, r.manifestNo());
        if (jdbc.update("UPDATE sorting_total_package SET package_status='OPENED',opened_at=?,terminal_reason='OPENED',version=version+1,updated_at=? WHERE package_no=? AND package_status='RECEIVED'", at, at, r.bagNo()) != 1) {
            throw new BusinessException(409, "bag changed during opening");
        }
        receiving.opened(r.bagNo());
        jdbc.update("UPDATE sorting_unpack_request SET response_json=? WHERE request_no=?", write(lines), key);
        return lines;
    }

    private void discrepancy(Request r, String event, String mail, String type, int expected, int actual, LocalDateTime at) {
        jdbc.update("INSERT INTO discrepancy_verification (verification_no,manifest_no,package_no,item_no,discrepancy_type,discrepancy_source,expected_qty,actual_qty,evidence,created_at) VALUES (?,?,?,?,?,'MANIFEST',?,?,?,?)",
                id(), r.manifestNo(), r.bagNo(), mail, type, expected, actual, write(Map.of("unpackEventNo", event)), at);
    }

    public Receipt receipt(String requestNo) {
        var rows = jdbc.queryForList("SELECT * FROM sorting_unpack_request WHERE request_no=?", code(requestNo, 64));
        if (rows.isEmpty()) throw new BusinessException(404, "opening request not found");
        var row = rows.get(0); actor((String) row.get("facility_code"));
        try {
            Object timestamp = row.get("confirmed_at");
            LocalDateTime confirmed = timestamp == null ? null : timestamp instanceof LocalDateTime value
                    ? value : ((java.sql.Timestamp) timestamp).toLocalDateTime();
            return new Receipt((String) row.get("request_no"), json.readValue((String) row.get("payload_json"), UnpackingConfirmation.class),
                    (String) row.get("status"), (String) row.get("last_error"), confirmed == null ? null : confirmed.toInstant(ZoneOffset.UTC), lines((String) row.get("response_json")));
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    /** Prevent the old routing/sealing paths from bypassing a pending opening or surplus quarantine. */
    public void requireMailReady(String mailNo, String facility) {
        var rows = jdbc.queryForList("SELECT r.event_no,i.classification,r.status,r.facility_code FROM sorting_unpack_item i JOIN sorting_unpack_request r ON r.event_no=i.event_no WHERE i.mail_no=? ORDER BY r.occurred_at DESC,r.event_no DESC LIMIT 1 FOR UPDATE", code(mailNo, 32));
        if (!rows.isEmpty() && (!"EXPECTED".equals(rows.get(0).get("classification")) || !"SENT".equals(rows.get(0).get("status"))
                || !code(facility, 32).equals(rows.get(0).get("facility_code")))) {
            throw new BusinessException(409, "opening awaits OMS confirmation or scanned mail is quarantined");
        }
        if (!rows.isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM sorting_repack_item WHERE mail_no=? AND unpack_event_no=?", Long.class, code(mailNo, 32), rows.get(0).get("event_no")) != 0) {
            throw new BusinessException(409, "mail already reserved for structured repacking");
        }
    }

    private List<SortingLineResponse> replay(String key, String hash) {
        var rows = jdbc.queryForList("SELECT request_hash,response_json FROM sorting_unpack_request WHERE request_no=?", key);
        if (rows.isEmpty()) return null;
        if (!hash.equals(rows.get(0).get("request_hash"))) throw new BusinessException(409, "opening request key reused with different content");
        return lines((String) rows.get(0).get("response_json"));
    }
    String actor(String facility) {
        String header = http.getHeader("Authorization");
        String token = header != null && header.matches("(?i)^Bearer\\s+\\S+$") ? header.replaceFirst("(?i)^Bearer\\s+", "") : null;
        if (!JwtTokenUtil.validateToken(token)) throw new BusinessException(401, "valid login required");
        var user = JwtTokenUtil.getUserContextFromToken(token);
        if (!"ADMIN".equals(user.getRole()) && !facility.equals(user.getFacilityCode())) throw new BusinessException(403, "opening outside login facility");
        return code(user.getUserId() == null ? user.getUsername() : user.getUserId().toString(), 64);
    }
    private static String code(String value, int max) { return UnpackingConfirmation.code(value, max); }
    private String optional(String value) { return value == null || value.isBlank() ? null : code(value, 64); }
    private String id() { return UUID.randomUUID().toString().toUpperCase(Locale.ROOT); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<SortingLineResponse> lines(String value) { try { return json.readValue(value, new TypeReference<>() { }); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
