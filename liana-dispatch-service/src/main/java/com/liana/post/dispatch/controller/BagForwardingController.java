package com.liana.post.dispatch.controller;

import com.liana.post.common.dto.dispatch.BagForwarding;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.dispatch.service.BagForwardingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/dispatch/internal/bag-forwarding")
public class BagForwardingController {
    private final BagForwardingService service;
    private final String token;
    public BagForwardingController(BagForwardingService service, @Value("${manifest.internal-token:}") String token) { this.service = service; this.token = token; }
    @PostMapping
    public Result<BagForwarding.Ack> accept(@RequestBody BagForwarding.Command command,
            @RequestHeader(name = "X-Manifest-Token", required = false) String supplied) {
        if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new BusinessException(401, "valid manifest service credential required");
        return Result.ok(service.accept(command));
    }
}
