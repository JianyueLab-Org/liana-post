package com.liana.post.dispatch.controller;

import com.liana.post.common.dto.dispatch.RepackingCommand;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.dispatch.service.RepackingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/dispatch/internal/repackings")
public class RepackingController {
    private final RepackingService service;
    private final String token;
    public RepackingController(RepackingService service, @Value("${manifest.internal-token:}") String token) { this.service = service; this.token = token; }
    @PostMapping
    public Result<RepackingCommand.Ack> accept(@RequestBody RepackingCommand command,
            @RequestHeader(name = "X-Manifest-Token", required = false) String supplied) {
        if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) throw new BusinessException(401, "repacking service credentials required");
        return Result.ok(service.accept(command));
    }
}
