package com.liana.post.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.oms.cache.RedisCacheSupport;
import com.liana.post.oms.cache.RedisKeyConstants;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class UnpackingConfirmationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    public UnpackingConfirmationService(JdbcTemplate jdbc, ObjectMapper json, StringRedisTemplate redis) {
        this.jdbc = jdbc; this.json = json; this.redis = redis;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public UnpackingConfirmation.Ack accept(UnpackingConfirmation input) {
        if (input == null) throw new BusinessException(400, "unpacking command required");
        var command = input.normalized();
        String payload = write(command), hash = hash(payload);
        Instant applied = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var now = LocalDateTime.ofInstant(applied, ZoneOffset.UTC);
        jdbc.update("INSERT INTO oms_unpacking_inbox (event_no,bag_no,request_hash,request_json,occurred_at,recorded_at) VALUES (?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE event_no=event_no", command.eventNo(), command.bagNo(), hash, payload,
                LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC), now);
        var receipts = jdbc.queryForList("SELECT request_hash,receipt_json FROM oms_unpacking_inbox WHERE event_no=? FOR UPDATE", command.eventNo());
        if (receipts.size() != 1 || !hash.equals(receipts.get(0).get("request_hash"))) {
            throw new BusinessException(409, "unpacking identity already has different content");
        }
        evictAfterCommit(command);
        if (receipts.get(0).get("receipt_json") != null) return read((String) receipts.get(0).get("receipt_json"));
        // Facility may still describe the original packing site after several sealed transport legs.
        // Ownership, rather than that stale location, is the guard against detaching another bag's mail.
        for (String no : command.mailNos()) {
            var rows = jdbc.queryForList("SELECT bag_no,package_id,status FROM mail WHERE waybill_no=? FOR UPDATE", no);
            if (rows.size() != 1 || !command.bagNo().equals(rows.get(0).get("bag_no"))
                    || !command.bagNo().equals(rows.get(0).get("package_id"))
                    || !"DISPATCHED".equals(rows.get(0).get("status"))) {
                throw new BusinessException(409, "scanned mail no longer belongs to unopened bag: " + no);
            }
            jdbc.update("UPDATE mail SET bag_no=NULL,package_id=NULL,current_slot=NULL,current_facility_code=?,status='SORTED',updated_at=? WHERE waybill_no=?",
                    command.facilityCode(), now, no);
        }
        var ack = new UnpackingConfirmation.Ack(command, applied);
        jdbc.update("UPDATE oms_unpacking_inbox SET receipt_json=?,applied_at=? WHERE event_no=?", write(ack), now, command.eventNo());
        return ack;
    }

    private void evictAfterCommit(UnpackingConfirmation command) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    for (String no : command.mailNos()) RedisCacheSupport.evict(redis, RedisKeyConstants.OMS_MAIL_BY_WAYBILL_PREFIX + no);
                    RedisCacheSupport.evictLike(redis, RedisKeyConstants.OMS_DISPATCH_CANDIDATES_PREFIX);
                    RedisCacheSupport.evictLike(redis, RedisKeyConstants.OMS_MAIL_LIST_BY_STATUS_PREFIX);
                } catch (RuntimeException e) {
                    LoggerFactory.getLogger(UnpackingConfirmationService.class).warn("Unpacking committed; cache eviction retries on redelivery: {}", command.eventNo());
                }
            }
        });
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private UnpackingConfirmation.Ack read(String value) { try { return json.readValue(value, UnpackingConfirmation.Ack.class); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
