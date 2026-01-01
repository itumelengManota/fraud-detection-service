package com.twenty9ine.frauddetection.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LocationDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldPassValidationWithValidData() {
        LocationDto dto = new LocationDto(40.7128, -74.0060, "US", "New York");
        Set<ConstraintViolation<LocationDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailWhenLatitudeIsNull() {
        LocationDto dto = new LocationDto(null, -74.0060, "US", "New York");
        Set<ConstraintViolation<LocationDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertEquals("must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void shouldFailWhenLongitudeIsNull() {
        LocationDto dto = new LocationDto(40.7128, null, "US", "New York");
        Set<ConstraintViolation<LocationDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertEquals("must not be null", violations.iterator().next().getMessage());
    }

    @Test
    void shouldFailWhenCountryIsNull() {
        LocationDto dto = new LocationDto(40.7128, -74.0060, null, "New York");
        Set<ConstraintViolation<LocationDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("must not be null")));
    }

    @Test
    void shouldFailWhenCountryIsInvalid() {
        LocationDto dto = new LocationDto(40.7128, -74.0060, "INVALID", "New York");
        Set<ConstraintViolation<LocationDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("provideCityBlankCases")
    void shouldFailWhenCityIsBlank(String city) {
        LocationDto dto = new LocationDto(40.7128, -74.0060, "US", city);
        Set<ConstraintViolation<LocationDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertEquals("must not be blank", violations.iterator().next().getMessage());
    }

    private static Stream<Arguments> provideCityBlankCases() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of((String) null),
                Arguments.of("   ")
        );
    }
}