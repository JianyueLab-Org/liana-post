package com.liana.post.gateway.filter;

import com.liana.post.common.model.JwtUserContext;
import com.liana.post.common.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_DISPLAY_NAME = "X-Display-Name";
    private static final String HEADER_ROLE = "X-User-Role";
    private static final String HEADER_FACILITY_CODE = "X-Facility-Code";
    private static final String HEADER_PERMISSIONS = "X-User-Permissions";

    private final List<String> whitelistPaths;

    public GatewayAuthenticationFilter(
            @Value("${gateway.auth.whitelist-paths:/api/auth/login,/liana-auth-service/api/auth/login,/actuator/health,/actuator/info}")
            List<String> whitelistPaths) {
        this.whitelistPaths = whitelistPaths;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String token = resolveBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (!StringUtils.hasText(token) || !JwtTokenUtil.validateToken(token)) {
            return unauthorized(exchange, "unauthorized");
        }

        JwtUserContext userContext = JwtTokenUtil.getUserContextFromToken(token);
        if (userContext == null || !StringUtils.hasText(userContext.getUsername())) {
            return unauthorized(exchange, "invalid token");
        }

        ServerHttpRequest authenticatedRequest = request.mutate()
                .headers(headers -> applyUserHeaders(headers, userContext))
                .build();
        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean isWhitelisted(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        for (String whitelistPath : whitelistPaths) {
            if (!StringUtils.hasText(whitelistPath)) {
                continue;
            }
            String normalized = whitelistPath.trim();
            if (path.equals(normalized) || path.endsWith(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String resolveBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    private void applyUserHeaders(HttpHeaders headers, JwtUserContext userContext) {
        headers.remove(HEADER_USER_ID);
        headers.remove(HEADER_USERNAME);
        headers.remove(HEADER_DISPLAY_NAME);
        headers.remove(HEADER_ROLE);
        headers.remove(HEADER_FACILITY_CODE);
        headers.remove(HEADER_PERMISSIONS);

        if (userContext.getUserId() != null) {
            headers.set(HEADER_USER_ID, String.valueOf(userContext.getUserId()));
        }
        headers.set(HEADER_USERNAME, userContext.getUsername());
        if (StringUtils.hasText(userContext.getDisplayName())) {
            headers.set(HEADER_DISPLAY_NAME, userContext.getDisplayName());
        }
        if (StringUtils.hasText(userContext.getRole())) {
            headers.set(HEADER_ROLE, userContext.getRole());
        }
        if (StringUtils.hasText(userContext.getFacilityCode())) {
            headers.set(HEADER_FACILITY_CODE, userContext.getFacilityCode());
        }
        if (userContext.getPermissions() != null && !userContext.getPermissions().isEmpty()) {
            headers.set(HEADER_PERMISSIONS, String.join(",", userContext.getPermissions()));
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
