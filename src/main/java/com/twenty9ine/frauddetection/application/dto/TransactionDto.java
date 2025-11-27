package com.twenty9ine.frauddetection.application.dto;

import com.twenty9ine.frauddetection.domain.model.*;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record TransactionDto(
    @NotNull UUID transactionId,
    @NotNull String accountId,
    @NotNull BigDecimal amount,
    @NotNull String currency,
    @NotNull TransactionType type,
    @NotNull Channel channel,
    String merchantId,
    String merchantName,
    String merchantCategory,
    LocationDto location,
    String deviceId,
    @NotNull Instant timestamp
) {
    public Transaction toDomain() {
        return Transaction.builder()
            .id(transactionId)
            .accountId(accountId)
            .amount(new Money(amount, currency))
            .type(type)
            .channel(channel)
            .merchantId(merchantId)
            .merchantName(merchantName)
            .merchantCategory(merchantCategory)
            .location(location != null ? location.toDomain() : null)
            .deviceId(deviceId)
            .timestamp(timestamp)
            .build();
    }
}
