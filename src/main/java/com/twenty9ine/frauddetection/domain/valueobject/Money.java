package com.twenty9ine.frauddetection.domain.valueobject;

import java.math.BigDecimal;

public record Money(
    BigDecimal amount,
    String currency    //TODO: Consider using ISO 4217 currency codes
) {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must be provided");
        }
    }
}
