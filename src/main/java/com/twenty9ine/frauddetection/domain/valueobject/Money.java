package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(
        @NotNull(message = "Value of Money cannot be null")
        BigDecimal value,
        @NotNull(message = "Currency of Money cannot be null")
        Currency currency
) {
    public static Money of(BigDecimal value, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        return new Money(value, currency);
    }

    @Override
    public String toString() {
        return String.format("%s %s", value, currency.getCurrencyCode());
    }
}