package com.twenty9ine.frauddetection.domain.valueobject;

/**
 * Represents the severity/ruleViolationSeverity of an individual rule violation.
 * Multiple rule impacts are aggregated to contribute to the overall risk score.
 * <p>
 * Values: LOW (10), MEDIUM (25), HIGH (40), CRITICAL (60)
 * Purpose: Used by Drools rules to assign point values when rules are triggered
 * Context: Tactical, rule-level scoring - represents the ruleViolationSeverity of individual rule violations
 * Used in:
 *  RuleResult value object (rule engine output)
 *  Drools rules (LARGE_AMOUNT, VELOCITY_5MIN, IMPOSSIBLE_TRAVEL, etc.)
 *  Aggregated to calculate total rule score
 */
public enum RuleViolationSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
