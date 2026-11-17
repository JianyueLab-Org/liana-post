package com.liana.post.sorting.client;

import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-dispatch-service", contextId = "sortingDispatchLifecycleClient", url = "${dispatch.service.url:}")
public interface DispatchLifecycleClient {
    @PostMapping("/api/dispatch/internal/repackings")
    Result<com.liana.post.common.dto.dispatch.RepackingCommand.Ack> repack(
            @RequestBody com.liana.post.common.dto.dispatch.RepackingCommand command,
            @org.springframework.web.bind.annotation.RequestHeader("X-Manifest-Token") String token);

    @PostMapping("/api/dispatch/internal/bag-forwarding")
    Result<com.liana.post.common.dto.dispatch.BagForwarding.Ack> forward(
            @RequestBody com.liana.post.common.dto.dispatch.BagForwarding.Command command,
            @org.springframework.web.bind.annotation.RequestHeader("X-Manifest-Token") String token);

    @PostMapping("/api/dispatch/internal/bag-receipts")
    Result<com.liana.post.common.dto.sorting.ManifestReceipt.Ack> receive(
            @RequestBody com.liana.post.common.dto.sorting.ManifestReceipt.Event event,
            @org.springframework.web.bind.annotation.RequestHeader("X-Manifest-Token") String token);

    @PostMapping("/api/dispatch/internal/manifest-closures")
    Result<com.liana.post.common.dto.sorting.ManifestClosure.Ack> close(
            @RequestBody com.liana.post.common.dto.sorting.ManifestClosure.Command command,
            @org.springframework.web.bind.annotation.RequestHeader("X-Manifest-Token") String token);
}
