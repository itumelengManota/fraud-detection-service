package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.application.port.in.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.AssessTransactionRiskUseCase;
import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka adapter for consuming transaction events.
 * <p>
 * Subscribes to transaction topic and triggers fraud detection use case
 * for each incoming transaction. Follows Hexagonal Architecture by
 * depending on input port interface.
 *
 * @author Fraud Detection Team
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class TransactionEventConsumer {

    private final AssessTransactionRiskUseCase assessTransactionRiskUseCase;
    private final VelocityServicePort velocityService;
    private final TransactionEventMapper mapper;
    private final MeterRegistry meterRegistry;
    private final TransactionRepository transactionRepository;

    @KafkaListener(
            topics = "${kafka.topics.transactions}",
            groupId = "fraud-detection-service",
            concurrency = "10"
    )
    public void consume(
            @Payload byte[] payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Transaction transaction = mapper.toDomain(payload);
            log.debug("Received transaction event: {}", transaction.id());

            // Update velocity counters
            velocityService.incrementCounters(transaction);

            // Persist transaction for geographic validation
            transactionRepository.save(transaction);

            // Convert to command and trigger use case
            AssessTransactionRiskCommand command = buildAssessTransactionRiskCommand(transaction);

            assessTransactionRiskUseCase.assess(command);

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

    private static AssessTransactionRiskCommand buildAssessTransactionRiskCommand(Transaction transaction) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transaction.id().toUUID())
                .accountId(transaction.accountId())
                .amount(transaction.amount().value())
                .currency(transaction.amount().currency().getCurrencyCode())
                .type(transaction.type())
                .channel(transaction.channel())
                .merchantId(transaction.merchant().id().merchantId())
                .merchantName(transaction.merchant().name())
                .merchantCategory(transaction.merchant().category())
                .deviceId(transaction.deviceId())
                .transactionTimestamp(transaction.timestamp())
                .build();
    }
}