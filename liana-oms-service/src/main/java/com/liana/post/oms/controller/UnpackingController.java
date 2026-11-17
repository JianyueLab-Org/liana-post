package com.liana.post.oms.controller;

import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.oms.service.UnpackingConfirmationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/oms/internal/unpackings")
public class UnpackingController {
    private final UnpackingConfirmationService service;
    private final String token;
    public UnpackingController(UnpackingConfirmationService service, @Value("${packing.internal-token:}") String token) {
        this.service = service; this.token = token;
    }
    @PostMapping
    public Result<UnpackingConfirmation.Ack> accept(@RequestBody UnpackingConfirmation command,
            @RequestHeader(name = "X-Packing-Token", required = false) String supplied) {
        if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401, "unpacking service credentials are required");
        }
        return Result.ok(service.accept(command));
    }
}
