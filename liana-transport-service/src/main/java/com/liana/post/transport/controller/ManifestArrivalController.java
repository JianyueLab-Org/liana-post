package com.liana.post.transport.controller;

import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.dto.sorting.ManifestReceipt;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.transport.service.ManifestArrivalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/transport/internal")
public class ManifestArrivalController {
    private final ManifestArrivalService service;
    private final String token;
    public ManifestArrivalController(ManifestArrivalService service, @Value("${manifest.internal-token:}") String token) { this.service = service; this.token = token; }
    @GetMapping("/manifests/{manifestNo}/arrival")
    public Result<ManifestReceipt.Arrival> arrival(@PathVariable("manifestNo") String no, @RequestHeader(name="X-Manifest-Token", required=false) String supplied) {
        authorize(supplied); return Result.ok(service.arrival(ManifestAllocation.code(no)));
    }
    @PostMapping("/bag-receipts")
    public Result<ManifestReceipt.Ack> receive(@RequestBody ManifestReceipt.Event event, @RequestHeader(name="X-Manifest-Token", required=false) String supplied) {
        authorize(supplied); return Result.ok(service.receive(event));
    }
    @PostMapping("/manifest-closures")
    public Result<com.liana.post.common.dto.sorting.ManifestClosure.Ack> close(
            @RequestBody com.liana.post.common.dto.sorting.ManifestClosure.Command command,
            @RequestHeader(name="X-Manifest-Token", required=false) String supplied) {
        authorize(supplied); return Result.ok(service.close(command));
    }
    private void authorize(String supplied) {
        if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new BusinessException(401, "valid manifest service credential required");
    }
}
