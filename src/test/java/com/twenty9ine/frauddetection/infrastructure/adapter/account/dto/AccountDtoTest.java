package com.twenty9ine.frauddetection.infrastructure.adapter.account.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccountDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldPassValidationWithValidAccountDto() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, "ZA", "Johannesburg");
        AccountDto accountDto = new AccountDto("ACC123", location, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAccountIdIsNull() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, "ZA", "Johannesburg");
        AccountDto accountDto = new AccountDto(null, location, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("accountId")));
    }

    @Test
    void shouldFailValidationWhenAccountIdIsBlank() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, "ZA", "Johannesburg");
        AccountDto accountDto = new AccountDto("", location, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("accountId")));
    }

    @Test
    void shouldFailValidationWhenHomeLocationIsNull() {
        AccountDto accountDto = new AccountDto("ACC123", null, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("homeLocation")));
    }

    @Test
    void shouldFailValidationWhenLocationCountryIsNull() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, null, "Johannesburg");
        AccountDto accountDto = new AccountDto("ACC123", location, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("homeLocation.country")));
    }

    @Test
    void shouldFailValidationWhenLocationCountryIsInvalid() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, "INVALID", "Johannesburg");
        AccountDto accountDto = new AccountDto("ACC123", location, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("homeLocation.country")));
    }

    @Test
    void shouldFailValidationWhenLocationCityIsNull() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, "ZA", null);
        AccountDto accountDto = new AccountDto("ACC123", location, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("homeLocation.city")));
    }

    @Test
    void shouldFailValidationWhenLocationCityIsBlank() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, "ZA", "");
        AccountDto accountDto = new AccountDto("ACC123", location, Instant.now());

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("homeLocation.city")));
    }

    @Test
    void shouldPassValidationWhenCreatedAtIsNull() {
        LocationDto location = new LocationDto(-26.2041, 28.0473, "ZA", "Johannesburg");
        AccountDto accountDto = new AccountDto("ACC123", location, null);

        Set<ConstraintViolation<AccountDto>> violations = validator.validate(accountDto);

        assertTrue(violations.isEmpty());
    }
}