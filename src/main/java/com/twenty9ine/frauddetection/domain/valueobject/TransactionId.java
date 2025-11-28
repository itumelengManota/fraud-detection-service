package com.twenty9ine.frauddetection.domain.valueobject;

import java.util.UUID;

public record TransactionId(UUID transactionId) {

    public UUID toUUID() {
        return transactionId;
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(String transactionId) {
        return new TransactionId(UUID.fromString(transactionId));
    }

    public static TransactionId of(UUID transactionId) {
        return new TransactionId(transactionId);
    }
}
