package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.EventPublishingException;
import com.twenty9ine.frauddetection.application.port.out.EventPublisherPort;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Component
@Slf4j
public class EventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DomainEventToAvroMapper avroMapper;

    @Override
    public void publish(DomainEvent<TransactionId> event) {
        try {
            String topic = determineTopicForEvent(event);

            CompletableFuture<SendResult<String, Object>> future = publishEvent(event, topic);
            future.whenComplete((result, throwable) -> complete(event, result, throwable, topic));
        } catch (Exception e) {
            log.error("Error publishing event: {}", event.getEventType(), e);
            throw new EventPublishingException("Failed to publish event", e);
        }
    }

    @Override
    public void publishAll(List<DomainEvent<TransactionId>> events) {
        events.forEach(this::publish);
    }

    private static void complete(DomainEvent<TransactionId> event, SendResult<String, Object> result, Throwable throwable, String topic) {
        if (throwable != null) {
            log.error("Failed to publish event: {} to topic: {}", event.getEventType(), topic, throwable);
            throw new EventPublishingException("Failed to publish event: " + event.getEventType(), throwable);
        } else {
            RecordMetadata recordMetadata = result.getRecordMetadata();
            log.debug("Published event: {} to topic: {} partition: {} offset: {}", event.getEventType(), topic,
                    recordMetadata.partition(), recordMetadata.offset());
        }
    }

    private CompletableFuture<SendResult<String, Object>> publishEvent(DomainEvent<TransactionId> event, String topic) {
        return kafkaTemplate.send(topic, determinePartitionKey(event), avroMapper.toAvro(event));
    }

    private String determineTopicForEvent(DomainEvent<?> event) {
        return switch (event) {
            case RiskAssessmentCompleted _ -> "fraud-detection.risk-assessments";
            case HighRiskDetected _ -> "fraud-detection.high-risk-alerts";
            default -> "fraud-detection.domain-events";
        };
    }

    private String determinePartitionKey(DomainEvent<?> event) {
        return switch (event) {
            case RiskAssessmentCompleted e -> e.id().toString();
            case HighRiskDetected e -> e.id().toString();
            default -> event.getEventId().toString();
        };
    }
}