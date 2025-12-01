package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class MLPredictionTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Constructor and Factory Method Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create ML prediction with all fields")
        void shouldCreateMLPredictionWithAllFields() {
            Map<String, Double> features = Map.of(
                    "amount", 0.4,
                    "velocity", 0.3,
                    "location", 0.3
            );

            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.85,
                    0.92,
                    features
            );

            assertThat(prediction.modelId()).isEqualTo("model-v1");
            assertThat(prediction.modelVersion()).isEqualTo("1.0.0");
            assertThat(prediction.fraudProbability()).isEqualTo(0.85);
            assertThat(prediction.confidence()).isEqualTo(0.92);
            assertThat(prediction.featureImportance()).isEqualTo(features);
        }

        @Test
        @DisplayName("Should create unavailable ML prediction using factory method")
        void shouldCreateUnavailableMLPrediction() {
            MLPrediction prediction = MLPrediction.unavailable();

            assertThat(prediction.modelId()).isEqualTo("unavailable");
            assertThat(prediction.modelVersion()).isEqualTo("0.0.0");
            assertThat(prediction.fraudProbability()).isEqualTo(0.0);
            assertThat(prediction.confidence()).isEqualTo(0.0);
            assertThat(prediction.featureImportance()).isEmpty();
        }

        @Test
        @DisplayName("Should create prediction with empty feature importance")
        void shouldCreatePredictionWithEmptyFeatures() {
            MLPrediction prediction = new MLPrediction(
                    "model-v2",
                    "2.0.0",
                    0.5,
                    0.8,
                    Map.of()
            );

            assertThat(prediction.featureImportance()).isEmpty();
        }

        @Test
        @DisplayName("Should create prediction with zero probabilities")
        void shouldCreatePredictionWithZeroProbabilities() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.0,
                    0.0,
                    Map.of()
            );

            assertThat(prediction.fraudProbability()).isEqualTo(0.0);
            assertThat(prediction.confidence()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should create prediction with maximum probabilities")
        void shouldCreatePredictionWithMaximumProbabilities() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    1.0,
                    1.0,
                    Map.of()
            );

            assertThat(prediction.fraudProbability()).isEqualTo(1.0);
            assertThat(prediction.confidence()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Bean Validation Constraint Tests - Fraud Probability")
    class FraudProbabilityValidationTests {

        @Test
        @DisplayName("Should pass validation when fraud probability is 0.0")
        void shouldPassValidationWhenFraudProbabilityIsZero() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.0,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation when fraud probability is 1.0")
        void shouldPassValidationWhenFraudProbabilityIsOne() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    1.0,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation when fraud probability is within range")
        void shouldPassValidationWhenFraudProbabilityIsWithinRange() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should fail validation when fraud probability is below 0.0")
        void shouldFailValidationWhenFraudProbabilityIsBelowZero() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    -0.1,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("Fraud probability must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("Should fail validation when fraud probability is above 1.0")
        void shouldFailValidationWhenFraudProbabilityIsAboveOne() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    1.1,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("Fraud probability must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("Should fail validation when fraud probability is significantly negative")
        void shouldFailValidationWhenFraudProbabilityIsSignificantlyNegative() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    -5.0,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
        }

        @Test
        @DisplayName("Should fail validation when fraud probability is significantly above range")
        void shouldFailValidationWhenFraudProbabilityIsSignificantlyAboveRange() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    10.0,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Bean Validation Constraint Tests - Confidence")
    class ConfidenceValidationTests {

        @Test
        @DisplayName("Should pass validation when confidence is 0.0")
        void shouldPassValidationWhenConfidenceIsZero() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    0.0,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation when confidence is 1.0")
        void shouldPassValidationWhenConfidenceIsOne() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    1.0,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation when confidence is within range")
        void shouldPassValidationWhenConfidenceIsWithinRange() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    0.75,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should fail validation when confidence is below 0.0")
        void shouldFailValidationWhenConfidenceIsBelowZero() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    -0.1,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("Confidence must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("Should fail validation when confidence is above 1.0")
        void shouldFailValidationWhenConfidenceIsAboveOne() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    1.5,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("Confidence must be between 0.0 and 1.0");
        }

        @Test
        @DisplayName("Should fail validation when confidence is significantly negative")
        void shouldFailValidationWhenConfidenceIsSignificantlyNegative() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    -10.0,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
        }

        @Test
        @DisplayName("Should fail validation when confidence is significantly above range")
        void shouldFailValidationWhenConfidenceIsSignificantlyAboveRange() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    100.0,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Bean Validation Constraint Tests - Multiple Violations")
    class MultipleViolationsTests {

        @Test
        @DisplayName("Should fail validation with both constraints violated")
        void shouldFailValidationWithBothConstraintsViolated() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    -0.5,
                    1.5,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(2);
        }

        @Test
        @DisplayName("Should fail validation when both values exceed upper bound")
        void shouldFailValidationWhenBothValuesExceedUpperBound() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    2.0,
                    3.0,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(2);
        }

        @Test
        @DisplayName("Should fail validation when both values are below lower bound")
        void shouldFailValidationWhenBothValuesBelowLowerBound() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    -1.0,
                    -2.0,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            Map<String, Double> features = Map.of("amount", 0.5);

            MLPrediction prediction1 = new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, features);
            MLPrediction prediction2 = new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, features);

            assertThat(prediction1).isEqualTo(prediction2);
            assertThat(prediction1.hashCode()).isEqualTo(prediction2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when model IDs differ")
        void shouldNotBeEqualWhenModelIdsDiffer() {
            MLPrediction prediction1 = new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, Map.of());
            MLPrediction prediction2 = new MLPrediction("model-v2", "1.0.0", 0.75, 0.85, Map.of());

            assertThat(prediction1).isNotEqualTo(prediction2);
        }

        @Test
        @DisplayName("Should not be equal when probabilities differ")
        void shouldNotBeEqualWhenProbabilitiesDiffer() {
            MLPrediction prediction1 = new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, Map.of());
            MLPrediction prediction2 = new MLPrediction("model-v1", "1.0.0", 0.80, 0.85, Map.of());

            assertThat(prediction1).isNotEqualTo(prediction2);
        }

        @Test
        @DisplayName("Should be equal to itself")
        void shouldBeEqualToItself() {
            MLPrediction prediction = new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, Map.of());

            assertThat(prediction).isEqualTo(prediction);
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should generate toString representation")
        void shouldGenerateToString() {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.75,
                    0.85,
                    Map.of("amount", 0.5)
            );

            String toString = prediction.toString();

            assertThat(toString)
                    .contains("MLPrediction")
                    .contains("model-v1")
                    .contains("1.0.0")
                    .contains("0.75")
                    .contains("0.85");
        }
    }
}