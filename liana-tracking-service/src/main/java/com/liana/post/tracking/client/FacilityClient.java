package com.liana.post.tracking.client;

import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "liana-facility-service", contextId = "facilityClient", url = "${facility.service.url:}")
public interface FacilityClient {
    @GetMapping("/api/facilities/placeholder")
    Result<String> ping(@RequestParam("code") String code);
}