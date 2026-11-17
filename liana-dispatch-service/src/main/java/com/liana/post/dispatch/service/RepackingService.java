package com.liana.post.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.dto.dispatch.RepackingCommand;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.dispatch.client.FacilityRouteClient;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import com.liana.post.dispatch.repository.DispatchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

@Service
public class RepackingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final FacilityRouteClient facility;
    private final DispatchRepository repository;
    private final BagPackingService packing;
    private final TransactionTemplate tx;
    public RepackingService(JdbcTemplate jdbc, ObjectMapper json, FacilityRouteClient facility,
            DispatchRepository repository, BagPackingService packing, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.facility = facility; this.repository = repository; this.packing = packing;
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public RepackingCommand.Ack accept(RepackingCommand input) {
        if (input == null) throw new BusinessException(400, "repacking command required");
        var command = input.normalized();
        var replay = replay(command); if (replay != null) return replay;
        // Island network validation happens before the local transaction.
        var routes = facility.listRoutes();
        if (routes == null || !routes.isSuccess() || routes.getData() == null) throw new BusinessException(503, "network lookup unavailable");
        String edge = routes.getData().stream().filter(r -> command.facilityCode().equals(r.getOriginFacilityCode())
                && command.destinationCode().equals(r.getDestinationFacilityCode()) && Integer.valueOf(1).equals(r.getStatus()))
                .sorted(Comparator.comparing(FacilityRouteClient.FacilityRouteView::getRouteCode)).map(FacilityRouteClient.FacilityRouteView::getRouteCode)
                .findFirst().orElseThrow(() -> new BusinessException(409, "repacking destination requires an active island network edge"));
        try { return tx.execute(s -> save(command, edge)); }
        catch (org.springframework.dao.DuplicateKeyException e) {
            replay = replay(command); if (replay != null) return replay;
            throw new BusinessException(409, "repacking identity or scan already used");
        }
    }
    private RepackingCommand.Ack save(RepackingCommand c, String edge) {
        var replay = replay(c); if (replay != null) return replay;
        for (String source : c.items().stream().map(RepackingCommand.Item::sourceBagNo).distinct().sorted().toList()) {
            var bags = jdbc.queryForList("SELECT status,transport_task_code FROM dispatch_bag WHERE bag_no=? FOR UPDATE", source);
            if (bags.size() != 1 || !"ARRIVED".equals(bags.get(0).get("status")) || bags.get(0).get("transport_task_code") != null
                    || jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_batch_bag WHERE bag_no=? AND active_flag=1", Long.class, source) != 0) {
                throw new BusinessException(409, "source bag receipt must be confirmed before repacking");
            }
        }
        long total = 0; boolean known = true;
        for (var item : c.items()) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_bag_item WHERE bag_no=? AND item_no=?", Long.class, item.sourceBagNo(), item.mailNo()) != 1) {
                throw new BusinessException(409, "mail absent from original structured packing");
            }
            if (item.weightGrams() == null) known = false; else total = Math.addExact(total, item.weightGrams());
        }
        if (total > Integer.MAX_VALUE) throw new BusinessException(409, "repacked weight exceeds transport representation");
        var bag = new DispatchBagEntity(); bag.setBagNo(c.bagNo()); bag.setOriginFacilityCode(c.facilityCode());
        bag.setDestinationFacilityCode(c.destinationCode()); bag.setFacilityRouteCode(edge); bag.setMailTypeCode("MIXED");
        bag.setMailNoListAsList(c.items().stream().map(RepackingCommand.Item::mailNo).toList());
        bag.setMailCount(c.items().size()); bag.setTotalWeightGrams(known ? (int) total : null); bag.setStatus("CREATED");
        repository.saveDispatchBag(bag);
        packing.prepare(new PackingConfirmation(c.requestNo(), c.bagNo(), c.facilityCode(), 1,
                c.items().stream().map(i -> new PackingConfirmation.Item(i.mailNo(), i.weightGrams())).toList()));
        for (var item : c.items()) jdbc.update("INSERT INTO dispatch_repack_source (bag_no,mail_no,source_bag_no,unpack_event_no) VALUES (?,?,?,?)",
                c.bagNo(), item.mailNo(), item.sourceBagNo(), item.unpackEventNo());
        var ack = new RepackingCommand.Ack(c, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        jdbc.update("INSERT INTO dispatch_repack_inbox (request_no,bag_no,payload_json,receipt_json,prepared_at) VALUES (?,?,?,?,?)",
                c.requestNo(), c.bagNo(), write(c), write(ack), LocalDateTime.ofInstant(ack.preparedAt(), ZoneOffset.UTC));
        return ack;
    }
    private RepackingCommand.Ack replay(RepackingCommand c) {
        var rows = jdbc.queryForList("SELECT payload_json,receipt_json FROM dispatch_repack_inbox WHERE request_no=?", c.requestNo());
        if (rows.isEmpty()) return null;
        try {
            if (!c.equals(json.readValue((String) rows.get(0).get("payload_json"), RepackingCommand.class))) throw new BusinessException(409, "repacking content changed");
            return json.readValue((String) rows.get(0).get("receipt_json"), RepackingCommand.Ack.class);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
}
