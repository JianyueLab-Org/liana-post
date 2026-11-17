package com.liana.post.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

@Service
public class BagPackingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public BagPackingService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public record Delivery(String bagNo, String payload, int attempts) { }
    public record State(Integer packingVersion, String syncStatus, String lastError) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void prepare(PackingConfirmation command) {
        var request = command.normalized();
        String payload;
        try { payload = json.writeValueAsString(request); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        String hash = hash(payload);
        LocalDateTime now = now();
        // The bag row, complete packing, and durable delivery are committed by one local transaction.
        jdbc.queryForObject("SELECT bag_no FROM dispatch_bag WHERE bag_no=? FOR UPDATE", String.class, request.bagNo());
        var previous = jdbc.query("SELECT content_hash FROM dispatch_bag_packing WHERE bag_no=?",
                (rs, row) -> rs.getString(1), request.bagNo());
        if (!previous.isEmpty()) {
            if (!previous.get(0).equals(hash)) throw new BusinessException(409, "packing content is immutable");
            return;
        }
        jdbc.update("INSERT INTO dispatch_bag_packing (bag_no,packing_version,event_no,content_hash,payload_json,"
                        + "recorded_at,next_attempt_at) VALUES (?,?,?,?,?,?,?)", request.bagNo(), 1,
                request.eventNo(), hash, payload, now, now);
        int seq = 0;
        for (var item : request.items()) {
            jdbc.update("INSERT INTO dispatch_bag_item (bag_no,item_no,seq_no,weight_grams) VALUES (?,?,?,?)",
                    request.bagNo(), item.mailNo(), ++seq, item.weightGrams());
        }
    }

    public List<PackingConfirmation.Item> items(String bagNo) {
        return jdbc.query("SELECT item_no,weight_grams FROM dispatch_bag_item WHERE bag_no=? ORDER BY seq_no",
                (rs, row) -> new PackingConfirmation.Item(rs.getString(1), rs.getObject(2, Integer.class)), bagNo);
    }

    public State state(String bagNo) {
        return jdbc.query("SELECT packing_version,sync_status,last_error FROM dispatch_bag_packing WHERE bag_no=?",
                (rs, row) -> new State(rs.getInt(1), rs.getString(2), rs.getString(3)), bagNo).stream()
                .findFirst().orElse(new State(null, "NOT_PREPARED", null));
    }

    public void requireConfirmed(String bagNo) {
        if (!"CONFIRMED".equals(state(bagNo).syncStatus())) {
            throw new BusinessException(409, "总包装包尚未获 OMS 确认，请在总包页面查看状态");
        }
    }

    public List<Delivery> due() {
        return jdbc.query("SELECT bag_no,payload_json,attempt_count FROM dispatch_bag_packing "
                        + "WHERE sync_status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=? "
                        + "ORDER BY recorded_at,bag_no LIMIT 10",
                (rs, row) -> new Delivery(rs.getString(1), rs.getString(2), rs.getInt(3)), now());
    }

    public boolean claim(Delivery delivery) {
        return jdbc.update("UPDATE dispatch_bag_packing SET sync_status='SENDING',attempt_count=attempt_count+1,next_attempt_at=? "
                        + "WHERE bag_no=? AND sync_status IN ('PENDING','RETRY','SENDING') AND next_attempt_at<=? AND attempt_count=?",
                now().plusSeconds(60), delivery.bagNo(), now(), delivery.attempts()) == 1;
    }

    @Transactional
    public void confirmed(Delivery delivery) {
        int updated = jdbc.update("UPDATE dispatch_bag_packing SET sync_status='CONFIRMED',confirmed_at=?,last_error=NULL "
                        + "WHERE bag_no=? AND sync_status='SENDING' AND attempt_count=?",
                now(), delivery.bagNo(), delivery.attempts() + 1);
        if (updated == 1) jdbc.update("UPDATE dispatch_bag SET status='REVIEWED',updated_at=? WHERE bag_no=? AND status='CREATED'",
                now(), delivery.bagNo());
    }

    public void failed(Delivery delivery, String error, boolean conflict) {
        String message = error == null ? "OMS confirmation failed" : error.substring(0, Math.min(error.length(), 512));
        jdbc.update("UPDATE dispatch_bag_packing SET sync_status=?,last_error=?,next_attempt_at=? "
                        + "WHERE bag_no=? AND sync_status='SENDING' AND attempt_count=?", conflict ? "CONFLICT" : "RETRY", message,
                now().plusSeconds(Math.min(3600, 5L << Math.min(delivery.attempts(), 10))), delivery.bagNo(), delivery.attempts() + 1);
    }

    private String hash(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS); }
}
