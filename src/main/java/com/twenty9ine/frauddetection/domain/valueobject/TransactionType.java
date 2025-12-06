package com.twenty9ine.frauddetection.domain.valueobject;

public enum TransactionType {
    PURCHASE,
    ATM_WITHDRAWAL,
    TRANSFER,
    PAYMENT,
    REFUND;

    public static TransactionType fromString(String type) {
        for (TransactionType transactionType : TransactionType.values()) {
            if (transactionType.name().equalsIgnoreCase(normalise(type)))
                return transactionType;
        }

        throw new IllegalArgumentException("Unknown type: " + type);
    }


    private static String normalise(String type) {
        return type.replace(" ", "_");
    }
}