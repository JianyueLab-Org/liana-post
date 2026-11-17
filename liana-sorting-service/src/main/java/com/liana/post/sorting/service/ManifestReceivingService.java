package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.util.JwtTokenUtil;
import com.liana.post.sorting.client.TransportReceiptClient;
import com.liana.post.sorting.model.dto.SortingPackageResponse;
import com.liana.post.sorting.model.dto.SortingReceiveRequest;
import com.liana.post.sorting.model.dto.SortingUnpackItemRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
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

/** Expected contents never constitute an actual receipt. HTTP is outside the local transaction. */
@Service
public class ManifestReceivingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransportReceiptClient transport;
    private final TransactionTemplate tx;
    private final HttpServletRequest http;
    private final String token;
    public record Request(String manifestNo, String facilityCode, String actorId, List<String> bagNos) { }

    public ManifestReceivingService(JdbcTemplate jdbc, ObjectMapper json, TransportReceiptClient transport,
            PlatformTransactionManager manager, HttpServletRequest http, @Value("${manifest.internal-token:}") String token) {
        this.jdbc = jdbc; this.json = json; this.transport = transport;
        this.tx = new TransactionTemplate(manager); this.http = http; this.token = token;
        this.tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public SortingPackageResponse receive(SortingReceiveRequest input) {
        var user = user();
        if (input == null || !"PREALERT".equalsIgnoreCase(input.getReceiveMode())) {
            throw new BusinessException(409, "receive against a sealed, arrived manifest; blind receipt requires discrepancy handling");
        }
        String no = code(input.getManifestNo()), key = code(input.getIdempotencyKey());
        String station = code(input.getStationCode());
        ownFacility(user, station);
        if (input.getPackageNos() == null || input.getPackageNos().isEmpty()) throw new BusinessException(400, "scanned bags required");
        var bags = input.getPackageNos().stream().map(ManifestReceivingService::code).sorted().toList();
        if (bags.size() != new HashSet<>(bags).size()) throw new BusinessException(400, "duplicate scanned bag");
        var request = new Request(no, station, actor(user), bags);
        String payload = write(request), hash = hash(payload);
        // Replay before any remote lookup, even when transport is temporarily offline after success.
        var replay = replay(key, hash);
        if (replay != null) return replay;
        var result = transport.arrival(no, token);
        if (result == null || !result.isSuccess() || result.getData() == null) throw new BusinessException(409, "arrived transport assignment required");
        var arrival = result.getData();
        try { return tx.execute(s -> save(key, request, payload, hash, arrival)); }
        catch (DuplicateKeyException e) { throw new BusinessException(409, "receipt or bag position already recorded; retry the original request"); }
    }

    private SortingPackageResponse save(String key, Request request, String payload, String hash, ManifestReceipt.Arrival arrival) {
        var manifests = jdbc.queryForList("SELECT * FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", request.manifestNo());
        if (manifests.size() != 1) throw new BusinessException(404, "manifest not found");
        var manifest = manifests.get(0);
        if (!request.facilityCode().equals(manifest.get("destination_org_code"))) throw new BusinessException(403, "only receiving facility may receive");
        var replay = replay(key, hash);
        if (replay != null) return replay;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_check WHERE manifest_no=?", Long.class, request.manifestNo()) != 0) throw new BusinessException(409, "bag check is finished; new receipts are not accepted");
        var seals = jdbc.queryForList("SELECT version_no,content_hash FROM sorting_manifest_seal WHERE manifest_no=?", request.manifestNo());
        if (seals.size() != 1 || !Set.of("SEALED", "RECEIVING", "DISCREPANCY").contains(manifest.get("manifest_status"))) {
            throw new BusinessException(409, "manifest is not awaiting receipt");
        }
        int version = ((Number) seals.get(0).get("version_no")).intValue();
        String contentHash = (String) seals.get(0).get("content_hash");
        var expected = jdbc.query("SELECT package_no FROM sorting_manifest_package WHERE manifest_no=? ORDER BY package_no", (rs, n) -> rs.getString(1), request.manifestNo());
        if (!request.manifestNo().equals(arrival.manifestNo()) || version != arrival.versionNo() || !contentHash.equals(arrival.contentHash())
                || !contentHash.equals(manifest.get("content_hash")) || version != ((Number) manifest.get("version_no")).intValue()
                || !request.facilityCode().equals(arrival.destinationOrgCode()) || !Objects.equals(manifest.get("source_org_code"), arrival.sourceOrgCode())
                || arrival.bagNos() == null || arrival.bagNos().size() != expected.size()
                || !new TreeSet<>(arrival.bagNos()).equals(new TreeSet<>(expected)) || !expected.containsAll(request.bagNos())) {
            throw new BusinessException(409, "arrival differs from sealed manifest or scanned bags");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime at = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        var events = request.bagNos().stream().map(bag -> new ManifestReceipt.Event(id(), key, request.manifestNo(), version, contentHash,
                arrival.assignmentNo(), arrival.taskCode(), bag, arrival.sourceOrgCode(), request.facilityCode(), request.actorId(), now).normalized()).toList();
        var response = new SortingPackageResponse();
        response.setPackageNo(request.bagNos().get(0)); response.setManifestNo(request.manifestNo());
        response.setPackageLevel("TRANSIT"); response.setPackageStatus("RECEIVED"); response.setPrealertFlag(1);
        response.setSourceOrgCode(arrival.sourceOrgCode()); response.setDestinationOrgCode(request.facilityCode()); response.setReceivedAt(at);
        response.setReceiveRequestNo(key); response.setReceiptEvents(events);
        jdbc.update("INSERT INTO sorting_receive_request (request_no,manifest_no,version_no,content_hash,request_hash,request_json,receipt_json,received_at) VALUES (?,?,?,?,?,?,?,?)",
                key, request.manifestNo(), version, contentHash, hash, payload, write(response), at);
        for (var event : events) {
            var positions = jdbc.queryForList("SELECT * FROM sorting_bag_position WHERE bag_no=? FOR UPDATE", event.bagNo());
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_bag_receipt WHERE manifest_no=? AND bag_no=?", Long.class, request.manifestNo(), event.bagNo()) != 0) {
                throw new BusinessException(409, "bag has already been received; use its original request");
            }
            var packages = jdbc.queryForList("SELECT * FROM sorting_total_package WHERE package_no=? FOR UPDATE", event.bagNo());
            if (positions.isEmpty() && !packages.isEmpty()) throw new BusinessException(409, "existing package has no supported custody history");
            if (!positions.isEmpty()) {
                var position = positions.get(0);
                if (packages.size() != 1 || !"SEALED".equals(position.get("container_status"))
                        || !event.sourceOrgCode().equals(position.get("facility_code"))
                        || !event.manifestNo().equals(position.get("outgoing_manifest_no"))
                        || jdbc.queryForObject("SELECT COUNT(*) FROM sorting_bag_handoff h JOIN sorting_forward_request r ON r.request_no=h.request_no WHERE h.manifest_no=? AND h.bag_no=? AND h.previous_event_no=? AND h.source_org_code=? AND h.destination_org_code=? AND h.status='RESERVED' AND r.status='SENT'",
                                Long.class, event.manifestNo(), event.bagNo(), position.get("last_event_no"), event.sourceOrgCode(), event.destinationOrgCode()) != 1) {
                    throw new BusinessException(409, "next receipt requires this bag's confirmed outgoing handoff");
                }
            }
            jdbc.update("INSERT INTO sorting_bag_receipt (event_no,request_no,manifest_no,version_no,bag_no,assignment_no,task_code,source_org_code,destination_org_code,actor_id,received_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    event.eventNo(), key, event.manifestNo(), version, event.bagNo(), event.assignmentNo(), event.taskCode(), event.sourceOrgCode(), event.destinationOrgCode(), event.actorId(), at);
            if (positions.isEmpty()) {
                jdbc.update("INSERT INTO sorting_bag_position (bag_no,facility_code,last_manifest_no,last_event_no,container_status,updated_at) VALUES (?,?,?,?,'SEALED',?)",
                        event.bagNo(), request.facilityCode(), event.manifestNo(), event.eventNo(), at);
                jdbc.update("INSERT INTO sorting_total_package (package_no,package_level,package_status,source_org_code,destination_org_code,manifest_no,prealert_flag,received_at,created_at,updated_at) VALUES (?,'TRANSIT','RECEIVED',?,?,?,1,?,?,?)",
                        event.bagNo(), event.sourceOrgCode(), event.destinationOrgCode(), event.manifestNo(), at, at, at);
            } else {
                if (jdbc.update("UPDATE sorting_bag_position SET facility_code=?,last_manifest_no=?,last_event_no=?,outgoing_manifest_no=NULL,lock_version=lock_version+1,updated_at=? WHERE bag_no=? AND outgoing_manifest_no=? AND container_status='SEALED'",
                        request.facilityCode(), event.manifestNo(), event.eventNo(), at, event.bagNo(), event.manifestNo()) != 1) throw new BusinessException(409, "outgoing custody changed");
                jdbc.update("UPDATE sorting_total_package SET package_status='RECEIVED',source_org_code=?,destination_org_code=?,manifest_no=?,received_at=?,updated_at=? WHERE package_no=?",
                        event.sourceOrgCode(), event.destinationOrgCode(), event.manifestNo(), at, at, event.bagNo());
                if (jdbc.update("UPDATE sorting_bag_handoff SET status='RECEIVED',completed_at=? WHERE manifest_no=? AND bag_no=? AND status='RESERVED'", at, event.manifestNo(), event.bagNo()) != 1) throw new BusinessException(409, "outgoing handoff changed");
            }
            if (jdbc.update("UPDATE sorting_manifest_package SET item_status='RECEIVED',received_at=? WHERE manifest_no=? AND package_no=? AND item_status='EXPECTED' AND received_at IS NULL", at, event.manifestNo(), event.bagNo()) != 1) {
                throw new BusinessException(409, "expected bag projection already changed");
            }
            for (String destination : List.of("TRANSPORT", "DISPATCH")) {
                jdbc.update("INSERT INTO sorting_receipt_outbox (event_no,destination,payload_json,next_attempt_at,created_at) VALUES (?,?,?,?,?)", event.eventNo(), destination, write(event), at, at);
            }
        }
        boolean complete = jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_package WHERE manifest_no=? AND item_status<>'RECEIVED'", Long.class, request.manifestNo()) == 0;
        jdbc.update("UPDATE sorting_manifest SET manifest_status=?,updated_at=? WHERE manifest_no=?",
                "DISCREPANCY".equals(manifest.get("manifest_status")) ? "DISCREPANCY" : complete ? "CHECKED" : "RECEIVING", at, request.manifestNo());
        return response;
    }

    private SortingPackageResponse replay(String key, String hash) {
        var rows = jdbc.queryForList("SELECT request_hash,receipt_json FROM sorting_receive_request WHERE request_no=?", key);
        if (rows.isEmpty()) return null;
        if (!hash.equals(rows.get(0).get("request_hash"))) throw new BusinessException(409, "receive request key reused with different content");
        return read((String) rows.get(0).get("receipt_json"), SortingPackageResponse.class);
    }

    public boolean isCheckFinished(String manifestNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sorting_manifest_check WHERE manifest_no=?", Long.class, code(manifestNo)) != 0;
    }

    /** Caller has locked the manifest. Keep the position lock through the existing simulated unpack transaction. */
    public void lockForUnpack(SortingUnpackItemRequest request, String manifestNo) {
        var user = user();
        String station = code(request.getStationCode()); ownFacility(user, station);
        var rows = jdbc.queryForList("SELECT * FROM sorting_bag_position WHERE bag_no=? FOR UPDATE", code(request.getPackageNo()));
        if (rows.size() != 1 || !station.equals(rows.get(0).get("facility_code")) || !Objects.equals(manifestNo, rows.get(0).get("last_manifest_no"))
                || !"SEALED".equals(rows.get(0).get("container_status")) || rows.get(0).get("outgoing_manifest_no") != null) throw new BusinessException(409, "unpack requires unreserved sealed custody at this facility");
        request.setOperatorId(actor(user));
    }

    public void opened(String bagNo) {
        if (jdbc.update("UPDATE sorting_bag_position SET container_status='OPENED',lock_version=lock_version+1,updated_at=? WHERE bag_no=? AND container_status='SEALED' AND outgoing_manifest_no IS NULL",
                LocalDateTime.now(ZoneOffset.UTC), code(bagNo)) != 1) throw new BusinessException(409, "bag position changed while unpacking");
    }

    public List<Map<String, Object>> history(String manifestNo) {
        return jdbc.query("SELECT r.*,p.facility_code,p.container_status,o.destination,o.status,o.attempt_count,o.last_error,o.confirmed_at "
                + "FROM sorting_bag_receipt r LEFT JOIN sorting_bag_position p ON p.bag_no=r.bag_no JOIN sorting_receipt_outbox o ON o.event_no=r.event_no "
                + "WHERE r.manifest_no=? ORDER BY r.received_at,r.bag_no,o.destination", (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String field : List.of("event_no","request_no","bag_no","assignment_no","task_code","facility_code","container_status","destination","status","last_error")) row.put(field, rs.getString(field));
            row.put("attempt_count", rs.getInt("attempt_count"));
            for (String field : List.of("received_at","confirmed_at")) row.put(field, rs.getTimestamp(field) == null ? null : rs.getTimestamp(field).toLocalDateTime().toInstant(ZoneOffset.UTC));
            return row;
        }, code(manifestNo));
    }

    private JwtUserContext user() {
        String header = http.getHeader("Authorization");
        String bearer = header != null && header.matches("(?i)^Bearer\\s+\\S+$") ? header.replaceFirst("(?i)^Bearer\\s+", "") : null;
        if (!JwtTokenUtil.validateToken(bearer)) throw new BusinessException(401, "valid login required");
        return JwtTokenUtil.getUserContextFromToken(bearer);
    }
    private void ownFacility(JwtUserContext user, String station) {
        if (!"ADMIN".equals(user.getRole()) && !station.equals(user.getFacilityCode())) throw new BusinessException(403, "receiving facility is outside login scope");
    }
    private String actor(JwtUserContext user) { return user.getUserId() == null ? user.getUsername() : user.getUserId().toString(); }
    private static String code(String value) { return ManifestAllocation.code(value); }
    private String id() { return UUID.randomUUID().toString().toUpperCase(Locale.ROOT); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
