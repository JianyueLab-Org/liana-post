package com.liana.post.sorting.controller;

import com.liana.post.common.dto.sorting.ManifestSealCommand;
import com.liana.post.common.exception.BusinessException;
import com.liana.post.common.model.Result;
import com.liana.post.sorting.service.ManifestSealService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class ManifestSealController {
    private final ManifestSealService service;
    private final String token;

    public ManifestSealController(ManifestSealService service,
            @Value("${manifest.internal-token:}") String token) {
        this.service = service;
        this.token = token;
    }

    @PostMapping("/api/v1/sorting/internal/manifests/seal")
    public Result<ManifestSealService.Receipt> seal(@RequestBody ManifestSealCommand command,
            @RequestHeader(name = "X-Manifest-Token", required = false) String supplied) {
        if (token.isBlank() || supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) throw new BusinessException(401, "valid manifest service credential required");
        return Result.ok(service.seal(command));
    }
}
