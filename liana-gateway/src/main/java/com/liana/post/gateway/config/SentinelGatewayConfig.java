package com.liana.post.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.liana.post.common.model.Result;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Set;

@Configuration
public class SentinelGatewayConfig {

    private final String routeId;
    private final double qps;
    private final String blockMessage;

    public SentinelGatewayConfig(@Value("${liana.sentinel.gateway.route-id:sorting-alias}") String routeId,
                                 @Value("${liana.sentinel.gateway.qps:2}") double qps,
                                 @Value("${liana.sentinel.gateway.block-message:请求过于频繁，请稍后再试}") String blockMessage) {
        this.routeId = routeId;
        this.qps = qps;
        this.blockMessage = blockMessage;
    }

    @PostConstruct
    public void initGatewayRules() {
        GatewayFlowRule sortingRouteRule = new GatewayFlowRule(routeId)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setIntervalSec(1);
        GatewayRuleManager.loadRules(Set.of(sortingRouteRule));

        GatewayCallbackManager.setBlockHandler((exchange, throwable) -> {
            Result<Void> body = Result.error(blockMessage, HttpStatus.TOO_MANY_REQUESTS.value());
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(body));
        });
    }
}
