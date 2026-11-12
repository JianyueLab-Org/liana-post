package com.liana.post.sorting.client;

import com.liana.post.common.model.Result;
import com.liana.post.sorting.model.dto.FacilityRouteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@FeignClient(name = "liana-facility-service", contextId = "sortingFacilityClient", url = "${facility.service.url:}")
public interface FacilityClient {
    @GetMapping("/api/facilities/routes")
    Result<List<FacilityRouteResponse>> listRoutes();
}
