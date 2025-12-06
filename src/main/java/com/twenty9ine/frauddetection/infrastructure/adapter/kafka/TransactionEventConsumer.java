package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.application.port.in.ProcessTransactionUseCase;
import com.twenty9ine.frauddetection.application.port.in.command.ProcessTransactionCommand;
import com.twenty9ine.frauddetection.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Kafka adapter for consuming transaction events.
 * <p>
 * Subscribes to transaction topic and triggers fraud detection use case
 * for each incoming transaction. Follows Hexagonal Architecture by
 * depending on input port interface.
 *
 * @author Ignatius Itumeleng Manota
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class TransactionEventConsumer {

    private final ProcessTransactionUseCase processTransactionUseCase;
    private final TransactionEventMapper mapper;
    private final SeenMessageCache seenMessageCache;
    private final KafkaTopicProperties kafkaTopicProperties;

    @KafkaListener(topics = "#{kafkaTopicProperties.name}", groupId = "#{kafkaTopicProperties.groupId}",
                   concurrency = "#{kafkaTopicProperties.concurrency}")
    @Transactional
    public void consume(TransactionAvro avroTransaction, Acknowledgment acknowledgment) {

        try {
            ProcessTransactionCommand processTransactionCommand = mapper.toCommand(avroTransaction);
            UUID transactionId = processTransactionCommand.transactionId();

            if (!isProcessed(acknowledgment, transactionId)) {
                processTransaction(processTransactionCommand);
                markProcessed(acknowledgment, transactionId);
            }
        } catch (DeserializationException e) {
            log.error("Failed to deserialize transaction event - skipping poison pill", e);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retry
        } catch (Exception e) {
            log.error("Failed to process transaction event", e);
            throw e;  // Don't acknowledge - let Kafka retry with backoff
        }
    }

    private boolean isProcessed(Acknowledgment acknowledgment, UUID transactionId) {
        if (seenMessageCache.hasProcessed(transactionId)) {
            log.debug("Duplicate transaction detected, skipping: {}", transactionId);
            acknowledgment.acknowledge();
            return true;
        }

        return false;
    }

    private void markProcessed(Acknowledgment acknowledgment, UUID transactionId) {
        seenMessageCache.markProcessed(transactionId);
        acknowledgment.acknowledge();
    }

    private void processTransaction(ProcessTransactionCommand processTransactionCommand) {
        processTransactionUseCase.process(processTransactionCommand);
    }
}