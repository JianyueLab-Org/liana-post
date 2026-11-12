package com.liana.post.oms.acceptance;

import com.fasterxml.jackson.databind.*;
import com.liana.post.common.dto.postage.AcceptanceOutcome;
import com.liana.post.common.dto.postage.PostageAcceptanceData;
import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.model.Result;
import com.liana.post.oms.client.PostageAcceptanceClient;
import com.liana.post.oms.model.dto.MailCreateRequest;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/oms/acceptances")
public class OmsAcceptanceController {
    private final AcceptanceAccess access;
    private final AcceptanceRules rules;
    private final OmsAcceptanceWriter writer;
    private final PostageAcceptanceClient postage;
    private final ObjectMapper json;
    public OmsAcceptanceController(AcceptanceAccess access,AcceptanceRules rules,OmsAcceptanceWriter writer,PostageAcceptanceClient postage,ObjectMapper json) {
        this.access=access; this.rules=rules; this.writer=writer; this.postage=postage; this.json=json;
    }
    @PostMapping("/validate")
    public Result<JsonNode> validate(@RequestBody JsonNode body,@RequestHeader(value="Authorization",required=false) String auth) {
        var actor=access.user(auth);
        MailCreateRequest request;
        try { request=json.readerFor(MailCreateRequest.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(body); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"收寄字段无效，重量须为整数克"); }
        return Result.ok(json.valueToTree(rules.validate(request,actor)));
    }
    @PostMapping("/{id}/complete")
    public Result<AcceptanceOutcome> complete(@PathVariable("id") String id,@RequestHeader(value="Authorization",required=false) String auth) {
        var actor=access.user(auth);
        var data=load(id,actor,auth);
        return Result.ok(writer.complete(data,actor));
    }
    @PostMapping("/{id}/abort")
    public Result<AcceptanceOutcome> abort(@PathVariable("id") String id,@RequestHeader(value="Authorization",required=false) String auth) {
        var actor=access.user(auth);
        return Result.ok(writer.abort(load(id,actor,auth)));
    }
    private PostageAcceptanceData load(String id,JwtUserContext actor,String auth) {
        // This HTTP read completes before the writer starts its local OMS transaction.
        try {
            var result=postage.get(id,auth);
            if (result==null || result.getCode()==null || result.getCode()!=200 || result.getData()==null)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"无法确认邮资预占，保留原请求重试");
            var data=result.getData();
            if (!id.equals(data.acceptanceId()) || (!"ADMIN".equals(actor.getRole()) && !actor.getUsername().equals(data.createdBy())))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"结算身份不匹配");
            return data;
        } catch (FeignException e) {
            int code=e.status()>=400 && e.status()<500?e.status():503;
            throw new ResponseStatusException(HttpStatus.valueOf(code),"无法确认邮资预占，请恢复原结算请求");
        }
    }
}
