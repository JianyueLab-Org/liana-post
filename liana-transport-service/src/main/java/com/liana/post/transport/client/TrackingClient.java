package com.liana.post.transport.client;

import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-tracking-service", contextId = "transportTrackingClient", url = "${tracking.service.url:}")
public interface TrackingClient {
    @PostMapping("/api/records/events")
    Result<Object> recordEvent(@RequestBody TrackingEventCreateRequest request);
}
