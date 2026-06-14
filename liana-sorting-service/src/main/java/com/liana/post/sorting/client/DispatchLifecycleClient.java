package com.liana.post.sorting.client;

import com.liana.post.common.dto.sorting.ManifestArrivedRequest;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-dispatch-service", contextId = "sortingDispatchLifecycleClient", url = "${dispatch.service.url:}")
public interface DispatchLifecycleClient {
    @PostMapping("/api/dispatch/manifests/arrived")
    Result<Boolean> markManifestArrived(@RequestBody ManifestArrivedRequest request);
}
