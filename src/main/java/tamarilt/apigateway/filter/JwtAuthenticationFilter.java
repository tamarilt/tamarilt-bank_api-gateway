package tamarilt.apigateway.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tamarilt.apigateway.config.JwtGatewayProperties;
import tamarilt.apigateway.service.EventProducer;
import tamarilt.apigateway.service.TokenCacheService;
import tamarilt.apigateway.service.UserServiceClient;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_ROLE = "X-User-Role";
    private static final String X_TRACE_ID = "X-Trace-Id";

    private final TokenCacheService tokenCacheService;
    private final UserServiceClient userServiceClient;
    private final JwtGatewayProperties gatewayProperties;
    private final EventProducer eventProducer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString();

        ServerHttpRequest requestWithTrace = exchange.getRequest().mutate()
                .header(X_TRACE_ID, traceId)
                .build();
        exchange = exchange.mutate().request(requestWithTrace).build();

        String path = exchange.getRequest().getURI().getPath();

        if (isOpenEndpoint(path)) {
            eventProducer.sendSuccess("ROUTE_REQUEST", Map.of(
                    "path", path, "traceId", traceId, "type", "open"));
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            eventProducer.sendError("AUTHENTICATE_REQUEST", Map.of(
                    "path", path, "traceId", traceId, "error", "Missing Authorization header"));
            return onUnauthorized(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        ServerWebExchange finalExchange = exchange;

        return tokenCacheService.getCachedUserId(token)
                .flatMap(cachedUserId -> tokenCacheService.getCachedRole(token)
                        .map(cachedRole -> addUserHeaders(finalExchange, cachedUserId, cachedRole)))
                .switchIfEmpty(Mono.defer(() -> validateViaUserService(token, traceId, finalExchange)))
                .flatMap(chain::filter)
                .onErrorResume(e -> {
                    eventProducer.sendError("AUTHENTICATE_REQUEST", Map.of(
                            "path", path, "traceId", traceId, "error", e.getMessage()));
                    return onUnauthorized(finalExchange);
                });
    }

    private Mono<ServerWebExchange> validateViaUserService(String token, String traceId,
            ServerWebExchange exchange) {
        return userServiceClient.validateToken(token)
                .flatMap(response -> {
                    String userId = response.getUserId();
                    String role = response.getRole();

                    eventProducer.sendSuccess("AUTHENTICATE_REQUEST", Map.of(
                            "traceId", traceId, "userId", userId, "role", role));

                    return tokenCacheService.cacheToken(token, userId, role)
                            .thenReturn(addUserHeaders(exchange, userId, role));
                });
    }

    private ServerWebExchange addUserHeaders(ServerWebExchange exchange, String userId, String role) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(X_USER_ID, userId)
                .header(X_USER_ROLE, role)
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    private boolean isOpenEndpoint(String path) {
        return gatewayProperties.getOpenEndpoints().stream()
                .anyMatch(path::startsWith);
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
