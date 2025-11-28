package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RiskScore(
    @Min(0) @Max(100) int value
) {

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

//  preview feature primitive pattern matching for switch implementation
//    public RiskLevel toRiskLevel() {
//        return switch(value) {
//            case int v when v <= 40 -> RiskLevel.LOW;
//            case int v when v <= 70 -> RiskLevel.MEDIUM;
//            case int v when v <= 90 -> RiskLevel.HIGH;
//            default -> RiskLevel.CRITICAL;
//        };
//    }

    public boolean isHighRisk() {
        return toRiskLevel() == RiskLevel.HIGH ||
               toRiskLevel() == RiskLevel.CRITICAL;
    }
}
