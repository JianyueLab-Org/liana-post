package com.liana.post.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.BagForwarding;
import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.dto.sorting.TotalPackageDTO;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.dispatch.client.FacilityRouteClient;
import com.liana.post.dispatch.model.entity.DispatchBatchEntity;
import com.liana.post.dispatch.repository.DispatchRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Consumes a sorting custody reservation; normal prealert, sealing and transport follow afterwards. */
@Service
public class BagForwardingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DispatchRepository repository;
    private final BagPackingService packing;
    private final ManifestOutbox outbox;
    private final FacilityRouteClient facility;
    private final TransactionTemplate tx;
    public BagForwardingService(JdbcTemplate jdbc, ObjectMapper json, DispatchRepository repository,
            BagPackingService packing, ManifestOutbox outbox, FacilityRouteClient facility, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.repository = repository; this.packing = packing;
        this.outbox = outbox; this.facility = facility; this.tx = new TransactionTemplate(manager);
    }

    public BagForwarding.Ack accept(BagForwarding.Command input) {
        if (input == null) throw new BusinessException(400, "forwarding command required");
        var command = input.normalized();
        var replay = replay(command);
        if (replay != null) return replay;
        // No local transaction spans the network lookup. Transport revalidates the selected edge and mode.
        var routes = facility.listRoutes();
        if (routes == null || !routes.isSuccess() || routes.getData() == null) throw new BusinessException(503, "facility route lookup unavailable");
        if (routes.getData().stream().noneMatch(r -> command.facilityRouteCode().equals(r.getRouteCode())
                && command.sourceOrgCode().equals(r.getOriginFacilityCode()) && command.destinationOrgCode().equals(r.getDestinationFacilityCode())
                && Integer.valueOf(1).equals(r.getStatus()))) throw new BusinessException(409, "forwarding requires the configured active island network edge");
        try { return tx.execute(s -> save(command)); }
        catch (DuplicateKeyException e) { throw new BusinessException(409, "forwarding request or bag is already reserved; retry original request"); }
    }

    private BagForwarding.Ack save(BagForwarding.Command command) {
        // Serialize all successors of a previous manifest, then lock bags in barcode order.
        if (jdbc.queryForList("SELECT batch_no FROM dispatch_batch WHERE manifest_no=? FOR UPDATE", command.previousManifestNo()).size() != 1) throw new BusinessException(404, "previous manifest not found");
        var replay = replay(command);
        if (replay != null) return replay;
        var manifest = new ManifestDTO();
        manifest.setManifestCode(command.manifestNo()); manifest.setBatchNo(command.batchNo()); manifest.setVersionNo(1);
        manifest.setOriginNode(command.sourceOrgCode()); manifest.setTargetNode(command.destinationOrgCode());
        var packages = new ArrayList<TotalPackageDTO>();
        BigDecimal total = BigDecimal.ZERO; boolean known = true;
        for (var bag : command.bags()) {
            var rows = jdbc.queryForList("SELECT status,transport_task_code,last_receipt_event_no FROM dispatch_bag WHERE bag_no=? FOR UPDATE", bag.bagNo());
            if (rows.size() != 1 || !List.of("ARRIVED", "REVIEWED").contains(rows.get(0).get("status"))
                    || rows.get(0).get("transport_task_code") != null || !bag.receiptEventNo().equals(rows.get(0).get("last_receipt_event_no"))) {
                throw new BusinessException(409, "forwarding requires this bag's latest confirmed receipt");
            }
            var receipts = jdbc.queryForList("SELECT payload_json FROM dispatch_receipt_inbox WHERE event_no=? AND manifest_no=? AND bag_no=?", bag.receiptEventNo(), command.previousManifestNo(), bag.bagNo());
            if (receipts.size() != 1 || !command.sourceOrgCode().equals(read((String) receipts.get(0).get("payload_json"), ManifestReceipt.Event.class).destinationOrgCode())) throw new BusinessException(409, "forwarding source differs from receipt");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag WHERE bag_no=? AND active_flag=1", Long.class, bag.bagNo()) != 0) throw new BusinessException(409, "bag already has an active batch");
            packing.requireConfirmed(bag.bagNo());
            var items = packing.items(bag.bagNo());
            if (items.isEmpty()) throw new BusinessException(409, "original structured packing is missing");
            var pack = new TotalPackageDTO(); pack.setPackageNo(bag.bagNo()); pack.setPackageLevel("TRANSIT");
            var lines = new ArrayList<TotalPackageDTO.ManifestItemDTO>();
            long weight = 0; boolean bagKnown = true;
            for (var item : items) {
                var line = new TotalPackageDTO.ManifestItemDTO(); line.setItemNo(item.mailNo()); line.setSeqNo(lines.size() + 1);
                line.setWeight(item.weightGrams() == null ? null : BigDecimal.valueOf(item.weightGrams())); lines.add(line);
                if (item.weightGrams() == null) bagKnown = false; else weight = Math.addExact(weight, item.weightGrams());
            }
            if (weight > Integer.MAX_VALUE) throw new BusinessException(409, "bag weight exceeds transport capacity representation");
            pack.setItems(lines); pack.setPackageWeight(bagKnown ? BigDecimal.valueOf(weight) : null); packages.add(pack);
            if (bagKnown) total = total.add(pack.getPackageWeight()); else known = false;
        }
        var batch = new DispatchBatchEntity(); batch.setBatchNo(command.batchNo()); batch.setManifestNo(command.manifestNo());
        batch.setBagNo(command.bags().get(0).bagNo()); batch.setSourceOrgCode(command.sourceOrgCode());
        batch.setDestinationOrgCode(command.destinationOrgCode()); batch.setFacilityRouteCode(command.facilityRouteCode());
        batch.setStatus("PENDING");
        repository.saveDispatchBatch(batch);
        repository.replaceBatchBags(batch.getBatchNo(), command.bags().stream().map(BagForwarding.Bag::bagNo).toList());
        for (var bag : command.bags()) jdbc.update("UPDATE dispatch_bag SET status='REVIEWED',updated_at=? WHERE bag_no=?", LocalDateTime.now(ZoneOffset.UTC), bag.bagNo());
        manifest.setTotalPackages(packages); manifest.setTotalWeight(known ? total : null);
        outbox.enqueue(manifest);
        var ack = new BagForwarding.Ack(command, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        jdbc.update("INSERT INTO dispatch_forward_inbox (request_no,manifest_no,previous_manifest_no,payload_json,receipt_json,confirmed_at) VALUES (?,?,?,?,?,?)",
                command.requestNo(), command.manifestNo(), command.previousManifestNo(), write(command), write(ack), LocalDateTime.ofInstant(ack.confirmedAt(), ZoneOffset.UTC));
        return ack;
    }

    private BagForwarding.Ack replay(BagForwarding.Command command) {
        var rows = jdbc.queryForList("SELECT payload_json,receipt_json FROM dispatch_forward_inbox WHERE request_no=?", command.requestNo());
        if (rows.isEmpty()) return null;
        if (!command.equals(read((String) rows.get(0).get("payload_json"), BagForwarding.Command.class))) throw new BusinessException(409, "forwarding request content changed");
        return read((String) rows.get(0).get("receipt_json"), BagForwarding.Ack.class);
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
}
