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

    @Test
    void testIsHighRisk_LowRisk_ReturnsFalse() {
        RiskScore riskScore = new RiskScore(30);

        boolean isHighRisk = riskScore.isHighRisk();

        assertFalse(isHighRisk);
    }

    @Test
    void testIsHighRisk_MediumRisk_ReturnsFalse() {
        RiskScore riskScore = new RiskScore(60);

        boolean isHighRisk = riskScore.isHighRisk();

        assertFalse(isHighRisk);
    }

    @Test
    void testIsHighRisk_HighRisk_ReturnsTrue() {
        RiskScore riskScore = new RiskScore(80);

        boolean isHighRisk = riskScore.isHighRisk();

        assertTrue(isHighRisk);
    }

    @Test
    void testIsHighRisk_CriticalRisk_ReturnsTrue() {
        RiskScore riskScore = new RiskScore(95);

        boolean isHighRisk = riskScore.isHighRisk();

        assertTrue(isHighRisk);
    }

    @Test
    void testIsHighRisk_BoundaryValue71_ReturnsTrue() {
        RiskScore riskScore = new RiskScore(71);

        boolean isHighRisk = riskScore.isHighRisk();

        assertTrue(isHighRisk);
    }

    @Test
    void testIsHighRisk_BoundaryValue70_ReturnsFalse() {
        RiskScore riskScore = new RiskScore(70);

        boolean isHighRisk = riskScore.isHighRisk();

        assertFalse(isHighRisk);
    }
}