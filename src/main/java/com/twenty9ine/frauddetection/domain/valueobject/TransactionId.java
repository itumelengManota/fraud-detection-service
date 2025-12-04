package com.twenty9ine.frauddetection.domain.valueobject;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

public record TransactionId(UUID transactionId) {

    public UUID toUUID() {
        return transactionId;
    }

    public static TransactionId generate() {
        return new TransactionId(Generators.timeBasedEpochGenerator().generate());
    }

    public static TransactionId of(String transactionId) {
        return new TransactionId(UUID.fromString(transactionId));
    }

    public static TransactionId of(UUID transactionId) {
        return new TransactionId(transactionId);
    }

    @Override
    public String toString() {
        return transactionId.toString();
    }
}
