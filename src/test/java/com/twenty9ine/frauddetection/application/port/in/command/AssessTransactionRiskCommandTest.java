package com.twenty9ine.frauddetection.application.port.in.command;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AssessTransactionRiskCommand Tests")
class AssessTransactionRiskCommandTest {

    private Validator validator;
    private UUID transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
    private LocationDto location;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        transactionId = UUID.randomUUID();
        accountId = "ACC123456";
        amount = new BigDecimal("100.00");
        currency = "USD";
        timestamp = Instant.now();
        location = new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now());
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should pass validation with all required fields")
        void shouldPassValidationWithAllRequiredFields() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should fail validation when transactionId is null")
        void shouldFailValidationWhenTransactionIdIsNull() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(null)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Transaction ID cannot be null");
        }

        @Test
        @DisplayName("Should fail validation when accountId is null")
        void shouldFailValidationWhenAccountIdIsNull() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(null)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Account ID cannot be null");
        }

        @Test
        @DisplayName("Should fail validation when amount is null")
        void shouldFailValidationWhenAmountIsNull() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(null)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Amount cannot be null");
        }

        @Test
        @DisplayName("Should fail validation when currency is null")
        void shouldFailValidationWhenCurrencyIsNull() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(null)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Currency cannot be null");
        }

        @Test
        @DisplayName("Should fail validation when type is null")
        void shouldFailValidationWhenTypeIsNull() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(null)
                    .channel(Channel.ONLINE.name())
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Transaction type cannot be empty");
        }

        @Test
        @DisplayName("Should fail validation when channel is null")
        void shouldFailValidationWhenChannelIsNull() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(null)
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Channel cannot be empty");
        }

        @Test
        @DisplayName("Should fail validation when timestamp is null")
        void shouldFailValidationWhenTimestampIsNull() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .transactionTimestamp(null)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Timestamp cannot be null");
        }

        @Test
        @DisplayName("Should validate nested LocationDto when present")
        void shouldValidateNestedLocationDto() {
            LocationDto invalidLocation = new LocationDto(0, 0, null, null, null);

            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .location(invalidLocation)
                    .transactionTimestamp(timestamp)
                    .build();

            Set<ConstraintViolation<AssessTransactionRiskCommand>> violations = validator.validate(command);

            assertThat(violations).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build command with all fields using builder")
        void shouldBuildCommandWithAllFields() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.PURCHASE.name())
                    .channel(Channel.ONLINE.name())
                    .merchantId("MERCHANT123")
                    .merchantName("Test Merchant")
                    .merchantCategory("Retail")
                    .location(location)
                    .deviceId("DEVICE456")
                    .transactionTimestamp(timestamp)
                    .build();

            assertThat(command.transactionId()).isEqualTo(transactionId);
            assertThat(command.accountId()).isEqualTo(accountId);
            assertThat(command.amount()).isEqualTo(amount);
            assertThat(command.currency()).isEqualTo(currency);
            assertThat(command.type()).isEqualTo(TransactionType.PURCHASE.name());
            assertThat(command.channel()).isEqualTo(Channel.ONLINE.name());
            assertThat(command.merchantId()).isEqualTo("MERCHANT123");
            assertThat(command.merchantName()).isEqualTo("Test Merchant");
            assertThat(command.merchantCategory()).isEqualTo("Retail");
            assertThat(command.location()).isEqualTo(location);
            assertThat(command.deviceId()).isEqualTo("DEVICE456");
            assertThat(command.transactionTimestamp()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("Should build command with only required fields")
        void shouldBuildCommandWithOnlyRequiredFields() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(transactionId)
                    .accountId(accountId)
                    .amount(amount)
                    .currency(currency)
                    .type(TransactionType.ATM_WITHDRAWAL.name())
                    .channel(Channel.ATM.name())
                    .transactionTimestamp(timestamp)
                    .build();

            assertThat(command.transactionId()).isEqualTo(transactionId);
            assertThat(command.merchantId()).isNull();
            assertThat(command.location()).isNull();
            assertThat(command.deviceId()).isNull();
        }
    }
}