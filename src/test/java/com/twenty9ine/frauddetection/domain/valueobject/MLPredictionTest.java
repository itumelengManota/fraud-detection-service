package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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

        @ParameterizedTest(name = "{0}")
        @MethodSource("boundaryValueProvider")
        @DisplayName("Should create prediction with boundary values")
        void shouldCreatePredictionWithBoundaryValues(String description, double fraudProbability, double confidence, Map<String, Double> features) {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    fraudProbability,
                    confidence,
                    features
            );

            assertThat(prediction.fraudProbability()).isEqualTo(fraudProbability);
            assertThat(prediction.confidence()).isEqualTo(confidence);
            assertThat(prediction.featureImportance()).isEqualTo(features);
        }

        static Stream<Arguments> boundaryValueProvider() {
            return Stream.of(
                    Arguments.of("Empty feature importance", 0.5, 0.8, Map.of()),
                    Arguments.of("Zero probabilities", 0.0, 0.0, Map.of()),
                    Arguments.of("Maximum probabilities", 1.0, 1.0, Map.of())
            );
        }
    }

    @Nested
    @DisplayName("Bean Validation Constraint Tests - Fraud Probability")
    class FraudProbabilityValidationTests {

        @ParameterizedTest(name = "Fraud probability {0} should pass validation")
        @CsvSource({
                "0.0",
                "0.5",
                "1.0"
        })
        @DisplayName("Should pass validation for valid fraud probability values")
        void shouldPassValidationForValidFraudProbability(double fraudProbability) {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    fraudProbability,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @ParameterizedTest(name = "Fraud probability {0} should fail validation")
        @CsvSource({
                "-0.1",
                "1.1",
                "-5.0",
                "10.0"
        })
        @DisplayName("Should fail validation for invalid fraud probability values")
        void shouldFailValidationForInvalidFraudProbability(double fraudProbability) {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    fraudProbability,
                    0.8,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("Fraud probability must be between 0.0 and 1.0");
        }
    }

    @Nested
    @DisplayName("Bean Validation Constraint Tests - Confidence")
    class ConfidenceValidationTests {

        @ParameterizedTest(name = "Confidence {0} should pass validation")
        @CsvSource({
                "0.0",
                "0.75",
                "1.0"
        })
        @DisplayName("Should pass validation for valid confidence values")
        void shouldPassValidationForValidConfidence(double confidence) {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    confidence,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).isEmpty();
        }

        @ParameterizedTest(name = "Confidence {0} should fail validation")
        @CsvSource({
                "-0.1",
                "1.5",
                "-10.0",
                "100.0"
        })
        @DisplayName("Should fail validation for invalid confidence values")
        void shouldFailValidationForInvalidConfidence(double confidence) {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    0.5,
                    confidence,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("Confidence must be between 0.0 and 1.0");
        }
    }

    @Nested
    @DisplayName("Bean Validation Constraint Tests - Multiple Violations")
    class MultipleViolationsTests {

        @ParameterizedTest(name = "Fraud probability {0} and confidence {1} should fail with {2} violations")
        @CsvSource({
                "-0.5, 1.5,  2",
                "2.0,  3.0,  2",
                "-1.0, -2.0, 2"
        })
        @DisplayName("Should fail validation with multiple constraint violations")
        void shouldFailValidationWithMultipleViolations(double fraudProbability, double confidence, int expectedViolations) {
            MLPrediction prediction = new MLPrediction(
                    "model-v1",
                    "1.0.0",
                    fraudProbability,
                    confidence,
                    Map.of()
            );

            Set<ConstraintViolation<MLPrediction>> violations = validator.validate(prediction);

            assertThat(violations).hasSize(expectedViolations);
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
            assertThat(prediction1.hashCode()).hasSameHashCodeAs(prediction2.hashCode());
        }

        @Test
        @DisplayName("Should be equal to itself")
        void shouldBeEqualToItself() {
            MLPrediction prediction = new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, Map.of());

            assertThat(prediction).isEqualTo(prediction);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("inequalityProvider")
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer(String description, MLPrediction prediction1, MLPrediction prediction2) {
            assertThat(prediction1).isNotEqualTo(prediction2);
        }

        static Stream<Arguments> inequalityProvider() {
            return Stream.of(
                    Arguments.of(
                            "Model IDs differ",
                            new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, Map.of()),
                            new MLPrediction("model-v2", "1.0.0", 0.75, 0.85, Map.of())
                    ),
                    Arguments.of(
                            "Probabilities differ",
                            new MLPrediction("model-v1", "1.0.0", 0.75, 0.85, Map.of()),
                            new MLPrediction("model-v1", "1.0.0", 0.80, 0.85, Map.of())
                    )
            );
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