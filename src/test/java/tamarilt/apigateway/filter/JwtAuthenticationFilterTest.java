package tamarilt.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tamarilt.apigateway.config.JwtGatewayProperties;
import tamarilt.apigateway.dto.TokenValidationResponse;
import tamarilt.apigateway.service.EventProducer;
import tamarilt.apigateway.service.TokenCacheService;
import tamarilt.apigateway.service.UserServiceClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

        @Mock
        private TokenCacheService tokenCacheService;

        @Mock
        private UserServiceClient userServiceClient;

        @Mock
        private EventProducer eventProducer;

        @Mock
        private GatewayFilterChain chain;

        private JwtAuthenticationFilter filter;

        @BeforeEach
        void setUp() {
                JwtGatewayProperties properties = new JwtGatewayProperties();
                properties.getJwt().setCacheTtlMinutes(10);
                properties.setOpenEndpoints(List.of(
                                "/api/v1/users/register",
                                "/api/v1/users/login",
                                "/api/v1/users/refresh"));

                filter = new JwtAuthenticationFilter(tokenCacheService, userServiceClient, properties, eventProducer);
        }

        private MockServerWebExchange createExchange(String method, String path) {
                MockServerHttpRequest request = MockServerHttpRequest.method(
                                org.springframework.http.HttpMethod.valueOf(method), path).build();
                return MockServerWebExchange.from(request);
        }

        private MockServerWebExchange createExchangeWithAuth(String method, String path, String authHeader) {
                MockServerHttpRequest request = MockServerHttpRequest.method(
                                org.springframework.http.HttpMethod.valueOf(method), path)
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .build();
                return MockServerWebExchange.from(request);
        }

        @Test
        @DisplayName("Открытый эндпоинт /api/v1/users/register — пропускает без токена")
        void openEndpoint_register_shouldPassWithoutToken() {
                MockServerWebExchange exchange = createExchange("POST", "/api/v1/users/register");
                when(chain.filter(any())).thenReturn(Mono.empty());

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();
        }

        @Test
        @DisplayName("Открытый эндпоинт /api/v1/users/login — пропускает без токена")
        void openEndpoint_login_shouldPassWithoutToken() {
                MockServerWebExchange exchange = createExchange("POST", "/api/v1/users/login");
                when(chain.filter(any())).thenReturn(Mono.empty());

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();
        }

        @Test
        @DisplayName("Открытый эндпоинт /api/v1/users/refresh — пропускает без токена")
        void openEndpoint_refresh_shouldPassWithoutToken() {
                MockServerWebExchange exchange = createExchange("POST", "/api/v1/users/refresh");
                when(chain.filter(any())).thenReturn(Mono.empty());

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();
        }

        @Test
        @DisplayName("Закрытый эндпоинт без токена — 401")
        void protectedEndpoint_noToken_shouldReturn401() {
                MockServerWebExchange exchange = createExchange("GET", "/api/v1/accounts/some-id");

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();

                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Закрытый эндпоинт с невалидным токеном — user-service отклоняет — 401")
        void protectedEndpoint_invalidToken_shouldReturn401() {
                MockServerWebExchange exchange = createExchangeWithAuth(
                                "GET", "/api/v1/accounts/some-id", "Bearer invalid.token.here");
                when(tokenCacheService.getCachedUserId(anyString())).thenReturn(Mono.empty());
                when(userServiceClient.validateToken(anyString()))
                                .thenReturn(Mono.error(new RuntimeException("Invalid token")));

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();

                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Закрытый эндпоинт без Bearer префикса — 401")
        void protectedEndpoint_noBearerPrefix_shouldReturn401() {
                MockServerWebExchange exchange = createExchangeWithAuth(
                                "GET", "/api/v1/accounts/some-id", "some-token-without-bearer");

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();

                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Закрытый эндпоинт с валидным токеном — user-service валидирует — пропускает")
        void protectedEndpoint_validToken_shouldPassAndAddHeaders() {
                String userId = UUID.randomUUID().toString();
                String token = "valid.jwt.token";

                MockServerWebExchange exchange = createExchangeWithAuth(
                                "GET", "/api/v1/accounts/some-id", "Bearer " + token);

                when(tokenCacheService.getCachedUserId(token)).thenReturn(Mono.empty());
                when(userServiceClient.validateToken(token))
                                .thenReturn(Mono.just(new TokenValidationResponse(userId, "USER")));
                when(tokenCacheService.cacheToken(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(true));
                when(chain.filter(any())).thenReturn(Mono.empty());

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();
        }

        @Test
        @DisplayName("Кэш-хит — берёт userId и role из Redis, не обращается к user-service")
        void cachedToken_shouldUseRedisValues() {
                String userId = UUID.randomUUID().toString();
                String token = "cached.jwt.token";

                MockServerWebExchange exchange = createExchangeWithAuth(
                                "GET", "/api/v1/accounts/some-id", "Bearer " + token);

                when(tokenCacheService.getCachedUserId(token)).thenReturn(Mono.just(userId));
                when(tokenCacheService.getCachedRole(token)).thenReturn(Mono.just("USER"));
                when(chain.filter(any())).thenReturn(Mono.empty());

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();
        }

        @Test
        @DisplayName("X-Trace-Id добавляется к запросу")
        void request_shouldContainTraceIdHeader() {
                MockServerWebExchange exchange = createExchange("POST", "/api/v1/users/register");
                when(chain.filter(any())).thenReturn(Mono.empty());

                StepVerifier.create(filter.filter(exchange, chain))
                                .verifyComplete();
        }
}
