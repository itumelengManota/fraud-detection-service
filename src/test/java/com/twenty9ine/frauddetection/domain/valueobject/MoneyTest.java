package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testMoney_WithNullValue_ViolatesNotNullConstraint() {
        Currency currency = Currency.getInstance("ZAR");
        Money money = new Money(null, currency);

        Set<ConstraintViolation<Money>> violations = validator.validate(money);

        assertEquals(1, violations.size());
        ConstraintViolation<Money> violation = violations.iterator().next();
        assertEquals("Value of Money cannot be null", violation.getMessage());
        assertEquals("value", violation.getPropertyPath().toString());
    }

    @Test
    void testMoney_WithNullCurrency_ViolatesNotNullConstraint() {
        BigDecimal value = new BigDecimal("100.00");
        Money money = new Money(value, null);

        Set<ConstraintViolation<Money>> violations = validator.validate(money);

        assertEquals(1, violations.size());
        ConstraintViolation<Money> violation = violations.iterator().next();
        assertEquals("Currency of Money cannot be null", violation.getMessage());
        assertEquals("currency", violation.getPropertyPath().toString());
    }

    @Test
    void testMoney_WithBothNullFields_ViolatesBothNotNullConstraints() {
        Money money = new Money(null, null);

        Set<ConstraintViolation<Money>> violations = validator.validate(money);

        assertEquals(2, violations.size());
    }

    @Test
    void testMoney_WithValidFields_PassesValidation() {
        BigDecimal value = new BigDecimal("100.00");
        Currency currency = Currency.getInstance("ZAR");
        Money money = new Money(value, currency);

        Set<ConstraintViolation<Money>> violations = validator.validate(money);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testMoney_ValidValueAndCurrency_CreatesInstance() {
        BigDecimal value = new BigDecimal("100.00");
        Currency currency = Currency.getInstance("ZAR");

        Money money = new Money(value, currency);

        assertEquals(value, money.value());
        assertEquals(currency, money.currency());
    }

    @Test
    void testMoney_WithZeroValue_CreatesInstance() {
        BigDecimal value = BigDecimal.ZERO;
        Currency currency = Currency.getInstance("EUR");

        Money money = new Money(value, currency);

        assertEquals(BigDecimal.ZERO, money.value());
        assertEquals(currency, money.currency());
    }

    @Test
    void testMoney_WithNegativeValue_CreatesInstance() {
        BigDecimal value = new BigDecimal("-50.00");
        Currency currency = Currency.getInstance("GBP");

        Money money = new Money(value, currency);

        assertEquals(value, money.value());
        assertEquals(currency, money.currency());
    }

    @Test
    void testMoney_WithLargeValue_CreatesInstance() {
        BigDecimal value = new BigDecimal("999999999.99");
        Currency currency = Currency.getInstance("JPY");

        Money money = new Money(value, currency);

        assertEquals(value, money.value());
        assertEquals(currency, money.currency());
    }

    @Test
    void testMoney_WithDifferentCurrencies_CreatesDistinctInstances() {
        BigDecimal value = new BigDecimal("100.00");
        Money zar = new Money(value, Currency.getInstance("ZAR"));
        Money eur = new Money(value, Currency.getInstance("EUR"));

        assertNotEquals(zar, eur);
        assertNotEquals(zar.currency(), eur.currency());
    }

    @Test
    void testMoney_EqualityWithSameValueAndCurrency_ReturnsTrue() {
        BigDecimal value = new BigDecimal("100.00");
        Currency currency = Currency.getInstance("ZAR");
        Money money1 = new Money(value, currency);
        Money money2 = new Money(value, currency);

        assertEquals(money1, money2);
        assertEquals(money1.hashCode(), money2.hashCode());
    }

    @Test
    void testMoney_EqualityWithDifferentValues_ReturnsFalse() {
        Currency currency = Currency.getInstance("ZAR");
        Money money1 = new Money(new BigDecimal("100.00"), currency);
        Money money2 = new Money(new BigDecimal("200.00"), currency);

        assertNotEquals(money1, money2);
    }

    @Test
    void testMoney_EqualityWithDifferentCurrencies_ReturnsFalse() {
        BigDecimal value = new BigDecimal("100.00");
        Money money1 = new Money(value, Currency.getInstance("ZAR"));
        Money money2 = new Money(value, Currency.getInstance("EUR"));

        assertNotEquals(money1, money2);
    }

    @Test
    void testMoney_WithPreciseDecimalValue_PreservesPrecision() {
        BigDecimal value = new BigDecimal("100.123456789");
        Currency currency = Currency.getInstance("ZAR");

        Money money = new Money(value, currency);

        assertEquals(value, money.value());
        assertEquals("100.123456789", money.value().toPlainString());
    }

    @Test
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

    @Test
    void testMoney_ToString_ReturnsExpectedFormat() {
        BigDecimal value = new BigDecimal("100.00");
        Currency currency = Currency.getInstance("USD");
        Money money = new Money(value, currency);

        String result = money.toString();
        assertEquals("100.00 USD", result);

    }
}