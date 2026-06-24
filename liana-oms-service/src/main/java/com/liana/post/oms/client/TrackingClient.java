package com.liana.post.oms.client;

import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-tracking-service", contextId = "omsTrackingClient", url = "${tracking.service.url:}", fallbackFactory = TrackingClientFallbackFactory.class)
public interface TrackingClient {

    @PostMapping("/api/records/events")
    Result<?> recordEvent(@RequestBody TrackingEventCreateRequest request);
}
