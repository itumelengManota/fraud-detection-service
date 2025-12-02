package com.twenty9ine.frauddetection.application.port.in.query;

import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
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
class FindHighRiskAssessmentsQueryTest {

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
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .since(since)
                    .build();

            Set<ConstraintViolation<FindHighRiskAssessmentsQuery>> violations = validator.validate(query);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should fail validation when riskLevel is null")
        void shouldFailValidationWhenRiskLevelIsNull() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(null)
                    .since(since)
                    .build();

            Set<ConstraintViolation<FindHighRiskAssessmentsQuery>> violations = validator.validate(query);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Risk level cannot be null");
        }

        @Test
        @DisplayName("Should fail validation when since timestamp is null")
        void shouldFailValidationWhenSinceIsNull() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .since(null)
                    .build();

            Set<ConstraintViolation<FindHighRiskAssessmentsQuery>> violations = validator.validate(query);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Since transactionTimestamp cannot be null");
        }

        @Test
        @DisplayName("Should fail validation when both fields are null")
        void shouldFailValidationWhenBothFieldsAreNull() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(null)
                    .since(null)
                    .build();

            Set<ConstraintViolation<FindHighRiskAssessmentsQuery>> violations = validator.validate(query);

            assertThat(violations).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build query with HIGH risk level")
        void shouldBuildQueryWithHighRiskLevel() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .since(since)
                    .build();

            assertThat(query.riskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(query.since()).isEqualTo(since);
        }

        @Test
        @DisplayName("Should build query with CRITICAL risk level")
        void shouldBuildQueryWithCriticalRiskLevel() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.CRITICAL)
                    .since(since)
                    .build();

            assertThat(query.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        }

        @Test
        @DisplayName("Should build query with MEDIUM risk level")
        void shouldBuildQueryWithMediumRiskLevel() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.MEDIUM)
                    .since(since)
                    .build();

            assertThat(query.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        }

        @Test
        @DisplayName("Should build query with LOW risk level")
        void shouldBuildQueryWithLowRiskLevel() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.LOW)
                    .since(since)
                    .build();

            assertThat(query.riskLevel()).isEqualTo(RiskLevel.LOW);
        }

        @Test
        @DisplayName("Should build query with recent timestamp")
        void shouldBuildQueryWithRecentTimestamp() {
            Instant recentTime = Instant.now().minus(1, ChronoUnit.HOURS);

            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .since(recentTime)
                    .build();

            assertThat(query.since()).isEqualTo(recentTime);
        }

        @Test
        @DisplayName("Should build query with older timestamp")
        void shouldBuildQueryWithOlderTimestamp() {
            Instant olderTime = Instant.now().minus(7, ChronoUnit.DAYS);

            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.CRITICAL)
                    .since(olderTime)
                    .build();

            assertThat(query.since()).isEqualTo(olderTime);
        }
    }

    @Nested
    @DisplayName("Record Tests")
    class RecordTests {

        @Test
        @DisplayName("Should have correct field values via accessors")
        void shouldHaveCorrectFieldValues() {
            RiskLevel riskLevel = RiskLevel.HIGH;
            Instant timestamp = Instant.now();

            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(riskLevel)
                    .since(timestamp)
                    .build();

            assertThat(query.riskLevel()).isEqualTo(riskLevel);
            assertThat(query.since()).isEqualTo(timestamp);
        }

        @Test
        @DisplayName("Should be equal when fields are equal")
        void shouldBeEqualWhenFieldsAreEqual() {
            RiskLevel riskLevel = RiskLevel.CRITICAL;
            Instant timestamp = Instant.now();

            FindHighRiskAssessmentsQuery query1 = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(riskLevel)
                    .since(timestamp)
                    .build();

            FindHighRiskAssessmentsQuery query2 = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(riskLevel)
                    .since(timestamp)
                    .build();

            assertThat(query1).isEqualTo(query2);
            assertThat(query1.hashCode()).hasSameHashCodeAs(query2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when risk levels differ")
        void shouldNotBeEqualWhenRiskLevelsDiffer() {
            Instant timestamp = Instant.now();

            FindHighRiskAssessmentsQuery query1 = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .since(timestamp)
                    .build();

            FindHighRiskAssessmentsQuery query2 = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.CRITICAL)
                    .since(timestamp)
                    .build();

            assertThat(query1).isNotEqualTo(query2);
        }

        @Test
        @DisplayName("Should not be equal when timestamps differ")
        void shouldNotBeEqualWhenTimestampsDiffer() {
            RiskLevel riskLevel = RiskLevel.HIGH;

            FindHighRiskAssessmentsQuery query1 = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(riskLevel)
                    .since(Instant.now())
                    .build();

            FindHighRiskAssessmentsQuery query2 = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(riskLevel)
                    .since(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            assertThat(query1).isNotEqualTo(query2);
        }

        @Test
        @DisplayName("Should have proper toString representation")
        void shouldHaveProperToStringRepresentation() {
            FindHighRiskAssessmentsQuery query = FindHighRiskAssessmentsQuery.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .since(since)
                    .build();

            String toString = query.toString();

            assertThat(toString).contains("FindHighRiskAssessmentsQuery")
                                .contains("HIGH");
        }
    }
}