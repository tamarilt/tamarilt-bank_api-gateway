package tamarilt.apigateway.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tamarilt.apigateway.config.JwtGatewayProperties;
import tamarilt.apigateway.dto.TokenValidationResponse;
import tamarilt.apigateway.service.UserServiceClient;

@Service
@RequiredArgsConstructor
public class UserServiceClientImpl implements UserServiceClient {

    private final WebClient.Builder webClientBuilder;
    private final JwtGatewayProperties gatewayProperties;

    @Override
    public Mono<TokenValidationResponse> validateToken(String token) {
        return webClientBuilder.build()
                .post()
                .uri(gatewayProperties.getUserServiceUrl() + "/api/v1/users/validate-token")
                .bodyValue(token)
                .retrieve()
                .bodyToMono(TokenValidationResponse.class);
    }
}
