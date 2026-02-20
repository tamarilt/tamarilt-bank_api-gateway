package tamarilt.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class JwtGatewayProperties {

    private JwtProperties jwt = new JwtProperties();
    private List<String> openEndpoints = new ArrayList<>();
    private String userServiceUrl = "http://localhost:8081";

    @Getter
    @Setter
    public static class JwtProperties {
        private int cacheTtlMinutes = 10;
    }
}
