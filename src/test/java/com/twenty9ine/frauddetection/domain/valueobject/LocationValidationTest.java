package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LocationValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidLocation_NoViolations() {
        Location location = new Location(40.7128, -74.0060, "US", "New York", Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testNullLatitude_ViolatesNotNull() {
        Location location = new Location(null, -74.0060, "US", "New York", Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertEquals("latitude", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testNullLongitude_ViolatesNotNull() {
        Location location = new Location(40.7128, null, "US", "New York", Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertEquals("longitude", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testNullTimestamp_ViolatesNotNull() {
        Location location = new Location(40.7128, -74.0060, "US", "New York", null);

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertEquals("timestamp", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testNullCountry_NoViolation() {
        Location location = new Location(40.7128, -74.0060, null, "New York", Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidCountryCode_NoViolation() {
        Location location = new Location(40.7128, -74.0060, "GB", "London", Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidCountryCode_ViolatesValidCountry() {
        Location location = new Location(40.7128, -74.0060, "INVALID", "New York", Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<Location> violation = violations.iterator().next();
        assertEquals("country", violation.getPropertyPath().toString());
        assertEquals("Invalid country code", violation.getMessage());
    }

    @Test
    void testCountryCodeCaseInsensitive_NoViolation() {
        Location location = new Location(40.7128, -74.0060, "us", "New York", Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testMultipleViolations() {
        Location location = new Location(null, null, "INVALID", "New York", null);

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertEquals(4, violations.size());
    }

    @Test
    void testNullCity_NoViolation() {
        Location location = new Location(40.7128, -74.0060, "US", null, Instant.now());

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertTrue(violations.isEmpty());
    }
}