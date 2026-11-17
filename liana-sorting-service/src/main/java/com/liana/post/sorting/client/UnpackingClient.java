package com.liana.post.sorting.client;

import com.liana.post.common.dto.sorting.UnpackingConfirmation;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "liana-oms-service", contextId = "sortingUnpackingClient", url = "${oms.service.url:}")
public interface UnpackingClient {
    @PostMapping("/api/oms/internal/unpackings")
    Result<UnpackingConfirmation.Ack> accept(@RequestBody UnpackingConfirmation command, @RequestHeader("X-Packing-Token") String token);
}
