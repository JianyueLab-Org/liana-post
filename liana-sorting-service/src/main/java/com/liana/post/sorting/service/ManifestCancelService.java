package com.liana.post.sorting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestCancelCommand;
import com.liana.post.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Consumes only source-service requests; receiver-side processing facts always prohibit cancellation. */
@Service
public class ManifestCancelService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ManifestCancelService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Receipt(String requestNo, String manifestNo, int versionNo, String contentHash,
                          String sourceOrgCode, String actorId, String reason, Instant cancelledAt) { }

    @Transactional
    public Receipt cancel(ManifestCancelCommand raw) {
        if (raw == null) throw new BusinessException(400, "cancel command is required");
        var command = raw.normalized();
        String payload;
        String hash;
        try {
            payload = json.writeValueAsString(command);
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        List<Map<String, Object>> manifests = jdbc.queryForList(
                "SELECT * FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", command.manifestNo());
        if (manifests.isEmpty()) throw new BusinessException(404, "manifest not found");
        Map<String, Object> manifest = manifests.get(0);
        if (!command.sourceOrgCode().equals(manifest.get("source_org_code"))) {
            throw new BusinessException(403, "only the source facility may cancel a manifest");
        }
        List<String> previous = jdbc.query("SELECT request_hash FROM sorting_manifest_cancel WHERE request_no=?",
                (rs, row) -> rs.getString(1), command.requestNo());
        if (!previous.isEmpty()) {
            if (!hash.equals(previous.get(0))) conflict("cancellation request reused with different content");
            return receipt(command.manifestNo());
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_cancel WHERE manifest_no=?", Long.class, command.manifestNo()) > 0) {
            conflict("manifest already cancelled by another command");
        }
        if (((Number) manifest.get("version_no")).intValue() != command.versionNo()) conflict("cancellation version is no longer current");
        if (!Set.of("PREALERT", "PREALERTED", "SEALED").contains(manifest.get("manifest_status"))) {
            conflict("manifest cannot be cancelled after processing or departure");
        }
        var hashes = jdbc.query("SELECT content_hash FROM sorting_manifest_version WHERE manifest_no=? AND version_no=?",
                (rs, row) -> rs.getString(1), command.manifestNo(), command.versionNo());
        if (hashes.size() != 1 || !hashes.get(0).equals(manifest.get("content_hash"))) conflict("immutable version is missing or inconsistent");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_package WHERE manifest_no=? "
                        + "AND (item_status<>'EXPECTED' OR received_at IS NOT NULL)", Long.class, command.manifestNo()) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_item WHERE manifest_no=? AND item_status<>'EXPECTED'",
                        Long.class, command.manifestNo()) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM package_item_line WHERE manifest_no=?", Long.class, command.manifestNo()) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_total_package WHERE manifest_no=? AND "
                        + "(received_at IS NOT NULL OR opened_at IS NOT NULL OR dispatched_at IS NOT NULL)", Long.class, command.manifestNo()) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM discrepancy_verification WHERE manifest_no=?", Long.class, command.manifestNo()) > 0) {
            conflict("manifest already has receiving, departure, unpacking or discrepancy facts");
        }
        var now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        try {
            jdbc.update("INSERT INTO sorting_manifest_cancel (manifest_no,version_no,request_no,request_hash,request_json,"
                            + "content_hash,source_org_code,actor_id,reason,cancelled_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    command.manifestNo(), command.versionNo(), command.requestNo(), hash, payload, hashes.get(0),
                    command.sourceOrgCode(), command.actorId(), command.reason(), now);
        } catch (DuplicateKeyException e) { throw new BusinessException(409, "cancellation request number already used"); }
        if (jdbc.update("UPDATE sorting_manifest SET manifest_status='CANCELLED',updated_at=? WHERE manifest_no=? AND version_no=? "
                        + "AND manifest_status IN ('PREALERT','PREALERTED','SEALED')", now, command.manifestNo(), command.versionNo()) != 1) {
            conflict("manifest changed while cancelling");
        }
        var handoffs = jdbc.queryForList("SELECT bag_no FROM sorting_bag_handoff WHERE manifest_no=? AND status='RESERVED' ORDER BY bag_no", command.manifestNo());
        for (var handoff : handoffs) {
            jdbc.queryForList("SELECT bag_no FROM sorting_bag_position WHERE bag_no=? FOR UPDATE", handoff.get("bag_no"));
            if (jdbc.update("UPDATE sorting_bag_position SET outgoing_manifest_no=NULL,lock_version=lock_version+1,updated_at=? WHERE bag_no=? AND outgoing_manifest_no=? AND container_status='SEALED'",
                    now, handoff.get("bag_no"), command.manifestNo()) != 1) conflict("outgoing custody already changed");
        }
        jdbc.update("UPDATE sorting_bag_handoff SET status='CANCELLED',completed_at=? WHERE manifest_no=? AND status='RESERVED'", now, command.manifestNo());
        return receipt(command.manifestNo());
    }

    public Receipt receipt(String manifestNo) {
        var receipts = jdbc.query("SELECT * FROM sorting_manifest_cancel WHERE manifest_no=?", (rs, row) ->
                new Receipt(rs.getString("request_no"), rs.getString("manifest_no"), rs.getInt("version_no"),
                        rs.getString("content_hash"), rs.getString("source_org_code"), rs.getString("actor_id"), rs.getString("reason"),
                        rs.getObject("cancelled_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), manifestNo);
        return receipts.isEmpty() ? null : receipts.get(0);
    }

    private static void conflict(String message) { throw new BusinessException(409, message); }
}
