package com.liana.post.postage.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.liana.post.common.dto.postage.AcceptanceOutcome;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="liana-oms-service", contextId="postageOmsAcceptance", url="${oms.service.url:}")
public interface OmsAcceptanceClient {
    @PostMapping("/api/oms/acceptances/validate")
    Result<JsonNode> validate(@RequestBody JsonNode mail, @RequestHeader("Authorization") String auth);
    @PostMapping("/api/oms/acceptances/{id}/complete")
    Result<AcceptanceOutcome> complete(@PathVariable("id") String id, @RequestHeader("Authorization") String auth);
    @PostMapping("/api/oms/acceptances/{id}/abort")
    Result<AcceptanceOutcome> abort(@PathVariable("id") String id, @RequestHeader("Authorization") String auth);
}
