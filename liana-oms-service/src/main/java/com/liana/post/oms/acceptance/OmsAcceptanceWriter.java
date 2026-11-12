package com.liana.post.oms.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liana.post.common.dto.postage.AcceptanceOutcome;
import com.liana.post.common.dto.postage.PostageAcceptanceData;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.oms.model.dto.MailCreateRequest;
import com.liana.post.oms.service.OmsService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class OmsAcceptanceWriter {
    private final JdbcTemplate jdbc;
    private final OmsService oms;
    private final AcceptanceRules rules;
    private final ObjectMapper json;
    public OmsAcceptanceWriter(JdbcTemplate jdbc,OmsService oms,AcceptanceRules rules,ObjectMapper json) {
        this.jdbc=jdbc; this.oms=oms; this.rules=rules; this.json=json;
    }
    public AcceptanceOutcome complete(PostageAcceptanceData data,JwtUserContext actor) {
        var row=lock(data);
        if ("CREATED".equals(row.get("status"))) return outcome(row);
        if ("CANCELLED".equals(row.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"收寄已取消，不能创建邮件");
        if (!"PREPARED".equals(data.status())) throw new ResponseStatusException(HttpStatus.CONFLICT,"邮资尚未预占");
        var request=json.convertValue(data.mail(),MailCreateRequest.class);
        rules.validate(request,actor);
        // Keep the original counter facility stored in the authoritative prepared settlement.
        request.setOriginFacilityCode(data.facilityCode()); request.setCurrentFacilityCode(data.facilityCode());
        var mail=oms.createMail(request);
        jdbc.update("UPDATE oms_postage_acceptance SET status='CREATED',waybill_no=?,updated_at=? WHERE acceptance_id=?",mail.getWaybillNo(),System.currentTimeMillis(),data.acceptanceId());
        return new AcceptanceOutcome("CREATED",mail.getWaybillNo());
    }
    public AcceptanceOutcome abort(PostageAcceptanceData data) {
        var row=lock(data);
        if ("CREATED".equals(row.get("status"))) return outcome(row);
        jdbc.update("UPDATE oms_postage_acceptance SET status='CANCELLED',updated_at=? WHERE acceptance_id=?",System.currentTimeMillis(),data.acceptanceId());
        return new AcceptanceOutcome("CANCELLED",null);
    }
    private Map<String,Object> lock(PostageAcceptanceData data) {
        jdbc.update("INSERT INTO oms_postage_acceptance (acceptance_id,created_by,status,updated_at) VALUES (?,?,'OPEN',?) ON DUPLICATE KEY UPDATE acceptance_id=acceptance_id",
                data.acceptanceId(),data.createdBy(),System.currentTimeMillis());
        return jdbc.queryForMap("SELECT * FROM oms_postage_acceptance WHERE acceptance_id=? FOR UPDATE",data.acceptanceId());
    }
    private AcceptanceOutcome outcome(Map<String,Object> row) { return new AcceptanceOutcome((String)row.get("status"),(String)row.get("waybill_no")); }
}
