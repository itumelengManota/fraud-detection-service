package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.EventPublishingException;
import com.twenty9ine.frauddetection.application.port.out.EventPublisherPort;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class EventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DomainEventToAvroMapper avroMapper;
    private final String riskAssessmentsTopic;
    private final String highRiskAlertsTopic;
    private final String domainEventsTopic;

    public EventPublisherAdapter(
            KafkaTemplate<String, Object> kafkaTemplate,
            DomainEventToAvroMapper avroMapper,
            @Value("${kafka.topics.risk-assessments:fraud-detection.risk-assessments}") String riskAssessmentsTopic,
            @Value("${kafka.topics.high-risk-alerts:fraud-detection.high-risk-alerts}") String highRiskAlertsTopic,
            @Value("${kafka.topics.domain-events:fraud-detection.domain-events}") String domainEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.avroMapper = avroMapper;
        this.riskAssessmentsTopic = riskAssessmentsTopic;
        this.highRiskAlertsTopic = highRiskAlertsTopic;
        this.domainEventsTopic = domainEventsTopic;
    }

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
            case RiskAssessmentCompleted _ -> riskAssessmentsTopic;
            case HighRiskDetected _ -> highRiskAlertsTopic;
            default -> domainEventsTopic;
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