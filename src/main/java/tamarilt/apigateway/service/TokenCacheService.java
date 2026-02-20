package tamarilt.apigateway.service;

import reactor.core.publisher.Mono;

public interface TokenCacheService {

    Mono<String> getCachedUserId(String token);

    Mono<Boolean> cacheToken(String token, String userId, String role);

    Mono<String> getCachedRole(String token);
}
