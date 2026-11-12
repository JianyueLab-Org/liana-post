package com.liana.post.oms.client;

import com.liana.post.common.dto.tracking.TrackingEventCreateRequest;
import com.liana.post.common.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class TrackingClientFallbackFactory implements FallbackFactory<TrackingClient> {

    private static final Logger log = LoggerFactory.getLogger(TrackingClientFallbackFactory.class);

    @Override
    public TrackingClient create(Throwable cause) {
        return new TrackingClient() {
            @Override
            public Result<?> recordEvent(TrackingEventCreateRequest request) {
                log.warn("Feign fallback triggered: client=TrackingClient operation=recordEvent waybillNo={} reason={}",
                        request == null ? null : request.getWaybillNo(),
                        cause == null ? "unknown" : cause.toString());
                return Result.fail("轨迹服务暂不可用，轨迹事件未记录");
            }
        };
    }
}
