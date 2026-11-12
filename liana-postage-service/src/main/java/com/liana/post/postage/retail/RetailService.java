package com.liana.post.postage.retail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.model.JwtUserContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.liana.post.postage.retail.RetailModels.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RetailService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final StampCodeGenerator codes;

    public RetailService(JdbcTemplate jdbc, ObjectMapper json, Clock clock, StampCodeGenerator codes) {
        this.jdbc = jdbc; this.json = json; this.clock = clock; this.codes = codes;
    }

    public Policy policy() {
        return jdbc.queryForObject("SELECT * FROM postage_stamp_policy WHERE id=1", (rs, n) ->
                new Policy(rs.getInt("revision"), rs.getString("currency"), rs.getBigDecimal("min_face_value"),
                        rs.getBigDecimal("max_face_value"), rs.getInt("validity_days"), rs.getString("updated_by"), instant(rs, "updated_at")));
    }

    public Policy updatePolicy(UpdatePolicy request, JwtUserContext actor) {
        admin(actor);
        lockPolicy();
        var before = policy();
        if (request.revision() != before.revision()) conflict("发行规则已更新，请重新加载");
        var min = money(request.minFaceValue());
        var max = money(request.maxFaceValue());
        if (min.compareTo(max) > 0 || max.compareTo(new BigDecimal("999999.99")) > 0
                || request.validityDays() < 1 || request.validityDays() > 36500) invalid("面额上下限或有效天数无效");
        Instant now = clock.instant();
        jdbc.update("UPDATE postage_stamp_policy SET revision=revision+1,min_face_value=?,max_face_value=?,validity_days=?,updated_by=?,updated_at=? WHERE id=1",
                min, max, request.validityDays(), actor.getUsername(), now.toEpochMilli());
        var after = policy();
        audit("POLICY", "1", null, "POLICY_UPDATED", actor, now, write(List.of(before, after)));
        return after;
    }

    public List<Audit> policyAudit(JwtUserContext actor) {
        admin(actor);
        return jdbc.query("SELECT * FROM postage_retail_audit WHERE subject_type='POLICY' ORDER BY occurred_at DESC,event_id LIMIT 100", auditMapper());
    }

    public Detail create(Create request, JwtUserContext actor) {
        requestId(request.requestId());
        var lines = normalizeLines(request.lines());
        // Shared row lock protects create retries and the policy snapshot across instances.
        lockPolicy();
        var previous = jdbc.query("SELECT * FROM postage_sale WHERE created_by=? AND request_id=?", saleMapper(), actor.getUsername(), request.requestId());
        if (!previous.isEmpty()) {
            if (!previous.get(0).lines().equals(lines)) conflict("请求编号已用于不同售卖内容，请重新发起业务");
            return detail(previous.get(0).saleId(), actor);
        }
        var policy = policy();
        BigDecimal total = BigDecimal.ZERO;
        for (var line : lines) {
            if (line.faceValue().compareTo(policy.minFaceValue()) < 0 || line.faceValue().compareTo(policy.maxFaceValue()) > 0) invalid("面额超出当前发行范围");
            total = total.add(line.faceValue().multiply(BigDecimal.valueOf(line.quantity())));
        }
        var saleId = id();
        Instant now = clock.instant();
        jdbc.update("INSERT INTO postage_sale (sale_id,request_id,status,currency,total_amount,lines_json,policy_revision,validity_days,created_by,facility_code,created_at) VALUES (?,?,'PENDING_PAYMENT',?,?,?,?,?,?,?,?)",
                saleId, request.requestId(), policy.currency(), total.setScale(2), write(lines), policy.revision(), policy.validityDays(), actor.getUsername(), actor.getFacilityCode(), now.toEpochMilli());
        audit("SALE", saleId, saleId, "SALE_CREATED", actor, now, write(lines));
        return detail(saleId, actor);
    }

    public Detail confirm(String saleId, Confirm request, JwtUserContext actor) {
        requestId(request.requestId());
        var sale = lockSale(saleId);
        owner(sale, actor);
        BigDecimal received = money(request.amountReceived());
        if (received.compareTo(sale.totalAmount()) != 0) invalid("确认收款金额必须等于应收邮资");
        var previous = receipt(saleId);
        if (previous != null) {
            if (!previous.requestId().equals(request.requestId()) || !previous.confirmedBy().equals(actor.getUsername())) conflict("此售卖已确认收款，请读取原发行结果");
            return detail(saleId, actor);
        }
        if (!"PENDING_PAYMENT".equals(sale.status())) conflict("此售卖不能确认收款");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM postage_receipt WHERE confirmed_by=? AND request_id=?", Integer.class, actor.getUsername(), request.requestId()) > 0) conflict("收款请求编号已用于其他售卖");
        Instant now = clock.instant();
        jdbc.update("INSERT INTO postage_receipt (receipt_id,sale_id,request_id,method,amount_received,confirmed_by,facility_code,confirmed_at) VALUES (?,?,?,'CASH',?,?,?,?)",
                id(), saleId, request.requestId(), received, actor.getUsername(), actor.getFacilityCode(), now.toEpochMilli());
        audit("SALE", saleId, saleId, "CASH_CONFIRMED", actor, now, received.toPlainString());
        for (var line : sale.lines()) {
            for (int n = 0; n < line.quantity(); n++) {
                String stampId = id();
                jdbc.update("INSERT INTO postage_digital_stamp (stamp_id,code,sale_id,currency,face_value,status,issued_at,expires_at) VALUES (?,?,?,?,?,'ISSUED',?,?)",
                        stampId, codes.generate(), saleId, sale.currency(), line.faceValue(), now.toEpochMilli(), now.plus(sale.validityDays(), ChronoUnit.DAYS).toEpochMilli());
                audit("STAMP", stampId, saleId, "STAMP_ISSUED", actor, now, line.faceValue().toPlainString());
            }
        }
        jdbc.update("UPDATE postage_sale SET status='PAID',paid_at=? WHERE sale_id=?", now.toEpochMilli(), saleId);
        return detail(saleId, actor);
    }

    public Detail cancel(String saleId, JwtUserContext actor) {
        var sale = lockSale(saleId);
        owner(sale, actor);
        if ("CANCELLED".equals(sale.status())) return detail(saleId, actor);
        if (!"PENDING_PAYMENT".equals(sale.status())) conflict("已收款售卖不能取消");
        jdbc.update("UPDATE postage_sale SET status='CANCELLED' WHERE sale_id=?", saleId);
        audit("SALE", saleId, saleId, "SALE_CANCELLED", actor, clock.instant(), "取消未收款售卖");
        return detail(saleId, actor);
    }

    public List<Sale> sales(JwtUserContext actor, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) invalid("分页参数无效");
        return "ADMIN".equals(actor.getRole())
                ? jdbc.query("SELECT * FROM postage_sale ORDER BY created_at DESC,sale_id LIMIT ? OFFSET ?", saleMapper(), size, page * size)
                : jdbc.query("SELECT * FROM postage_sale WHERE created_by=? ORDER BY created_at DESC,sale_id LIMIT ? OFFSET ?", saleMapper(), actor.getUsername(), size, page * size);
    }

    public Detail detail(String saleId, JwtUserContext actor) {
        // Keep sale status, receipt and issued vouchers from the same committed transition.
        var sale = lockSale(saleId);
        owner(sale, actor);
        var stamps = jdbc.query("SELECT * FROM postage_digital_stamp WHERE sale_id=? ORDER BY issued_at,stamp_id", stampMapper(), saleId);
        var audit = jdbc.query("SELECT * FROM postage_retail_audit WHERE sale_id=? ORDER BY occurred_at,event_id", auditMapper(), saleId);
        return new Detail(sale, receipt(saleId), stamps, audit);
    }

    public Stamp lookup(String code) {
        var rows = jdbc.query("SELECT * FROM postage_digital_stamp WHERE code=?", stampMapper(), StampCodeGenerator.normalize(code));
        if (rows.isEmpty()) missing("数码邮票不存在");
        return rows.get(0);
    }

    public Stamp voidStamp(String stampId, VoidRequest request, JwtUserContext actor) {
        admin(actor);
        requestId(request.requestId());
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank() || reason.length() > 500) invalid("请填写不超过 500 字的作废原因");
        var rows = jdbc.query("SELECT * FROM postage_digital_stamp WHERE stamp_id=? FOR UPDATE", stampMapper(), stampId);
        if (rows.isEmpty()) missing("数码邮票不存在");
        var stamp = rows.get(0);
        if ("VOIDED".equals(stamp.status())) {
            String oldRequest = jdbc.queryForObject("SELECT void_request_id FROM postage_digital_stamp WHERE stamp_id=?", String.class, stampId);
            if (!request.requestId().equals(oldRequest) || !reason.equals(stamp.voidReason()) || !actor.getUsername().equals(stamp.voidedBy())) conflict("此凭证已作废，请查询原记录");
            return stamp;
        }
        // Reserved and redeemed stamps cannot be voided.
        if (!"ISSUED".equals(stamp.status())) conflict("此凭证不能作废");
        Instant now = clock.instant();
        jdbc.update("UPDATE postage_digital_stamp SET status='VOIDED',voided_by=?,voided_at=?,void_reason=?,void_request_id=? WHERE stamp_id=?",
                actor.getUsername(), now.toEpochMilli(), reason, request.requestId(), stampId);
        audit("STAMP", stampId, stamp.saleId(), "STAMP_VOIDED", actor, now, reason);
        return lookup(stamp.code());
    }

    private void lockPolicy() { jdbc.queryForObject("SELECT id FROM postage_stamp_policy WHERE id=1 FOR UPDATE", Integer.class); }
    private Sale lockSale(String saleId) {
        var rows = jdbc.query("SELECT * FROM postage_sale WHERE sale_id=? FOR UPDATE", saleMapper(), saleId);
        if (rows.isEmpty()) missing("售卖记录不存在");
        return rows.get(0);
    }
    private Receipt receipt(String saleId) {
        var rows = jdbc.query("SELECT * FROM postage_receipt WHERE sale_id=?", (rs, n) -> new Receipt(rs.getString("receipt_id"), rs.getString("request_id"), rs.getString("method"), rs.getBigDecimal("amount_received"), rs.getString("confirmed_by"), rs.getString("facility_code"), instant(rs, "confirmed_at")), saleId);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private RowMapper<Sale> saleMapper() {
        return (rs, n) -> new Sale(rs.getString("sale_id"), rs.getString("request_id"), rs.getString("status"), rs.getString("currency"), rs.getBigDecimal("total_amount"), readLines(rs.getString("lines_json")), rs.getInt("policy_revision"), rs.getInt("validity_days"), rs.getString("created_by"), rs.getString("facility_code"), instant(rs, "created_at"), instant(rs, "paid_at"));
    }
    private RowMapper<Stamp> stampMapper() {
        return (rs, n) -> {
            String status = rs.getString("status");
            Instant expires = instant(rs, "expires_at");
            String display = "ISSUED".equals(status) && !clock.instant().isBefore(expires) ? "EXPIRED" : status;
            return new Stamp(rs.getString("stamp_id"), rs.getString("code"), rs.getString("sale_id"), rs.getString("currency"), rs.getBigDecimal("face_value"), status, display, instant(rs, "issued_at"), expires, rs.getString("voided_by"), instant(rs, "voided_at"), rs.getString("void_reason"));
        };
    }
    private RowMapper<Audit> auditMapper() {
        return (rs, n) -> new Audit(rs.getString("event_id"), rs.getString("subject_type"), rs.getString("subject_id"), rs.getString("event_type"), rs.getString("actor"), rs.getString("facility_code"), instant(rs, "occurred_at"), rs.getString("detail"));
    }
    private void audit(String type, String subject, String saleId, String event, JwtUserContext actor, Instant now, String detail) {
        jdbc.update("INSERT INTO postage_retail_audit (event_id,subject_type,subject_id,sale_id,event_type,actor,facility_code,occurred_at,detail) VALUES (?,?,?,?,?,?,?,?,?)",
                id(), type, subject, saleId, event, actor.getUsername(), actor.getFacilityCode(), now.toEpochMilli(), detail);
    }
    private List<Line> normalizeLines(List<Line> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > 50) invalid("请输入 1–50 行面额");
        int count = 0;
        var normalized = new java.util.ArrayList<Line>();
        for (var line : requested) {
            if (line == null || line.quantity() < 1 || line.quantity() > 100) invalid("张数必须为 1–100 的整数");
            count += line.quantity();
            normalized.add(new Line(money(line.faceValue()), line.quantity()));
        }
        if (count > 100) invalid("单次售卖最多发行 100 张");
        return List.copyOf(normalized);
    }
    private BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0) invalid("金额必须为正数且最多两位小数");
        return amount.setScale(2);
    }
    private void owner(Sale sale, JwtUserContext actor) {
        if (!"ADMIN".equals(actor.getRole()) && !sale.createdBy().equals(actor.getUsername())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查询或操作自己的售卖记录");
    }
    private void admin(JwtUserContext actor) {
        if (!"ADMIN".equals(actor.getRole())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "此操作需要管理员权限");
    }
    private void requestId(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) invalid("请求编号无效"); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : Instant.ofEpochMilli(((Number) value).longValue());
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize retail record", e); }
    }
    private List<Line> readLines(String value) {
        try { return json.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read retail record", e); }
    }
    private static void invalid(String message) { throw new IllegalArgumentException(message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void missing(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
