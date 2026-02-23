package tamarilt.apigateway.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tamarilt.apigateway.config.JwtGatewayProperties;
import tamarilt.apigateway.service.TokenCacheService;

import java.time.Duration;

/**
 * Реализация сервиса кэширования JWT-токенов в Redis.
 * <p>
 * Сохраняет пары токен → userId и токен → role с TTL из конфигурации.
 * Используется {@code reactiveStringRedisTemplate} из автоконфигурации Spring
 * Boot.
 * </p>
 */
@Service
public class TokenCacheServiceImpl implements TokenCacheService {

    private static final String TOKEN_PREFIX = "jwt:";
    private static final String ROLE_SUFFIX = ":role";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JwtGatewayProperties gatewayProperties;

    public TokenCacheServiceImpl(
            @Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate,
            JwtGatewayProperties gatewayProperties) {
        this.redisTemplate = redisTemplate;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<String> getCachedUserId(String token) {
        String key = TOKEN_PREFIX + token;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public Mono<Boolean> cacheToken(String token, String userId, String role) {
        String userIdKey = TOKEN_PREFIX + token;
        String roleKey = TOKEN_PREFIX + token + ROLE_SUFFIX;
        Duration ttl = Duration.ofMinutes(gatewayProperties.getJwt().getCacheTtlMinutes());

        return redisTemplate.opsForValue().set(userIdKey, userId, ttl)
                .then(redisTemplate.opsForValue().set(roleKey, role, ttl));
    }

    @Override
    public Mono<String> getCachedRole(String token) {
        String key = TOKEN_PREFIX + token + ROLE_SUFFIX;
        return redisTemplate.opsForValue().get(key);
    }
}
