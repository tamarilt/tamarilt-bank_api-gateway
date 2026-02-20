package tamarilt.apigateway.service;

import reactor.core.publisher.Mono;
import tamarilt.apigateway.dto.TokenValidationResponse;

public interface UserServiceClient {

    /**
     * Валидирует токен через user-service
     *
     * @param token JWT access token
     * @return Mono с userId и role при успешной валидации
     */
    Mono<TokenValidationResponse> validateToken(String token);
}
