package com.twenty9ine.frauddetection.domain.valueobject.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidCountryValidator implements ConstraintValidator<ValidCountry, String> {
    private static final Set<String> ISO_COUNTRIES = Arrays.stream(Locale.getISOCountries())
            .collect(Collectors.toSet());

    @Override
    public boolean isValid(String country, ConstraintValidatorContext context) {
        if (country == null) {
            return true; // Use @NotNull separately if country is required
        }
        return ISO_COUNTRIES.contains(country.toUpperCase());
    }
}