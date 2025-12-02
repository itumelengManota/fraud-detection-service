package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Builder;

import java.time.Instant;

@Builder
public record Transaction(TransactionId id, String accountId, Money amount, TransactionType type, Channel channel,
                          Merchant merchant, Location location,
                          String deviceId, Instant timestamp) {
}
