package com.twenty9ine.frauddetection.application.service;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.port.in.AssessTransactionRiskUseCase;
import com.twenty9ine.frauddetection.application.port.in.ProcessTransactionUseCase;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.command.ProcessTransactionCommand;
import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessTransactionApplicationService implements ProcessTransactionUseCase {

    private final AssessTransactionRiskUseCase assessTransactionRisk;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public void process(ProcessTransactionCommand command) {
        log.debug("Processing transaction: {}", command.transactionId());

        Transaction transaction = toDomain(command);
        transactionRepository.save(transaction);
        assessTransactionRisk.assess(toCommand(transaction));

        log.debug("Successfully processed transaction: {}", command.transactionId());
    }

    private static Transaction toDomain(ProcessTransactionCommand command) {
        return Transaction.builder()
                .id(TransactionId.of(command.transactionId()))
                .location(toDomain(command.location())) // Map location
                .merchant(buildMerchant(command))
                .deviceId(command.deviceId())
                .timestamp(command.transactionTimestamp())
                .accountId(command.accountId())
                .amount(Money.of(command.amount(), command.currency()))
                .type(TransactionType.fromString(command.type()))  // Convert here
                .channel(Channel.fromString(command.channel()))    // Convert here
                .build();
    }

    private static Merchant buildMerchant(ProcessTransactionCommand command) {
        return new Merchant(MerchantId.of(command.merchantId()), command.merchantName(), MerchantCategory.fromString(command.merchantCategory()));
    }

    private static Location toDomain(LocationDto locationDto) {
        if (locationDto == null) return null;

        return new Location(
                locationDto.latitude(),
                locationDto.longitude(),
                locationDto.country(),
                locationDto.city(),
                locationDto.timestamp()
        );
    }

    private AssessTransactionRiskCommand toCommand(Transaction transaction) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transaction.id().toUUID())
                .accountId(transaction.accountId())
                .amount(transaction.amount().value())
                .currency(transaction.amount().currency().getCurrencyCode())
                .type(transaction.type().name())
                .channel(transaction.channel().name())
                .merchantId(transaction.merchant() != null ? transaction.merchant().id().merchantId() : null)
                .merchantName(transaction.merchant() != null ? transaction.merchant().name() : null)
                .merchantCategory(transaction.merchant() != null ? transaction.merchant().category().name() : null)
                .location(mapLocationDto(transaction.location())) // Add this
                .deviceId(transaction.deviceId())
                .transactionTimestamp(transaction.timestamp())
                .build();
    }

    private LocationDto mapLocationDto(Location location) {
        if (location == null) return null;

        return new LocationDto(
                location.latitude(),
                location.longitude(),
                location.country(),
                location.city(),
                location.timestamp()
        );
    }
}