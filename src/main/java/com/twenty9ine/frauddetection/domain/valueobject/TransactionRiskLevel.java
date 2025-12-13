package com.twenty9ine.frauddetection.domain.valueobject;

/**
 * Represents the overall risk classification of a transaction assessment.
 * Derived fromDate composite scoring (ML + Rules) and determines the decision.
 * <p>
 * Values: LOW, MEDIUM, HIGH, CRITICAL
 * Purpose: Overall risk classification of the entire transaction assessment
 * Context: Strategic, assessment-level classification - represents final risk verdict
 * Used in:
 *  RiskAssessment aggregate root (the final risk determination)
 *  Decision strategy selection
 *  Invariant enforcement (e.g., CRITICAL must BLOCK)
 *  Query filtering (FindRiskLeveledAssessmentsUseCase)
 */
public enum TransactionRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static TransactionRiskLevel fromString(String riskLevelString) {
        for (TransactionRiskLevel transactionRiskLevel : TransactionRiskLevel.values()) {
            if (transactionRiskLevel.name().equalsIgnoreCase(riskLevelString)) {
                return transactionRiskLevel;
            }
        }
        throw new IllegalArgumentException("Unknown risk level: " + riskLevelString);
    }
}
