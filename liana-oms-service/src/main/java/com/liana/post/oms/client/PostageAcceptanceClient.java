package com.liana.post.oms.client;

import com.liana.post.common.dto.postage.PostageAcceptanceData;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="liana-postage-service",contextId="omsPostageAcceptance",url="${postage.service.url:}")
public interface PostageAcceptanceClient {
    @GetMapping("/api/postage/acceptances/{id}")
    Result<PostageAcceptanceData> get(@PathVariable("id") String id,@RequestHeader("Authorization") String auth);
}
