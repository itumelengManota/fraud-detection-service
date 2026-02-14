package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("nullFieldProvider")
        @DisplayName("Should violate NotNull constraint for null fields")
        void testNullFieldsViolateConstraints(String description, BigDecimal value, Currency currency, int expectedViolations, String expectedField, String expectedMessage) {
            Money money = new Money(value, currency);

            Set<ConstraintViolation<Money>> violations = validator.validate(money);

            assertEquals(expectedViolations, violations.size());
            if (expectedViolations == 1) {
                ConstraintViolation<Money> violation = violations.iterator().next();
                assertEquals(expectedMessage, violation.getMessage());
                assertEquals(expectedField, violation.getPropertyPath().toString());
            }
        }

        static Stream<Arguments> nullFieldProvider() {
            Currency currency = Currency.getInstance("ZAR");
            BigDecimal value = new BigDecimal("100.00");

            return Stream.of(
                    Arguments.of("Null value", null, currency, 1, "value", "Value of Money cannot be null"),
                    Arguments.of("Null currency", value, null, 1, "currency", "Currency of Money cannot be null"),
                    Arguments.of("Both null", null, null, 2, null, null)
            );
        }

        @Test
        @DisplayName("Should pass validation with valid fields")
        void testMoney_WithValidFields_PassesValidation() {
            BigDecimal value = new BigDecimal("100.00");
            Currency currency = Currency.getInstance("ZAR");
            Money money = new Money(value, currency);

            Set<ConstraintViolation<Money>> violations = validator.validate(money);

            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Instance Creation Tests")
    class InstanceCreationTests {

        @Test
        @DisplayName("Should create instance with valid value and currency")
        void testMoney_ValidValueAndCurrency_CreatesInstance() {
            BigDecimal value = new BigDecimal("100.00");
            Currency currency = Currency.getInstance("ZAR");

            Money money = new Money(value, currency);

            assertEquals(value, money.value());
            assertEquals(currency, money.currency());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("valueVariationsProvider")
        @DisplayName("Should create instance with different value types")
        void testMoney_WithDifferentValues_CreatesInstance(String description, BigDecimal value, Currency currency, String expectedValueString) {
            Money money = new Money(value, currency);

            assertEquals(value, money.value());
            assertEquals(currency, money.currency());
            if (expectedValueString != null) {
                assertEquals(expectedValueString, money.value().toPlainString());
            }
        }

        static Stream<Arguments> valueVariationsProvider() {
            return Stream.of(
                    Arguments.of("Zero value", BigDecimal.ZERO, Currency.getInstance("EUR"), "0"),
                    Arguments.of("Negative value", new BigDecimal("-50.00"), Currency.getInstance("GBP"), "-50.00"),
                    Arguments.of("Large value", new BigDecimal("999999999.99"), Currency.getInstance("JPY"), "999999999.99"),
                    Arguments.of("Precise decimal value", new BigDecimal("100.123456789"), Currency.getInstance("ZAR"), "100.123456789")
            );
        }

        @Test
        @DisplayName("Should create distinct instances with different currencies")
        void testMoney_WithDifferentCurrencies_CreatesDistinctInstances() {
            BigDecimal value = new BigDecimal("100.00");
            Money zar = new Money(value, Currency.getInstance("ZAR"));
            Money eur = new Money(value, Currency.getInstance("EUR"));

            assertNotEquals(zar, eur);
            assertNotEquals(zar.currency(), eur.currency());
        }

        @Test
        @DisplayName("Should create instances with common currencies")
        void testMoney_WithCommonCurrencies_CreatesInstances() {
            BigDecimal value = new BigDecimal("100.00");

            Money zar = new Money(value, Currency.getInstance("ZAR"));
            Money eur = new Money(value, Currency.getInstance("EUR"));
            Money gbp = new Money(value, Currency.getInstance("GBP"));
            Money jpy = new Money(value, Currency.getInstance("JPY"));
            Money cad = new Money(value, Currency.getInstance("CAD"));

            assertAll(
                () -> assertEquals("ZAR", zar.currency().getCurrencyCode()),
                () -> assertEquals("EUR", eur.currency().getCurrencyCode()),
                () -> assertEquals("GBP", gbp.currency().getCurrencyCode()),
                () -> assertEquals("JPY", jpy.currency().getCurrencyCode()),
                () -> assertEquals("CAD", cad.currency().getCurrencyCode())
            );
        }
    }

    @Nested
    @DisplayName("Equality and HashCode Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal with same value and currency")
        void testMoney_EqualityWithSameValueAndCurrency_ReturnsTrue() {
            BigDecimal value = new BigDecimal("100.00");
            Currency currency = Currency.getInstance("ZAR");
            Money money1 = new Money(value, currency);
            Money money2 = new Money(value, currency);

            assertEquals(money1, money2);
            assertEquals(money1.hashCode(), money2.hashCode());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("inequalityProvider")
        @DisplayName("Should not be equal when fields differ")
        void testMoney_InequalityScenarios_ReturnsFalse(String description, Money money1, Money money2) {
            assertNotEquals(money1, money2);
        }

        static Stream<Arguments> inequalityProvider() {
            return Stream.of(
                    Arguments.of(
                            "Different values",
                            new Money(new BigDecimal("100.00"), Currency.getInstance("ZAR")),
                            new Money(new BigDecimal("200.00"), Currency.getInstance("ZAR"))
                    ),
                    Arguments.of(
                            "Different currencies",
                            new Money(new BigDecimal("100.00"), Currency.getInstance("ZAR")),
                            new Money(new BigDecimal("100.00"), Currency.getInstance("EUR"))
                    )
            );
        }
    }

    @Test
    @DisplayName("Should return expected format from toString")
    void testMoney_ToString_ReturnsExpectedFormat() {
        BigDecimal value = new BigDecimal("100.00");
        Currency currency = Currency.getInstance("USD");
        Money money = new Money(value, currency);

        String result = money.toString();
        assertEquals("100.00 USD", result);

    }
}