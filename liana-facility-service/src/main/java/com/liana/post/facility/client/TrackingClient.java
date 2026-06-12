package com.liana.post.facility.client;

import com.liana.post.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 设施服务预留的轨迹查询客户端。
 * TODO: 后续对接 tracking-service 的真实接口路径。
 */
@FeignClient(name = "liana-tracking-service", contextId = "trackingClient", url = "${tracking.service.url:}")
public interface TrackingClient {

    @GetMapping("/api/tracking/placeholder")
    Result<String> ping(@RequestParam("code") String code);
}