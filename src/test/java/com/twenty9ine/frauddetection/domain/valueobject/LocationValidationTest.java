package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LocationValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validLocationProvider")
    @DisplayName("Valid location configurations should have no violations")
    void testValidLocations(String description, Double latitude, Double longitude, String country, String city) {
        Location location = new Location(latitude, longitude, country, city);

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertTrue(violations.isEmpty(), "Expected no violations for: " + description);
    }

    static Stream<Arguments> validLocationProvider() {
        return Stream.of(
                Arguments.of("Valid location with all fields", 40.7128, -74.0060, "US", "New York"),
                Arguments.of("Valid location with different country code", 51.5074, -0.1278, "GB", "London"),
                Arguments.of("Valid location with lowercase country code", 40.7128, -74.0060, "us", "New York"),
                Arguments.of("Valid location with null country", 40.7128, -74.0060, null, "New York"),
                Arguments.of("Valid location with null city", 40.7128, -74.0060, "US", null)
        );
    }

    @ParameterizedTest(name = "Null {0} should violate NotNull constraint")
    @MethodSource("nullFieldProvider")
    @DisplayName("Null required fields should have violations")
    void testNullFieldsViolations(String fieldName, Double latitude, Double longitude, String country, String city, int expectedViolations) {
        Location location = new Location(latitude, longitude, country, city);

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertFalse(violations.isEmpty());
        assertEquals(expectedViolations, violations.size());
        if (expectedViolations == 1) {
            assertEquals(fieldName, violations.iterator().next().getPropertyPath().toString());
        }
    }

    static Stream<Arguments> nullFieldProvider() {
        return Stream.of(
                Arguments.of("latitude", null, -74.0060, "US", "New York", 1),
                Arguments.of("longitude", 40.7128, null, "US", "New York", 1)
        );
    }


    @Test
    @DisplayName("Invalid country code should violate ValidCountry constraint")
    void testInvalidCountryCode_ViolatesValidCountry() {
        Location location = new Location(40.7128, -74.0060, "INVALID", "New York");

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<Location> violation = violations.iterator().next();
        assertEquals("country", violation.getPropertyPath().toString());
        assertEquals("Invalid country code", violation.getMessage());
    }

    @Test
    @DisplayName("Multiple constraint violations should be reported")
    void testMultipleViolations() {
        Location location = new Location(null, null, "INVALID", "New York");

        Set<ConstraintViolation<Location>> violations = validator.validate(location);

        assertEquals(3, violations.size());
    }
}