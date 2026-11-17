package com.liana.post.transport.client;

import com.liana.post.common.dto.dispatch.DispatchBagBriefResponse;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.liana.post.common.dto.dispatch.ManifestAllocation;

@FeignClient(name = "liana-dispatch-service", contextId = "transportDispatchClient", url = "${dispatch.service.url:}")
public interface DispatchClient {
    @GetMapping("/api/dispatch/internal/manifests/{manifestNo}/transport")
    Result<ManifestAllocation.Snapshot> manifest(@PathVariable("manifestNo") String manifestNo, @RequestHeader("X-Manifest-Token") String token);

    @PostMapping("/api/dispatch/internal/manifest-assignments")
    Result<ManifestAllocation.Receipt> allocation(@RequestBody ManifestAllocation.Command command, @RequestHeader("X-Manifest-Token") String token);
    @GetMapping("/api/dispatch/bags/by-id/{id}")
    Result<DispatchBagBriefResponse> getBagById(@PathVariable("id") Long id);

}
