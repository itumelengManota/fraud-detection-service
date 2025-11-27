package com.twenty9ine.frauddetection.domain.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RiskScore(
    @Min(0) @Max(100) int value
) {
//    public RiskScore {
//        if (value < 0 || value > 100) {
//            throw new IllegalArgumentException("Risk score must be between 0 and 100");
//        }
//    }

//    Implementation without preview feature primitive pattern matching for switch
//    public RiskLevel toRiskLevel() {
//        if (value <= 40) {
//            return RiskLevel.LOW;
//        } else if (value <= 70) {
//            return RiskLevel.MEDIUM;
//        } else if (value <= 90) {
//            return RiskLevel.HIGH;
//        } else {
//            return RiskLevel.CRITICAL;
//        }
//    }


    public RiskLevel toRiskLevel() {
        return switch(value) {
            case int v when v <= 40 -> RiskLevel.LOW;
            case int v when v <= 70 -> RiskLevel.MEDIUM;
            case int v when v <= 90 -> RiskLevel.HIGH;
            default -> RiskLevel.CRITICAL;
        };
    }

    public boolean isHighRisk() {
        return toRiskLevel() == RiskLevel.HIGH ||
               toRiskLevel() == RiskLevel.CRITICAL;
    }
}
