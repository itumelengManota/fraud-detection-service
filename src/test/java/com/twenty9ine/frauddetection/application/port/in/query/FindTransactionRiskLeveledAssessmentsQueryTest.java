package com.twenty9ine.frauddetection.application.port.in.query;

import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FindHighRiskAssessmentsQuery Tests")
class FindTransactionRiskLeveledAssessmentsQueryTest {

    private Validator validator;
    private Instant since;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        since = Instant.now().minus(24, ChronoUnit.HOURS);
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should pass validation with all required fields")
        void shouldPassValidationWithAllRequiredFields() {
            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .from(since)
                    .build();

            Set<ConstraintViolation<FindRiskLeveledAssessmentsQuery>> violations = validator.validate(query);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should fail validation when from timestamp is null")
        void shouldFailValidationWhenFromIsNull() {
            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .from(null)
                    .build();

            Set<ConstraintViolation<FindRiskLeveledAssessmentsQuery>> violations = validator.validate(query);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("From transactionTimestamp cannot be null");
        }

        @Test
        @DisplayName("Should fail validation when from timestamp is in the future")
        void shouldFailValidationWhenFromIsInFuture() {
            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .from(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            Set<ConstraintViolation<FindRiskLeveledAssessmentsQuery>> violations = validator.validate(query);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("From timestamp cannot be in the future");
        }
    }

    @Nested
    @DisplayName("Record Tests")
    class RecordTests {

        @Test
        @DisplayName("Should be equal when fields are equal")
        void shouldBeEqualWhenFieldsAreEqual() {
            TransactionRiskLevel transactionRiskLevel = TransactionRiskLevel.CRITICAL;
            Instant timestamp = Instant.now();

            FindRiskLeveledAssessmentsQuery query1 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(transactionRiskLevel))
                    .from(timestamp)
                    .build();

            FindRiskLeveledAssessmentsQuery query2 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(transactionRiskLevel))
                    .from(timestamp)
                    .build();

            assertThat(query1).isEqualTo(query2);
            assertThat(query1.hashCode()).hasSameHashCodeAs(query2.hashCode());
        }

        @Test
        @DisplayName("Should be equal when RiskLevels are both null")
        void shouldBeEqualWhenRiskLevelsAreBothNull() {
            Instant timestamp = Instant.now();

            FindRiskLeveledAssessmentsQuery query1 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(null)
                    .from(timestamp)
                    .build();

            FindRiskLeveledAssessmentsQuery query2 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(null)
                    .from(timestamp)
                    .build();

            assertThat(query1).isEqualTo(query2);
            assertThat(query1.hashCode()).hasSameHashCodeAs(query2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when risk levels differ")
        void shouldNotBeEqualWhenRiskLevelsDiffer() {
            Instant timestamp = Instant.now();

            FindRiskLeveledAssessmentsQuery query1 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.HIGH))
                    .from(timestamp)
                    .build();

            FindRiskLeveledAssessmentsQuery query2 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.CRITICAL))
                    .from(timestamp)
                    .build();

            assertThat(query1).isNotEqualTo(query2);
        }

        @Test
        @DisplayName("Should not be equal when timestamps differ")
        void shouldNotBeEqualWhenTimestampsDiffer() {
            TransactionRiskLevel transactionRiskLevel = TransactionRiskLevel.HIGH;

            FindRiskLeveledAssessmentsQuery query1 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(transactionRiskLevel))
                    .from(Instant.now())
                    .build();

            FindRiskLeveledAssessmentsQuery query2 = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(transactionRiskLevel))
                    .from(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            assertThat(query1).isNotEqualTo(query2);
        }
    }
}