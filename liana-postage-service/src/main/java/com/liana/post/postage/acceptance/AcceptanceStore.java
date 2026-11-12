package com.liana.post.postage.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.postage.retail.RetailService;
import com.liana.post.postage.retail.StampCodeGenerator;
import com.liana.post.postage.tariff.QuoteModels;
import com.liana.post.postage.tariff.TariffService;
import com.liana.post.postage.tariff.TariffStore;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import static com.liana.post.postage.acceptance.AcceptanceModels.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class AcceptanceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TariffService tariffs;
    private final TariffStore quotes;
    private final RetailService retail;
    private final Clock clock;
    public AcceptanceStore(JdbcTemplate jdbc, ObjectMapper json, TariffService tariffs, TariffStore quotes,
                           RetailService retail, Clock clock) {
        this.jdbc=jdbc; this.json=json; this.tariffs=tariffs; this.quotes=quotes; this.retail=retail; this.clock=clock;
    }
    public Request normalize(Request r) {
        if (r.requestId()==null || !r.requestId().matches("[A-Za-z0-9_-]{1,64}") || r.quoteId()==null || r.mail()==null
                || !r.mail().isObject() || r.stampCodes()==null || r.stampCodes().size()>100
                || r.traditionalStamps()==null || r.traditionalStamps().size()>50) invalid("收寄结算内容无效");
        var codes=r.stampCodes().stream().map(StampCodeGenerator::normalize).sorted().toList();
        if (new HashSet<>(codes).size()!=codes.size()) invalid("同一邮票代码不能重复贴用");
        var traditional=new ArrayList<Traditional>();
        for (var t:r.traditionalStamps()) {
            if (t==null || t.faceValue()==null || t.faceValue().signum()<=0 || t.faceValue().stripTrailingZeros().scale()>2
                    || t.faceValue().compareTo(new BigDecimal("999999.99"))>0 || t.quantity()<1 || t.quantity()>1000) invalid("传统邮票面额或张数无效");
            traditional.add(new Traditional(t.faceValue().setScale(2),t.quantity()));
        }
        if (!traditional.isEmpty() && !r.traditionalConfirmed()) invalid("请先人工鉴别传统邮票并确认贴用");
        return new Request(r.requestId(),r.quoteId(),r.mail(),codes,List.copyOf(traditional),r.traditionalConfirmed());
    }
    public View previous(Request request, JwtUserContext actor) {
        var ids=jdbc.queryForList("SELECT acceptance_id FROM postage_acceptance WHERE created_by=? AND request_id=?",String.class,actor.getUsername(),request.requestId());
        if (ids.isEmpty()) return null;
        checkRequest(ids.get(0),request);
        return get(ids.get(0),actor);
    }
    public View prepare(Request request, JsonNode validatedMail, JwtUserContext actor) {
        // No remote calls inside this transaction. Serializes idempotency keys, then locks vouchers in code order.
        jdbc.queryForObject("SELECT id FROM postage_acceptance_lock WHERE id=1 FOR UPDATE",Integer.class);
        var previous=previous(request,actor);
        if (previous!=null) return previous;
        if (actor.getFacilityCode()==null || actor.getFacilityCode().isBlank()) invalid("工作人员必须绑定网点");
        var view=tariffs.quote(request.quoteId());
        if (!view.valid()) conflict("报价已失效，请重新报价后结算");
        var quote=view.snapshot();
        if (!quote.createdBy().equals(actor.getUsername())) forbidden("只能使用自己创建的报价");
        var m=validatedMail;
        var expected=new QuoteModels.Request(m.path("mailTypeCode").asText(),m.path("mailScope").asText(),m.path("serviceType").asText(),
                "INTERNATIONAL".equals(m.path("mailScope").asText())?m.path("destCountryCode").asText():"LN",
                m.path("weightGrams").asInt(),json.convertValue(m.path("additionalServices"),json.getTypeFactory().constructCollectionType(List.class,String.class)));
        var quotedInput=quote.input();
        // Legacy R quotes imply registration even when their stored service list is empty.
        if ("R".equals(quotedInput.product()) && quotedInput.additionalServices().isEmpty()) {
            quotedInput=new QuoteModels.Request(quotedInput.product(),quotedInput.scope(),quotedInput.mode(),
                    quotedInput.destinationCountry(),quotedInput.weightGrams(),List.of("REGISTRATION"));
        }
        if (!quotedInput.equals(expected)) conflict("报价条件与收寄内容不一致，请重新报价");
        BigDecimal traditional=BigDecimal.ZERO.setScale(2), digital=BigDecimal.ZERO.setScale(2);
        for (var t:request.traditionalStamps()) traditional=traditional.add(t.faceValue().multiply(BigDecimal.valueOf(t.quantity())));
        var stamps=new ArrayList<com.liana.post.postage.retail.RetailModels.Stamp>();
        for (var code:request.stampCodes()) {
            var locked=jdbc.queryForList("SELECT stamp_id FROM postage_digital_stamp WHERE code=? FOR UPDATE",String.class,code);
            if (locked.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"数码邮票不存在");
            var stamp=retail.lookup(code);
            if (!"ISSUED".equals(stamp.status()) || !clock.instant().isBefore(stamp.expiresAt())) conflict("邮票已预占、核销、过期或作废，不能贴用");
            if (!quote.currency().equals(stamp.currency())) invalid("邮票币种与报价不一致");
            stamps.add(stamp); digital=digital.add(stamp.faceValue());
        }
        BigDecimal applied=traditional.add(digital);
        if (applied.compareTo(quote.calculation().totalAmount())<0) conflict("邮资不足，请先补购差额邮票再结算");
        if (applied.compareTo(new BigDecimal("9999999999.99"))>0) invalid("贴用邮资超过系统金额上限");
        var id=UUID.randomUUID().toString();
        Instant now=clock.instant();
        jdbc.update("INSERT INTO postage_acceptance (acceptance_id,request_id,quote_id,status,request_json,mail_json,traditional_amount,digital_amount,applied_amount,excess_amount,created_by,facility_code,created_at) VALUES (?,?,?,'PREPARED',?,?,?,?,?,?,?,?,?)",
                id,request.requestId(),request.quoteId(),write(request),write(m),traditional,digital,applied,
                applied.subtract(quote.calculation().totalAmount()),actor.getUsername(),actor.getFacilityCode(),now.toEpochMilli());
        for (var stamp:stamps) {
            jdbc.update("INSERT INTO postage_acceptance_stamp (acceptance_id,stamp_id) VALUES (?,?)",id,stamp.stampId());
            jdbc.update("UPDATE postage_digital_stamp SET status='RESERVED' WHERE stamp_id=?",stamp.stampId());
            audit("STAMP",stamp.stampId(),stamp.saleId(),"STAMP_RESERVED",actor,id);
        }
        audit("ACCEPTANCE",id,null,"ACCEPTANCE_PREPARED",actor,applied.toPlainString());
        return get(id,actor);
    }
    public View get(String id, JwtUserContext actor) {
        var view=load(id);
        if (!"ADMIN".equals(actor.getRole()) && !actor.getUsername().equals(view.createdBy())) forbidden("只能恢复自己的收寄结算");
        return view;
    }
    public View byRequest(String requestId, JwtUserContext actor) {
        var ids=jdbc.queryForList("SELECT acceptance_id FROM postage_acceptance WHERE created_by=? AND request_id=?",String.class,actor.getUsername(),requestId);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"尚未找到此请求的结算记录，可用原请求编号重试");
        return get(ids.get(0),actor);
    }
    private View load(String id) {
        var rows=jdbc.queryForList("SELECT * FROM postage_acceptance WHERE acceptance_id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"收寄结算不存在");
        var row=rows.get(0);
        Request request=read((String)row.get("request_json"),Request.class);
        var codes=jdbc.queryForList("SELECT s.code FROM postage_digital_stamp s JOIN postage_acceptance_stamp a ON a.stamp_id=s.stamp_id WHERE a.acceptance_id=? ORDER BY s.code",String.class,id);
        return new View(id,(String)row.get("status"),(String)row.get("created_by"),(String)row.get("facility_code"),
                read((String)row.get("mail_json"),JsonNode.class),quotes.quote((String)row.get("quote_id")),request.traditionalStamps(),
                codes.stream().map(retail::lookup).toList(),(BigDecimal)row.get("traditional_amount"),(BigDecimal)row.get("digital_amount"),
                (BigDecimal)row.get("applied_amount"),(BigDecimal)row.get("excess_amount"),(String)row.get("waybill_no"),
                time(row.get("created_at")),time(row.get("completed_at")));
    }
    public List<View> list(JwtUserContext actor, int page) {
        if (page<0 || page>10000) invalid("分页参数无效");
        var ids="ADMIN".equals(actor.getRole())
                ?jdbc.queryForList("SELECT acceptance_id FROM postage_acceptance ORDER BY created_at DESC,acceptance_id LIMIT 20 OFFSET ?",String.class,page*20)
                :jdbc.queryForList("SELECT acceptance_id FROM postage_acceptance WHERE created_by=? ORDER BY created_at DESC,acceptance_id LIMIT 20 OFFSET ?",String.class,actor.getUsername(),page*20);
        // Lock in a consistent order to avoid deadlocks with concurrent list requests.
        var views=new HashMap<String,View>();
        ids.stream().sorted().forEach(id->views.put(id,get(id,actor)));
        return ids.stream().map(views::get).toList();
    }
    public Summary byWaybill(String waybill, JwtUserContext actor) {
        var ids=jdbc.queryForList("SELECT acceptance_id FROM postage_acceptance WHERE waybill_no=?",String.class,waybill);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"此邮件没有邮资结算记录（可能为历史邮件）");
        // All authorized counter staff may inspect amounts; full codes and personal data stay owner/admin only.
        var v=load(ids.get(0));
        return new Summary(v.acceptanceId(),v.status(),v.waybillNo(),v.quote(),v.traditionalStamps(),v.traditionalAmount(),v.digitalAmount(),v.appliedAmount(),v.excessAmount(),v.createdBy(),v.facilityCode(),v.completedAt());
    }
    public View finish(String id, String waybill, JwtUserContext actor) {
        var v=get(id,actor);
        if ("COMPLETED".equals(v.status())) {
            if (!Objects.equals(waybill,v.waybillNo())) conflict("运单号与原结算不一致");
            return v;
        }
        if (!"PREPARED".equals(v.status()) || waybill==null || !waybill.matches("[A-Z]{2}[0-9]{9}[A-Z]{2}")) conflict("结算状态或运单号无效");
        for (var stamp:v.stamps()) {
            int count=jdbc.update("UPDATE postage_digital_stamp SET status='REDEEMED' WHERE stamp_id=? AND status='RESERVED'",stamp.stampId());
            if (count!=1) conflict("邮票预占状态异常");
            audit("STAMP",stamp.stampId(),stamp.saleId(),"STAMP_REDEEMED",actor,waybill);
        }
        jdbc.update("UPDATE postage_acceptance SET status='COMPLETED',waybill_no=?,completed_at=? WHERE acceptance_id=?",waybill,clock.instant().toEpochMilli(),id);
        audit("ACCEPTANCE",id,null,"ACCEPTANCE_COMPLETED",actor,waybill);
        return get(id,actor);
    }
    public View release(String id, JwtUserContext actor) {
        var v=get(id,actor);
        if ("CANCELLED".equals(v.status())) return v;
        if (!"PREPARED".equals(v.status())) conflict("已完成的收寄不能取消");
        for (var stamp:v.stamps()) {
            if (jdbc.update("UPDATE postage_digital_stamp SET status='ISSUED' WHERE stamp_id=? AND status='RESERVED'",stamp.stampId())!=1) conflict("邮票预占状态异常");
            audit("STAMP",stamp.stampId(),stamp.saleId(),"STAMP_RELEASED",actor,id);
        }
        jdbc.update("UPDATE postage_acceptance SET status='CANCELLED' WHERE acceptance_id=?",id);
        audit("ACCEPTANCE",id,null,"ACCEPTANCE_CANCELLED",actor,"OMS confirmed cancellation");
        return get(id,actor);
    }
    private void checkRequest(String id, Request request) {
        var stored=jdbc.queryForObject("SELECT request_json FROM postage_acceptance WHERE acceptance_id=?",String.class,id);
        if (!read(stored,Request.class).equals(request)) conflict("请求编号已用于不同收寄内容");
    }
    private void audit(String type,String subject,String sale,String event,JwtUserContext actor,String detail) {
        jdbc.update("INSERT INTO postage_retail_audit (event_id,subject_type,subject_id,sale_id,event_type,actor,facility_code,occurred_at,detail) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),type,subject,sale,event,actor.getUsername(),actor.getFacilityCode(),clock.instant().toEpochMilli(),detail);
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Cannot serialize acceptance",e); } }
    private <T> T read(String value,Class<T> type) { try { return json.readValue(value,type); } catch (Exception e) { throw new IllegalStateException("Cannot read acceptance",e); } }
    private static Instant time(Object value) { return value==null?null:Instant.ofEpochMilli(((Number)value).longValue()); }
    private static void invalid(String message) { throw new IllegalArgumentException(message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,message); }
}
