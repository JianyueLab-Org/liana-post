package com.liana.post.transport.client;

import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.dto.dispatch.DispatchTransportTaskLinkRequest;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-dispatch-service", contextId = "transportDispatchClient", url = "${dispatch.service.url:}")
public interface DispatchClient {
    @GetMapping("/api/dispatch/bags/by-id/{id}")
    Result<DispatchBagBriefResponse> getBagById(@PathVariable("id") Long id);

    @PostMapping("/api/dispatch/bags/by-id/{id}/transport-task")
    Result<DispatchBagBriefResponse> linkTransportTask(@PathVariable("id") Long id, @RequestBody DispatchTransportTaskLinkRequest request);
}
