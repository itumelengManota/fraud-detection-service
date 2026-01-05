package com.twenty9ine.frauddetection.domain.valueobject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
class LocationIsDomesticIntegrationTest {

    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale.Builder()
                .setLanguage("af")
                .setRegion("ZA")
                .build());
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void shouldReturnTrueForDomesticLocation_WithSouthAfricanLocale() {
        // Given
        Locale currentLocale = Locale.getDefault();
        assertThat(currentLocale.getCountry()).isEqualTo("ZA");

        Location domesticLocation = new Location(-26.2041, 28.0473, "ZA", "Johannesburg");

        // When
        boolean isDomestic = domesticLocation.isDomestic();

        // Then
        assertThat(isDomestic).isTrue();
    }

    @Test
    void shouldReturnFalseForForeignLocation_WithSouthAfricanLocale() {
        // Given
        Locale currentLocale = Locale.getDefault();
        assertThat(currentLocale.getCountry()).isEqualTo("ZA");

        Location foreignLocation = new Location(51.5074, -0.1278, "UK", "London");

        // When
        boolean isDomestic = foreignLocation.isDomestic();

        // Then
        assertThat(isDomestic).isFalse();
    }
}