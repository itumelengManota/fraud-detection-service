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
}