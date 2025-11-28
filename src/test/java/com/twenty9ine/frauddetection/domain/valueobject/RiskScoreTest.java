package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RiskScoreTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        } catch (Exception e) {
            // If validation fails during test compilation, log it
            throw new RuntimeException("Failed to initialize validator", e);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 50, 100})
    void testRiskScore_ValidValues_NoViolations(int value) {
        RiskScore riskScore = new RiskScore(value);

        Set<ConstraintViolation<RiskScore>> violations = validator.validate(riskScore);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testRiskScore_BelowMinimum_HasViolation() {
        RiskScore riskScore = new RiskScore(-1);

        Set<ConstraintViolation<RiskScore>> violations = validator.validate(riskScore);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
    }

    @Test
    void testRiskScore_AboveMaximum_HasViolation() {
        RiskScore riskScore = new RiskScore(101);

        Set<ConstraintViolation<RiskScore>> violations = validator.validate(riskScore);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
    }

    @Test
    void testToRiskLevel_Value0_ReturnsLow() {
        RiskScore riskScore = new RiskScore(0);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.LOW, riskLevel);
    }

    @Test
    void testToRiskLevel_Value40_ReturnsLow() {
        RiskScore riskScore = new RiskScore(40);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.LOW, riskLevel);
    }

    @Test
    void testToRiskLevel_Value41_ReturnsMedium() {
        RiskScore riskScore = new RiskScore(41);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.MEDIUM, riskLevel);
    }

    @Test
    void testToRiskLevel_Value70_ReturnsMedium() {
        RiskScore riskScore = new RiskScore(70);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.MEDIUM, riskLevel);
    }

    @Test
    void testToRiskLevel_Value71_ReturnsHigh() {
        RiskScore riskScore = new RiskScore(71);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.HIGH, riskLevel);
    }

    @Test
    void testToRiskLevel_Value90_ReturnsHigh() {
        RiskScore riskScore = new RiskScore(90);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.HIGH, riskLevel);
    }

    @Test
    void testToRiskLevel_Value91_ReturnsCritical() {
        RiskScore riskScore = new RiskScore(91);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.CRITICAL, riskLevel);
    }

    @Test
    void testToRiskLevel_Value100_ReturnsCritical() {
        RiskScore riskScore = new RiskScore(100);

        RiskLevel riskLevel = riskScore.toRiskLevel();

        assertEquals(RiskLevel.CRITICAL, riskLevel);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 70})
    void testHasHighRisk_LowOrMediumRisk_ReturnsFalse(int value) {
        RiskScore riskScore = new RiskScore(value);

        boolean isHighRisk = riskScore.hasHighRisk();

        assertFalse(isHighRisk);
    }

    @ParameterizedTest
    @ValueSource(ints = {71, 80, 95})
    void testHasHighRisk_HighOrCriticalRisk_ReturnsTrue(int value) {
        RiskScore riskScore = new RiskScore(value);

        boolean isHighRisk = riskScore.hasHighRisk();

        assertTrue(isHighRisk);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 20, 40})
    void testHasLowRisk_LowRiskValues_ReturnsTrue(int value) {
        RiskScore riskScore = new RiskScore(value);

        boolean isLowRisk = riskScore.hasLowRisk();

        assertTrue(isLowRisk);
    }

    @ParameterizedTest
    @ValueSource(ints = {41, 60, 80, 95})
    void testHasLowRisk_MediumHighOrCriticalRisk_ReturnsFalse(int value) {
        RiskScore riskScore = new RiskScore(value);

        boolean isLowRisk = riskScore.hasLowRisk();

        assertFalse(isLowRisk);
    }

    @ParameterizedTest
    @ValueSource(ints = {91, 95, 100})
    void testHasCriticalRisk_CriticalRiskValues_ReturnsTrue(int value) {
        RiskScore riskScore = new RiskScore(value);

        boolean isCriticalRisk = riskScore.hasCriticalRisk();

        assertTrue(isCriticalRisk);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 40, 60, 80, 90})
    void testHasCriticalRisk_NonCriticalRisk_ReturnsFalse(int value) {
        RiskScore riskScore = new RiskScore(value);

        boolean isCriticalRisk = riskScore.hasCriticalRisk();

        assertFalse(isCriticalRisk);
    }
}