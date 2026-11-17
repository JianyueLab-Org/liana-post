package com.liana.post.oms.controller;

import com.liana.post.common.dto.dispatch.PackingConfirmation;
import com.liana.post.common.model.Result;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.oms.service.PackingConfirmationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/oms/internal/packings")
public class PackingController {
    private final PackingConfirmationService packing;
    private final String internalToken;
    public PackingController(PackingConfirmationService packing, @Value("${packing.internal-token:}") String internalToken) {
        this.packing = packing; this.internalToken = internalToken;
    }
    @PostMapping
    public Result<Boolean> confirm(@RequestBody PackingConfirmation request,
            @RequestHeader(name = "X-Packing-Token", required = false) String token) {
        if (internalToken.isBlank() || token == null || !MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401, "packing service credentials are required");
        }
        return Result.ok(packing.accept(request));
    }
}
