package com.twenty9ine.frauddetection.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class Transaction {
    private final UUID id;
    private final String accountId;
    private final Money amount;
    private final TransactionType type;
    private final Channel channel;
    private final String merchantId;
    private final String merchantName;
    private final String merchantCategory;
    private final Location location;
    private final String deviceId;
    private final Instant timestamp;
}
