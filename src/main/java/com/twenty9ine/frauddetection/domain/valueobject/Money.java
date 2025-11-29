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
    @Override
    public String toString() {
        return String.format("%s %s", value, currency.getCurrencyCode());
    }
}