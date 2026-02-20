package tamarilt.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceEvent {

    private String eventId;
    private String timestamp;
    private String serviceName;
    private String eventType;
    private String action;
    private Map<String, Object> details;

    public static ServiceEvent create(String action, String eventType, Map<String, Object> details) {
        return ServiceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now().toString())
                .serviceName("api-gateway")
                .eventType(eventType)
                .action(action)
                .details(details)
                .build();
    }

    public static ServiceEvent success(String action, Map<String, Object> details) {
        return create(action, "SUCCESS", details);
    }

    public static ServiceEvent error(String action, Map<String, Object> details) {
        return create(action, "ERROR", details);
    }
}
