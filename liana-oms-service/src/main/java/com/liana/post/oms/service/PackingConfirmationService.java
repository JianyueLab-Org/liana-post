package com.liana.post.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.oms.cache.RedisCacheSupport;
import com.liana.post.oms.cache.RedisKeyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Consumes complete packing commands atomically; no callback to dispatch inside this transaction. */
@Service
public class PackingConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(PackingConfirmationService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;

    public PackingConfirmationService(JdbcTemplate jdbc, ObjectMapper json, StringRedisTemplate redis) {
        this.jdbc = jdbc; this.json = json; this.redis = redis;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public boolean accept(PackingConfirmation command) {
        if (command == null) throw new BusinessException(400, "packing is required");
        var request = command.normalized();
        String payload;
        try { payload = json.writeValueAsString(request); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO oms_packing_inbox (bag_no,event_no,request_hash,request_json,recorded_at) VALUES (?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE bag_no=bag_no", request.bagNo(), request.eventNo(), hash, payload, now);
        var receipts = jdbc.queryForList("SELECT request_hash,applied_at FROM oms_packing_inbox WHERE bag_no=? FOR UPDATE", request.bagNo());
        if (receipts.size() != 1 || !hash.equals(receipts.get(0).get("request_hash"))) {
            throw new BusinessException(409, "packing identity already has different content");
        }
        evictAfterCommit(request);
        if (receipts.get(0).get("applied_at") != null) return true;
        // Stable mail ordering prevents overlapping multi-mail commands acquiring locks in opposite order.
        for (var item : request.items().stream().sorted(java.util.Comparator.comparing(PackingConfirmation.Item::mailNo)).toList()) {
            var rows = jdbc.queryForList("SELECT bag_no,package_id,current_facility_code,status,weight_grams FROM mail WHERE waybill_no=? FOR UPDATE", item.mailNo());
            if (rows.size() != 1) throw new BusinessException(409, "packing mail not found: " + item.mailNo());
            var mail = rows.get(0);
            if (!request.facilityCode().equals(mail.get("current_facility_code"))
                    || !Set.of("CREATED", "ACCEPTED", "SORTED", "ARRIVED").contains(mail.get("status"))
                    || hasValue(mail.get("bag_no")) || hasValue(mail.get("package_id"))) {
                throw new BusinessException(409, "mail is unavailable or already packed: " + item.mailNo());
            }
            Integer weight = mail.get("weight_grams") == null ? null : ((Number) mail.get("weight_grams")).intValue();
            if (!Objects.equals(weight, item.weightGrams())) {
                throw new BusinessException(409, "mail weight changed since packing was prepared: " + item.mailNo());
            }
            jdbc.update("UPDATE mail SET bag_no=?,package_id=?,current_slot=NULL,status='DISPATCHED',updated_at=? WHERE waybill_no=?",
                    request.bagNo(), request.bagNo(), now, item.mailNo());
        }
        jdbc.update("UPDATE oms_packing_inbox SET applied_at=? WHERE bag_no=?", now, request.bagNo());
        return true;
    }

    private boolean hasValue(Object value) { return value != null && !value.toString().isBlank(); }

    private void evictAfterCommit(PackingConfirmation request) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    for (var item : request.items()) RedisCacheSupport.evict(redis, RedisKeyConstants.OMS_MAIL_BY_WAYBILL_PREFIX + item.mailNo());
                    RedisCacheSupport.evictLike(redis, RedisKeyConstants.OMS_DISPATCH_CANDIDATES_PREFIX);
                    RedisCacheSupport.evictLike(redis, RedisKeyConstants.OMS_MAIL_LIST_BY_STATUS_PREFIX);
                } catch (RuntimeException e) { log.warn("Packing committed; OMS cache eviction will be retried on redelivery: {}", request.bagNo()); }
            }
        });
    }
}
