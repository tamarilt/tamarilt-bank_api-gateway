package tamarilt.apigateway.service;

import tamarilt.apigateway.dto.ServiceEvent;

import java.util.Map;

public interface EventProducer {

    void sendEvent(ServiceEvent event);

    void sendSuccess(String action, Map<String, Object> details);

    void sendError(String action, Map<String, Object> details);
}
