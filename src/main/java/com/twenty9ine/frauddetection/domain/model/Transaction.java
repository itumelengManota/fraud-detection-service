package com.twenty9ine.frauddetection.domain.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record Transaction(UUID id, String accountId, Money amount, TransactionType type, Channel channel,
                          String merchantId, String merchantName, String merchantCategory, Location location,
                          String deviceId, Instant timestamp) {
}
