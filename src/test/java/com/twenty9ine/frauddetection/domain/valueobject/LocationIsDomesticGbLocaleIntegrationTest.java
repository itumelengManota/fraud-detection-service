package com.twenty9ine.frauddetection.domain.valueobject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
class LocationIsDomesticGbLocaleIntegrationTest {

    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.UK);
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void shouldReturnTrueForGbLocation_WithGbLocale() {
        // Given
        Locale currentLocale = Locale.getDefault();
        assertThat(currentLocale.getCountry()).isEqualTo("GB");

        Location gbLocation = new Location(51.5074, -0.1278, "GB", "London", Instant.now());

        // When
        boolean isDomestic = gbLocation.isDomestic();

        // Then
        assertThat(isDomestic).isTrue();
    }

    @Test
    void shouldReturnFalseForNonGbLocation_WithGbLocale() {
        // Given
        Location foreignLocation = new Location(48.8566, 2.3522, "FR", "Paris", Instant.now());

        // When
        boolean isDomestic = foreignLocation.isDomestic();

        // Then
        assertThat(isDomestic).isFalse();
    }
}