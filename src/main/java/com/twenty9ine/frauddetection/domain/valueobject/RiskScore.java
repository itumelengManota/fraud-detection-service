package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RiskScore(@Min(0) @Max(100) int value) {
    public static RiskScore of(int value) {
        return new RiskScore(value);
    }
}
