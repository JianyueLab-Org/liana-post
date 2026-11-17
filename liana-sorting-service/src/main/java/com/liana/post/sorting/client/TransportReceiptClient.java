package com.liana.post.sorting.client;

import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "liana-transport-service", contextId = "sortingTransportReceiptClient", url = "${transport.service.url:}")
public interface TransportReceiptClient {
    @GetMapping("/api/transport/internal/manifests/{manifestNo}/arrival")
    Result<ManifestReceipt.Arrival> arrival(@PathVariable("manifestNo") String manifestNo, @RequestHeader("X-Manifest-Token") String token);

    @PostMapping("/api/transport/internal/bag-receipts")
    Result<ManifestReceipt.Ack> receive(@RequestBody ManifestReceipt.Event event, @RequestHeader("X-Manifest-Token") String token);

    @PostMapping("/api/transport/internal/manifest-closures")
    Result<com.liana.post.common.dto.sorting.ManifestClosure.Ack> close(
            @RequestBody com.liana.post.common.dto.sorting.ManifestClosure.Command command,
            @RequestHeader("X-Manifest-Token") String token);
}
