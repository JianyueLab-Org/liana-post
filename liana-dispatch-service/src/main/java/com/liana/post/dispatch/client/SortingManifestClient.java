package com.liana.post.dispatch.client;

import com.liana.post.common.dto.sorting.ManifestDTO;
import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "liana-sorting-service", contextId = "dispatchSortingManifestClient", url = "${sorting.service.url:}")
public interface SortingManifestClient {
    @PostMapping("/api/v1/sorting/manifest/sync")
    Result<Boolean> syncManifest(@RequestBody ManifestDTO request);
}
