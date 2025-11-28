package com.twenty9ine.frauddetection.domain.valueobject;

import java.util.UUID;

public record TransactionId(UUID transactionId) {

    public UUID toUUID() {
        return transactionId;
    }

    public static TransactionId generate() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(String brainstormId) {
        return new TransactionId(UUID.fromString(brainstormId));
    }

    public static TransactionId of(UUID brainstormId) {
        return new TransactionId(brainstormId);
    }
}
