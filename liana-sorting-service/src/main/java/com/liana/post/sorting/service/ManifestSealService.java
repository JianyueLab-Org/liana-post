package com.liana.post.sorting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestSealCommand;
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

@Service
public class ManifestSealService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ManifestSealService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Receipt(String requestNo, String manifestNo, int versionNo, String contentHash,
                          String sourceOrgCode, String actorId, Instant sealedAt) { }

    @Transactional
    public Receipt seal(ManifestSealCommand raw) {
        if (raw == null) throw new BusinessException(400, "seal command is required");
        ManifestSealCommand command = raw.normalized();
        String payload;
        String hash;
        try {
            payload = json.writeValueAsString(command);
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        // The prealert writer and receiving path take this same lock.
        List<Map<String, Object>> manifests = jdbc.queryForList(
                "SELECT * FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", command.manifestNo());
        if (manifests.isEmpty()) throw new BusinessException(404, "manifest not found");
        Map<String, Object> manifest = manifests.get(0);
        if (!command.sourceOrgCode().equals(manifest.get("source_org_code"))) {
            throw new BusinessException(403, "only the source facility may seal a manifest");
        }
        List<String> requests = jdbc.query("SELECT request_hash FROM sorting_manifest_seal WHERE request_no=?",
                (rs, row) -> rs.getString(1), command.requestNo());
        if (!requests.isEmpty()) {
            if (!hash.equals(requests.get(0))) conflict("seal request number reused with different content");
            return receipt(command.manifestNo()); // Never rewinds receiving or lifecycle on a late retry.
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_seal WHERE manifest_no=?", Long.class, command.manifestNo()) > 0) {
            conflict("manifest already sealed by another command");
        }
        if (((Number) manifest.get("version_no")).intValue() != command.versionNo()) conflict("seal version is no longer current");
        if (!Set.of("PREALERT", "PREALERTED").contains(manifest.get("manifest_status"))) conflict("manifest is not awaiting sealing");
        List<String> hashes = jdbc.query("SELECT content_hash FROM sorting_manifest_version WHERE manifest_no=? AND version_no=?",
                (rs, row) -> rs.getString(1), command.manifestNo(), command.versionNo());
        if (hashes.size() != 1 || !hashes.get(0).equals(manifest.get("content_hash"))) conflict("immutable version is missing or inconsistent");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_package WHERE manifest_no=? "
                        + "AND (item_status<>'EXPECTED' OR received_at IS NOT NULL)", Long.class, command.manifestNo()) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_item WHERE manifest_no=? AND item_status<>'EXPECTED'",
                        Long.class, command.manifestNo()) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM package_item_line WHERE manifest_no=?", Long.class, command.manifestNo()) > 0) {
            conflict("receiving or unpacking already started");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        try {
            jdbc.update("INSERT INTO sorting_manifest_seal (manifest_no,version_no,request_no,request_hash,request_json,"
                            + "content_hash,source_org_code,actor_id,sealed_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    command.manifestNo(), command.versionNo(), command.requestNo(), hash, payload, hashes.get(0),
                    command.sourceOrgCode(), command.actorId(), now);
        } catch (DuplicateKeyException e) { throw new BusinessException(409, "seal request number already used"); }
        if (jdbc.update("UPDATE sorting_manifest SET manifest_status='SEALED',updated_at=? WHERE manifest_no=? AND version_no=? "
                        + "AND manifest_status IN ('PREALERT','PREALERTED')", now, command.manifestNo(), command.versionNo()) != 1) {
            conflict("manifest changed while sealing");
        }
        return receipt(command.manifestNo());
    }

    public Receipt receipt(String manifestNo) {
        List<Receipt> receipts = jdbc.query("SELECT * FROM sorting_manifest_seal WHERE manifest_no=?", (rs, row) ->
                new Receipt(rs.getString("request_no"), rs.getString("manifest_no"), rs.getInt("version_no"),
                        rs.getString("content_hash"), rs.getString("source_org_code"), rs.getString("actor_id"),
                        rs.getObject("sealed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), manifestNo);
        return receipts.isEmpty() ? null : receipts.get(0);
    }

    private static void conflict(String message) { throw new BusinessException(409, message); }
}
