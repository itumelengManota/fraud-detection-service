package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RiskScore(@Min(0) @Max(100) int value) {

    public RiskLevel toRiskLevel() {
        if (value <= 40) {
            return RiskLevel.LOW;
        } else if (value <= 70) {
            return RiskLevel.MEDIUM;
        } else if (value <= 90) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.CRITICAL;
        }
    }

    public boolean hasLowRisk() {
        return toRiskLevel() == RiskLevel.LOW;
    }

    public boolean hasHighRisk() {
        return toRiskLevel() == RiskLevel.HIGH || toRiskLevel() == RiskLevel.CRITICAL;
    }

    public boolean hasCriticalRisk() {
        return toRiskLevel() == RiskLevel.CRITICAL;
    }
}
