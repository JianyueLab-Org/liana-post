package com.liana.post.sorting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;

@Service
public class DiscrepancyDispositionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HttpServletRequest http;
    private final TransactionTemplate tx;
    public record Request(String idempotencyKey, String disposition, String reason) { }
    public record Receipt(String verificationNo, String status, String disposition, String reason, String resolverId, LocalDateTime resolvedAt) { }
    public DiscrepancyDispositionService(JdbcTemplate jdbc, ObjectMapper json, HttpServletRequest http, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.http = http; tx = new TransactionTemplate(manager);
    }
    public Receipt apply(String verificationNo, Request input) {
        String actor = actor();
        if (input == null || input.idempotencyKey() == null || !input.idempotencyKey().trim().matches("[A-Za-z0-9_-]{1,64}")) throw new BusinessException(400, "fixed review request key required");
        String reason = input.reason() == null ? "" : input.reason().trim();
        if (reason.length() < 8 || reason.length() > 256) throw new BusinessException(400, "review reason must contain 8 to 256 characters");
        String key = input.idempotencyKey().trim().toUpperCase(Locale.ROOT);
        String disposition = input.disposition() == null ? "" : input.disposition().trim().toUpperCase(Locale.ROOT);
        String no = verificationNo == null ? "" : verificationNo.trim().toUpperCase(Locale.ROOT);
        if (!no.matches("[A-Z0-9_-]{1,64}")) throw new BusinessException(400, "invalid discrepancy number");
        return tx.execute(s -> save(no, key, disposition, reason, actor));
    }
    private Receipt save(String no, String key, String disposition, String reason, String actor) {
        var parents = jdbc.queryForList("SELECT manifest_no FROM discrepancy_verification WHERE verification_no=?", no);
        if (parents.size() != 1) throw new BusinessException(404, "discrepancy not found");
        Object manifestNo = parents.get(0).get("manifest_no");
        if (manifestNo != null) jdbc.queryForList("SELECT manifest_no FROM sorting_manifest WHERE manifest_no=? FOR UPDATE", manifestNo);
        var rows = jdbc.queryForList("SELECT * FROM discrepancy_verification WHERE verification_no=? FOR UPDATE", no);
        if (rows.size() != 1) throw new BusinessException(404, "discrepancy not found");
        var row = rows.get(0);
        if (!java.util.Objects.equals(manifestNo, row.get("manifest_no"))) throw new BusinessException(409, "discrepancy parent changed");
        if (!"MANIFEST".equals(row.get("discrepancy_source"))) throw new BusinessException(409, "this review only applies to recorded manifest discrepancies");
        String type = ((String) row.get("discrepancy_type")).toUpperCase(Locale.ROOT);
        String expected = switch (type) { case "MISSING" -> "CONFIRMED_NOT_RECEIVED"; case "SURPLUS" -> "QUARANTINED"; default -> throw new BusinessException(409, "this discrepancy type has no simulated disposition"); };
        if (!expected.equals(disposition)) throw new BusinessException(400, "disposition does not match recorded discrepancy type");
        String evidence = (String) row.get("evidence");
        var facts = evidence == null || evidence.isBlank() ? new LinkedHashMap<String, Object>() : read(evidence);
        if ("RESOLVED".equals(row.get("status"))) {
            if (key.equals(facts.get("dispositionRequestNo")) && disposition.equals(facts.get("disposition"))
                    && reason.equals(row.get("remark")) && actor.equals(row.get("resolver_id"))) return receipt(row, disposition, reason, actor);
            throw new BusinessException(409, "discrepancy already reviewed with another decision");
        }
        if (!"OPEN".equals(row.get("status"))) throw new BusinessException(409, "discrepancy is not open");
        facts.put("dispositionRequestNo", key); facts.put("disposition", disposition); facts.put("dispositionReason", reason);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (jdbc.update("UPDATE discrepancy_verification SET status='RESOLVED',evidence=?,resolved_at=?,resolver_id=?,remark=? WHERE verification_no=? AND status='OPEN'",
                write(facts), now, actor, reason, no) != 1) throw new BusinessException(409, "discrepancy changed during review");
        return new Receipt(no, "RESOLVED", disposition, reason, actor, now);
    }
    private Receipt receipt(java.util.Map<String,Object> row, String disposition, String reason, String actor) {
        Object timestamp = row.get("resolved_at");
        LocalDateTime at = timestamp == null ? null : timestamp instanceof LocalDateTime value
                ? value : ((java.sql.Timestamp) timestamp).toLocalDateTime();
        return new Receipt((String) row.get("verification_no"), "RESOLVED", disposition, reason, actor,
                at);
    }
    private String actor() {
        String header = http.getHeader("Authorization");
        String token = header != null && header.matches("(?i)^Bearer\\s+\\S+$") ? header.replaceFirst("(?i)^Bearer\\s+", "") : null;
        if (!JwtTokenUtil.validateToken(token)) throw new BusinessException(401, "valid login required");
        var user = JwtTokenUtil.getUserContextFromToken(token);
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) throw new BusinessException(403, "administrator review required");
        String id = user.getUserId() == null ? user.getUsername() : user.getUserId().toString();
        if (id == null || id.length() > 32 || id.isBlank()) throw new BusinessException(401, "invalid review identity");
        return id;
    }
    private LinkedHashMap<String,Object> read(String value) {
        try { return json.readValue(value, new TypeReference<LinkedHashMap<String,Object>>() { }); }
        catch (Exception e) { throw new IllegalStateException("invalid saved discrepancy evidence", e); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
}
