package com.twenty9ine.frauddetection.application.port.in.command;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record ProcessTransactionCommand(
    UUID transactionId,
    String accountId,
    BigDecimal amount,
    String currency,
    String type,
    String channel,
    String merchantId,
    String merchantName,
    String merchantCategory,
    LocationDto location,
    String deviceId,
    Instant transactionTimestamp
) { }
