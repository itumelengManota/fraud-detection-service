package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.application.service.FraudDetectionApplicationService;
import com.twenty9ine.frauddetection.application.port.VelocityServicePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionEventConsumer {

    private final FraudDetectionApplicationService fraudDetectionService;
    private final VelocityServicePort velocityService;
    private final TransactionEventMapper mapper;
    private final MeterRegistry meterRegistry;

    public TransactionEventConsumer(FraudDetectionApplicationService fraudDetectionService, VelocityServicePort velocityService,
                                    TransactionEventMapper mapper, MeterRegistry meterRegistry) {
        this.fraudDetectionService = fraudDetectionService;
        this.velocityService = velocityService;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "${kafka.topics.transactions}", groupId = "fraud-detection-service", concurrency = "10")
    public void consume(@Payload byte[] payload, @Header(KafkaHeaders.RECEIVED_KEY) String key, Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            var transaction = mapper.toDomain(payload);
            log.debug("Received transaction event: {}", transaction.id());

            velocityService.incrementCounters(transaction);

            fraudDetectionService.assessRisk(transaction);
            acknowledgment.acknowledge();

            meterRegistry.counter("fraud.events.processed", "status", "success").increment();
        } catch (Exception e) {
            log.error("Failed to process transaction event", e);
            meterRegistry.counter("fraud.events.processed", "status", "error").increment();

            throw e;
        } finally {
            sample.stop(meterRegistry.timer("fraud.event.processing"));
        }
    }
}
