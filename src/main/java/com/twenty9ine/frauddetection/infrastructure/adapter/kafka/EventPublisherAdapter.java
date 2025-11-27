package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.EventPublishingException;
import com.twenty9ine.frauddetection.domain.port.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class EventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisherAdapter(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String topic = determineTopicForEvent(event);
            byte[] payload = serializeEvent(event);

            kafkaTemplate.send(topic, event.getEventId().toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event: {}", event.getEventType(), ex);
                    } else {
                        log.debug("Published event: {} to topic: {}",
                                event.getEventType(), topic);
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing event", e);
            throw new EventPublishingException("Failed to publish event", e);
        }
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }

    private String determineTopicForEvent(DomainEvent event) {
        return switch(event) {
            case RiskAssessmentCompleted e -> "fraud-detection.risk-assessments";
            case HighRiskDetected e -> "fraud-detection.high-risk-alerts";
            default -> "fraud-detection.domain-events";
        };
    }

    private byte[] serializeEvent(DomainEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(event);
    }
}
