package com.liana.post.sorting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.RepackingCommand;
import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.IdGeneratorUtil;
import com.liana.post.sorting.client.OmsClient;
import com.liana.post.sorting.model.dto.SortingPackageResponse;
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

@Service
public class RepackingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OmsClient oms;
    private final UnpackingService unpacking;
    private final TransactionTemplate tx;
    public record Request(String facilityCode, String actorId, String slotCode, String destinationCode, String sourceBagNo, List<String> mailNos) { }
    public record Receipt(RepackingCommand command, String status, String lastError) { }
    public RepackingService(JdbcTemplate jdbc, ObjectMapper json, OmsClient oms, UnpackingService unpacking, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.oms = oms; this.unpacking = unpacking;
        tx = new TransactionTemplate(manager); tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public SortingPackageResponse reserve(String key, String facility, String slot, String destination, String source, List<String> mails) {
        String station = code(facility, 32), requestNo = code(key, 64);
        var items = mails == null ? List.<String>of() : mails.stream().map(no -> code(no, 32)).sorted().toList();
        if (new HashSet<>(items).size() != items.size()) throw new BusinessException(400, "duplicate repacking mail");
        var request = new Request(station, unpacking.actor(station), optional(slot), optional(destination), optional(source), items);
        if (request.slotCode() == null && (items.isEmpty() || request.destinationCode() == null)) throw new BusinessException(400, "slot or explicit scanned mail and destination required");
        String payload = write(request), hash = hash(payload);
        var replay = replay(requestNo, hash); if (replay != null) return response(replay);
        var selected = items.isEmpty() ? slotItems(request.slotCode(), station) : items;
        if (selected.isEmpty()) throw new BusinessException(409, "no routed mail in this slot");
        // Read weights outside the local transaction. OMS atomically revalidates them during packing confirmation.
        var weights = new HashMap<String, Integer>();
        for (String no : selected) {
            var result = oms.getMail(no);
            var mail = result == null || !result.isSuccess() ? null : result.getData();
            if (mail == null || !station.equals(mail.getCurrentFacilityCode()) || !"SORTED".equals(mail.getStatus())
                    || hasValue(mail.getBagNo()) || hasValue(mail.getPackageId())) throw new BusinessException(409, "mail is not available for repacking: " + no);
            weights.put(no, mail.getWeightGrams());
        }
        try { return tx.execute(s -> save(requestNo, hash, payload, request, selected, weights)); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new BusinessException(409, "mail already reserved; retry the original request"); }
    }
    private SortingPackageResponse save(String key, String hash, String payload, Request r, List<String> selected, Map<String, Integer> weights) {
        // Lock all source manifests first, in the same order as unpack/discrepancy operations.
        var manifests = new TreeSet<String>();
        var scannedEvents = new HashMap<String,String>();
        for (String no : selected) {
            var rows = latestScan(no);
            if (rows.isEmpty()) throw new BusinessException(409, "repacking requires an actual opening scan");
            manifests.add((String) rows.get(0).get("manifest_no"));
            scannedEvents.put(no, (String) rows.get(0).get("event_no"));
        }
        for (String no : manifests) jdbc.queryForList("SELECT manifest_no FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", no);
        var replay = replay(key, hash); if (replay != null) return response(replay);
        if (r.slotCode() != null && r.mailNos().isEmpty() && !selected.equals(slotItems(r.slotCode(), r.facilityCode()))) throw new BusinessException(409, "slot contents changed; refresh before repacking");
        var items = new ArrayList<RepackingCommand.Item>();
        for (String no : selected) {
            var scan = latestScan(no).get(0);
            String event = (String) scan.get("event_no");
            if (!event.equals(scannedEvents.get(no))) throw new BusinessException(409, "mail opening history changed; refresh before repacking");
            jdbc.queryForList("SELECT event_no FROM sorting_unpack_request WHERE event_no=? FOR UPDATE", event);
            unpacking.requireMailReady(no, r.facilityCode());
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sorting_receipt_outbox o JOIN sorting_bag_receipt b ON b.event_no=o.event_no WHERE b.manifest_no=? AND b.bag_no=? AND o.status='SENT'", Long.class, scan.get("manifest_no"), scan.get("bag_no")) != 2) throw new BusinessException(409, "wait for both source bag receipt confirmations before repacking");
            if (r.sourceBagNo() != null && !r.sourceBagNo().equals(scan.get("bag_no"))) throw new BusinessException(409, "mail came from another source bag");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM discrepancy_verification WHERE manifest_no=? AND status='OPEN'", Long.class, scan.get("manifest_no")) != 0) throw new BusinessException(409, "source manifest has unresolved discrepancies");
            var routes = jdbc.queryForList("SELECT * FROM package_item_line WHERE item_no=? ORDER BY event_time DESC,id DESC LIMIT 1", no);
            if (routes.isEmpty() || !"ROUTE".equals(routes.get(0).get("event_type")) || !r.facilityCode().equals(routes.get(0).get("station_code"))
                    || (r.slotCode() != null && !r.slotCode().equals(routes.get(0).get("target_center_code")))) throw new BusinessException(409, "mail must be routed before repacking");
            items.add(new RepackingCommand.Item(no, (String) scan.get("bag_no"), event, weights.get(no)));
        }
        String destination = r.destinationCode();
        if (destination == null) {
            // ROUTE lines store the slot in target_center_code and the selected next hop in ext.
            var destinations = new TreeSet<String>();
            for (String no : selected) {
                String ext = jdbc.queryForObject("SELECT ext FROM package_item_line WHERE item_no=? AND event_type='ROUTE' ORDER BY event_time DESC,id DESC LIMIT 1", String.class, no);
                try { destinations.add(code(json.readTree(ext).path("nextHop").asText(), 64)); }
                catch (java.io.IOException e) { throw new BusinessException(409, "route next hop is missing"); }
            }
            if (destinations.size() != 1) throw new BusinessException(409, "slot has different next hops");
            destination = destinations.first();
        }
        var occurred = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var at = LocalDateTime.ofInstant(occurred, ZoneOffset.UTC);
        var command = new RepackingCommand(key, IdGeneratorUtil.generateBagNo(), r.facilityCode(), destination, r.actorId(), occurred, items).normalized();
        jdbc.update("INSERT INTO sorting_repack_request (request_no,bag_no,facility_code,request_hash,request_json,payload_json,next_attempt_at,occurred_at) VALUES (?,?,?,?,?,?,?,?)",
                key, command.bagNo(), r.facilityCode(), hash, payload, write(command), at, at);
        for (var item : command.items()) {
            jdbc.update("INSERT INTO sorting_repack_item (unpack_event_no,mail_no,request_no,source_bag_no) VALUES (?,?,?,?)", item.unpackEventNo(), item.mailNo(), key, item.sourceBagNo());
            jdbc.update("INSERT INTO package_item_line (biz_line_no,idempotency_key,item_no,action_type,event_type,scan_mode,from_package_no,to_package_no,station_code,operator_id,event_time,ext) VALUES (?,?,?,'LOAD','REBAG','PREALERT',?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(), item.mailNo(), item.sourceBagNo(), command.bagNo(), r.facilityCode(), r.actorId(), at, write(Map.of("unpackEventNo", item.unpackEventNo(), "repackRequestNo", key)));
        }
        return response(command);
    }
    private List<String> slotItems(String slot, String facility) {
        return jdbc.query("SELECT l.item_no FROM package_item_line l WHERE l.event_type='ROUTE' AND l.target_center_code=? AND l.station_code=? AND NOT EXISTS (SELECT 1 FROM package_item_line n WHERE n.item_no=l.item_no AND (n.event_time>l.event_time OR (n.event_time=l.event_time AND n.id>l.id))) ORDER BY l.item_no", (rs, n) -> rs.getString(1), slot, facility);
    }
    private List<Map<String,Object>> latestScan(String no) {
        return jdbc.queryForList("SELECT r.* FROM sorting_unpack_item i JOIN sorting_unpack_request r ON r.event_no=i.event_no WHERE i.mail_no=? ORDER BY r.occurred_at DESC,r.event_no DESC LIMIT 1", no);
    }
    public Receipt receipt(String requestNo) {
        var rows = jdbc.queryForList("SELECT * FROM sorting_repack_request WHERE request_no=?", code(requestNo, 64));
        if (rows.isEmpty()) throw new BusinessException(404, "repacking not found");
        var row = rows.get(0); unpacking.actor((String) row.get("facility_code"));
        return new Receipt(read((String) row.get("payload_json")), (String) row.get("status"), (String) row.get("last_error"));
    }
    private RepackingCommand replay(String key, String hash) {
        var rows = jdbc.queryForList("SELECT request_hash,payload_json FROM sorting_repack_request WHERE request_no=?", key);
        if (rows.isEmpty()) return null;
        if (!hash.equals(rows.get(0).get("request_hash"))) throw new BusinessException(409, "repacking request content changed");
        return read((String) rows.get(0).get("payload_json"));
    }
    private SortingPackageResponse response(RepackingCommand c) {
        var response = new SortingPackageResponse(); response.setPackageNo(c.bagNo()); response.setSourceOrgCode(c.facilityCode());
        response.setDestinationOrgCode(c.destinationCode()); response.setPackageStatus("PACKING_PENDING"); response.setPackageLevel("TRANSIT");
        return response;
    }
    private boolean hasValue(String value) { return value != null && !value.isBlank(); }
    private String optional(String value) { return hasValue(value) ? code(value, 64) : null; }
    private String code(String value, int max) { return UnpackingConfirmation.code(value, max); }
    private RepackingCommand read(String value) { try { return json.readValue(value, RepackingCommand.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
