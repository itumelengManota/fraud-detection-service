package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.EventPublishingException;
import com.twenty9ine.frauddetection.application.port.out.EventPublisherPort;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class EventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DomainEventToAvroMapper avroMapper;

    public EventPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate, DomainEventToAvroMapper avroMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.avroMapper = avroMapper;
    }

    @Override
    public void publish(DomainEvent<TransactionId> event) {
        try {
            String topic = determineTopicForEvent(event);
            Object avroEvent = avroMapper.toAvro(event);
            String key = determinePartitionKey(event);

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, avroEvent);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event: {} to topic: {}", event.getEventType(), topic, ex);
                    throw new EventPublishingException("Failed to publish event: " + event.getEventType(), ex);
                } else {
                    log.debug("Published event: {} to topic: {} partition: {} offset: {}", event.getEventType(), topic,
                            result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                }
            });

            // Block for critical events to ensure delivery
            if (isCriticalEvent(event)) {
                future.get(5, TimeUnit.SECONDS);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            log.error("Thread interrupted while publishing event: {}", event.getEventType(), e);
            throw new EventPublishingException("Event publishing interrupted", e);
        } catch (Exception e) {
            log.error("Error publishing event: {}", event.getEventType(), e);
            throw new EventPublishingException("Failed to publish event", e);
        }
    }

    @Override
    public void publishAll(List<DomainEvent<TransactionId>> events) {
        events.forEach(this::publish);
    }

    private String determineTopicForEvent(DomainEvent<?> event) {
        return switch (event) {
            case RiskAssessmentCompleted _ -> "fraud-detection.risk-assessments";
            case HighRiskDetected _ -> "fraud-detection.high-risk-alerts";
            default -> "fraud-detection.domain-events";
        };
    }

    private boolean isCriticalEvent(DomainEvent<?> event) {
        return event instanceof HighRiskDetected;
    }

    private String determinePartitionKey(DomainEvent<?> event) {
        return switch (event) {
            case RiskAssessmentCompleted e -> e.id().toString();
            case HighRiskDetected e -> e.id().toString();
            default -> event.getEventId().toString();
        };
    }
}