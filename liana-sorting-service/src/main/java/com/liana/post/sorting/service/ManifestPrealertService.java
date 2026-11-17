package com.liana.post.sorting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.TotalPackageDTO;
import com.liana.post.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Serializes changes per manifest. Historical snapshots are separate from mutable receiving projections. */
@Service
public class ManifestPrealertService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ManifestPrealertService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Item(String itemNo, int seqNo, String routeCode, BigDecimal weight) { }
    public record Package(String packageNo, String packageLevel, String routeCode,
                          BigDecimal packageWeight, List<Item> items) { }
    public record Snapshot(String manifestCode, String batchNo, int versionNo, String originNode,
                           String targetNode, BigDecimal totalWeight, List<Package> totalPackages) { }
    public record Version(String manifestNo, int versionNo, String contentHash, int expectedPackageQty,
                          int expectedItemQty, JsonNode snapshot, java.time.Instant recordedAt) { }

    @Transactional
    public void accept(ManifestDTO request) {
        Snapshot snapshot = normalize(request);
        String payload = encode(snapshot);
        String hash = hash(payload);
        String no = snapshot.manifestCode();
        // Upsert acquires the same unique-key lock for first creation and duplicate deliveries.
        jdbc.update("INSERT INTO sorting_manifest (manifest_no,manifest_type,source_org_code,destination_org_code,"
                        + "batch_no,manifest_status,prealert_flag,version_no) VALUES (?,'TRANSIT',?,?,?,'PREALERT',1,0) "
                        + "ON DUPLICATE KEY UPDATE manifest_no=manifest_no",
                no, snapshot.originNode(), snapshot.targetNode(), snapshot.batchNo());
        Map<String, Object> current = jdbc.queryForMap("SELECT * FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", no);
        int version = ((Number) current.get("version_no")).intValue();
        List<String> previous = jdbc.query("SELECT content_hash FROM sorting_manifest_version WHERE manifest_no=? AND version_no=?",
                (rs, row) -> rs.getString(1), no, snapshot.versionNo());
        if (!previous.isEmpty()) {
            if (!previous.get(0).equals(hash)) conflict("same manifest version has different content");
            return; // A delayed retry never resets lifecycle, receipts or the current version.
        }
        if (snapshot.versionNo() != version + 1) conflict("manifest version must follow current version " + version);
        if (!snapshot.originNode().equals(current.get("source_org_code"))
                || !snapshot.targetNode().equals(current.get("destination_org_code"))
                || !snapshot.batchNo().equals(current.get("batch_no"))) {
            conflict("handoff endpoints and source batch cannot change; create a new manifest");
        }
        if (!Set.of("DRAFT", "PREALERT", "PREALERTED").contains(current.get("manifest_status"))) {
            conflict("manifest contents are locked after sealing or receiving starts");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_seal WHERE manifest_no=?", Long.class, no) > 0) {
            conflict("manifest has an immutable seal receipt");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_cancel WHERE manifest_no=?", Long.class, no) > 0) {
            conflict("manifest has an immutable cancellation receipt");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_package WHERE manifest_no=? "
                        + "AND (item_status<>'EXPECTED' OR received_at IS NOT NULL)", Long.class, no) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_item WHERE manifest_no=? AND item_status<>'EXPECTED'",
                        Long.class, no) > 0
                || jdbc.queryForObject("SELECT COUNT(*) FROM package_item_line WHERE manifest_no=?", Long.class, no) > 0) {
            conflict("manifest already has processing facts");
        }
        if (version > 0 && jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_version WHERE manifest_no=? AND version_no=?",
                Long.class, no, version) != 1) conflict("current manifest version is missing its immutable snapshot");
        // No receipt facts exist. Replace only the expected projection, including removed rows.
        jdbc.update("DELETE FROM sorting_manifest_item WHERE manifest_no=?", no);
        jdbc.update("DELETE FROM sorting_manifest_package WHERE manifest_no=?", no);
        int packageSeq = 0;
        int itemQty = 0;
        for (Package pack : snapshot.totalPackages()) {
            jdbc.update("INSERT INTO sorting_manifest_package (manifest_no,package_no,expected_seq_no,expected_weight,item_status) "
                            + "VALUES (?,?,?,?,'EXPECTED')", no, pack.packageNo(), ++packageSeq, pack.packageWeight());
            for (Item item : pack.items()) {
                jdbc.update("INSERT INTO sorting_manifest_item (manifest_no,item_no,expected_package_no,expected_seq_no,"
                                + "expected_route_code,expected_qty,item_status) VALUES (?,?,?,?,?,1,'EXPECTED')",
                        no, item.itemNo(), pack.packageNo(), item.seqNo(), item.routeCode());
                itemQty++;
            }
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO sorting_manifest_version (manifest_no,version_no,content_hash,expected_package_qty,"
                        + "expected_item_qty,snapshot_json,recorded_at) VALUES (?,?,?,?,?,?,?)", no, snapshot.versionNo(), hash,
                snapshot.totalPackages().size(), itemQty, payload, now);
        jdbc.update("UPDATE sorting_manifest SET version_no=?,content_hash=?,expected_package_qty=?,expected_item_qty=?,"
                        + "updated_at=? WHERE manifest_no=?", snapshot.versionNo(), hash, snapshot.totalPackages().size(), itemQty, now, no);
    }

    public List<Version> versions(String manifestNo) {
        String no = code(manifestNo, 64, "manifestCode");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest WHERE manifest_no=?", Long.class, no) == 0) {
            throw new BusinessException(404, "manifest not found");
        }
        return jdbc.query("SELECT * FROM sorting_manifest_version WHERE manifest_no=? ORDER BY version_no",
                (rs, row) -> new Version(rs.getString("manifest_no"), rs.getInt("version_no"), rs.getString("content_hash"),
                        rs.getInt("expected_package_qty"), rs.getInt("expected_item_qty"), decode(rs.getString("snapshot_json")),
                        rs.getObject("recorded_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), no);
    }

    public void requireSealed(String manifestNo) {
        if (!isSealed(manifestNo)) {
            throw new BusinessException(409, "manifest must be sealed before receiving");
        }
    }

    public boolean isSealed(String manifestNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_seal s JOIN sorting_manifest m ON m.manifest_no=s.manifest_no "
                        + "AND m.version_no=s.version_no AND m.content_hash=s.content_hash WHERE s.manifest_no=? AND m.manifest_status<>'CANCELLED' "
                        + "AND NOT EXISTS (SELECT 1 FROM sorting_manifest_cancel c WHERE c.manifest_no=m.manifest_no)",
                Long.class, manifestNo) == 1;
    }

    private Snapshot normalize(ManifestDTO request) {
        if (request == null) throw new BusinessException(400, "manifest is required");
        String no = code(request.getManifestCode(), 64, "manifestCode");
        String batch = code(request.getBatchNo(), 64, "batchNo");
        String origin = code(request.getOriginNode(), 32, "originNode");
        String target = code(request.getTargetNode(), 32, "targetNode");
        if (origin.equals(target)) throw new BusinessException(400, "handoff endpoints must differ");
        if (request.getVersionNo() == null || request.getVersionNo() < 1) throw new BusinessException(400, "positive versionNo is required");
        if (request.getTotalPackages() == null || request.getTotalPackages().isEmpty()) throw new BusinessException(400, "manifest must contain packages");
        Set<String> packageNos = new HashSet<>();
        Set<String> itemNos = new HashSet<>();
        List<Package> packages = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean allWeightsKnown = true;
        for (TotalPackageDTO pack : request.getTotalPackages()) {
            if (pack == null) throw new BusinessException(400, "null package is invalid");
            String packageNo = code(pack.getPackageNo(), 64, "packageNo");
            if (!packageNos.add(packageNo)) throw new BusinessException(400, "duplicate package: " + packageNo);
            BigDecimal weight = pack.getPackageWeight() == null ? null : weight(pack.getPackageWeight());
            if (weight == null) allWeightsKnown = false;
            else total = total.add(weight);
            List<Item> items = new ArrayList<>();
            Set<Integer> sequences = new HashSet<>();
            for (TotalPackageDTO.ManifestItemDTO item : pack.getItems() == null ? List.<TotalPackageDTO.ManifestItemDTO>of() : pack.getItems()) {
                if (item == null) throw new BusinessException(400, "null item is invalid");
                String itemNo = code(item.getItemNo(), 64, "itemNo");
                if (!itemNos.add(itemNo)) throw new BusinessException(400, "mail item occurs more than once: " + itemNo);
                int sequence = item.getSeqNo() == null ? items.size() + 1 : item.getSeqNo();
                if (sequence < 1 || !sequences.add(sequence)) throw new BusinessException(400, "invalid or duplicate item sequence");
                items.add(new Item(itemNo, sequence, optionalCode(item.getRouteCode(), 64), item.getWeight() == null ? null : weight(item.getWeight())));
            }
            items.sort(java.util.Comparator.comparingInt(Item::seqNo));
            packages.add(new Package(packageNo, optionalCode(pack.getPackageLevel(), 16), optionalCode(pack.getRouteCode(), 64), weight, List.copyOf(items)));
        }
        if (request.getTotalWeight() != null && (!allWeightsKnown || total.compareTo(weight(request.getTotalWeight())) != 0)) {
            throw new BusinessException(400, "total weight must equal package weights");
        }
        return new Snapshot(no, batch, request.getVersionNo(), origin, target,
                allWeightsKnown ? total.stripTrailingZeros() : null, List.copyOf(packages));
    }

    private String code(String value, int max, String field) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_-]{1," + max + "}")) throw new BusinessException(400, "invalid " + field);
        return value.trim().toUpperCase(Locale.ROOT);
    }
    private String optionalCode(String value, int max) { return value == null || value.isBlank() ? null : code(value, max, "code"); }
    private BigDecimal weight(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 3 || value.compareTo(new BigDecimal("999999999.999")) > 0) {
            throw new BusinessException(400, "weight must be nonnegative grams with at most three decimals");
        }
        return value.stripTrailingZeros();
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode manifest snapshot", e); }
    }
    private JsonNode decode(String value) {
        try { return json.readTree(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored manifest snapshot", e); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void conflict(String message) { throw new BusinessException(409, message); }
}
