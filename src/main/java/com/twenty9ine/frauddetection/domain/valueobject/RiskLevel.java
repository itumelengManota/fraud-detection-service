package com.twenty9ine.frauddetection.domain.valueobject;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromString(String riskLevelString) {
        for (RiskLevel riskLevel : RiskLevel.values()) {
            if (riskLevel.name().equalsIgnoreCase(riskLevelString)) {
                return riskLevel;
            }
        }
        throw new IllegalArgumentException("Unknown risk level: " + riskLevelString);
    }
}
