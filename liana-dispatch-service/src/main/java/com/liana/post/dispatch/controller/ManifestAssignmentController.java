package com.liana.post.dispatch.controller;

import com.liana.post.common.dto.dispatch.ManifestAllocation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.dispatch.service.DispatchManifestAssignmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/dispatch/internal")
public class ManifestAssignmentController {
    private final DispatchManifestAssignmentService service;
    private final String token;
    public ManifestAssignmentController(DispatchManifestAssignmentService service, @Value("${manifest.internal-token:}") String token) {
        this.service = service; this.token = token;
    }
    @GetMapping("/manifests/{manifestNo}/transport")
    public Result<ManifestAllocation.Snapshot> snapshot(@PathVariable("manifestNo") String no,
            @RequestHeader(name = "X-Manifest-Token", required = false) String supplied) {
        authorize(supplied); return Result.ok(service.snapshot(no));
    }
    @PostMapping("/manifest-assignments")
    public Result<ManifestAllocation.Receipt> apply(@RequestBody ManifestAllocation.Command command,
            @RequestHeader(name = "X-Manifest-Token", required = false) String supplied) {
        authorize(supplied); return Result.ok(service.apply(command));
    }
    private void authorize(String supplied) {
        if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401, "valid manifest service credential required");
        }
    }

    @PostMapping("/bag-receipts")
    public Result<com.liana.post.common.dto.sorting.ManifestReceipt.Ack> receive(
            @RequestBody com.liana.post.common.dto.sorting.ManifestReceipt.Event event,
            @RequestHeader(name = "X-Manifest-Token", required = false) String supplied) {
        authorize(supplied); return Result.ok(service.receive(event));
    }

    @PostMapping("/manifest-closures")
    public Result<com.liana.post.common.dto.sorting.ManifestClosure.Ack> close(
            @RequestBody com.liana.post.common.dto.sorting.ManifestClosure.Command command,
            @RequestHeader(name = "X-Manifest-Token", required = false) String supplied) {
        authorize(supplied); return Result.ok(service.close(command));
    }
}
