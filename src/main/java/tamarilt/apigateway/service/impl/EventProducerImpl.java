package tamarilt.apigateway.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tamarilt.apigateway.dto.ServiceEvent;
import tamarilt.apigateway.service.EventProducer;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventProducerImpl implements EventProducer {

    private static final String TOPIC = "service-events";

    private final KafkaTemplate<String, ServiceEvent> kafkaTemplate;

    @Override
    public void sendEvent(ServiceEvent event) {
        kafkaTemplate.send(TOPIC, event.getEventId(), event);
    }

    @Override
    public void sendSuccess(String action, Map<String, Object> details) {
        sendEvent(ServiceEvent.success(action, details));
    }

    @Override
    public void sendError(String action, Map<String, Object> details) {
        sendEvent(ServiceEvent.error(action, details));
    }
}
