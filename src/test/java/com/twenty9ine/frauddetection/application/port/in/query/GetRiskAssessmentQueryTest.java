package com.twenty9ine.frauddetection.application.port.in.query;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GetRiskAssessmentQuery Tests")
class GetRiskAssessmentQueryTest {

    private Validator validator;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        transactionId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should pass validation with valid transaction ID")
        void shouldPassValidationWithValidTransactionId() {
            GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(transactionId);

            Set<ConstraintViolation<GetRiskAssessmentQuery>> violations = validator.validate(query);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should fail validation when transaction ID is null")
        void shouldFailValidationWhenTransactionIdIsNull() {
            GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(null);

            Set<ConstraintViolation<GetRiskAssessmentQuery>> violations = validator.validate(query);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo("Transaction ID cannot be null");
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create query with valid transaction ID")
        void shouldCreateQueryWithValidTransactionId() {
            GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(transactionId);

            assertThat(query.transactionId()).isEqualTo(transactionId);
        }

        @Test
        @DisplayName("Should create query with different transaction IDs")
        void shouldCreateQueryWithDifferentTransactionIds() {
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();

            GetRiskAssessmentQuery query1 = new GetRiskAssessmentQuery(firstId);
            GetRiskAssessmentQuery query2 = new GetRiskAssessmentQuery(secondId);

            assertThat(query1.transactionId()).isEqualTo(firstId);
            assertThat(query2.transactionId()).isEqualTo(secondId);
            assertThat(query1.transactionId()).isNotEqualTo(query2.transactionId());
        }
    }

    @Nested
    @DisplayName("Record Tests")
    class RecordTests {

        @Test
        @DisplayName("Should have correct field value via accessor")
        void shouldHaveCorrectFieldValue() {
            UUID id = UUID.randomUUID();
            GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(id);

            assertThat(query.transactionId()).isEqualTo(id);
        }

        @Test
        @DisplayName("Should be equal when transaction IDs are equal")
        void shouldBeEqualWhenTransactionIdsAreEqual() {
            UUID id = UUID.randomUUID();

            GetRiskAssessmentQuery query1 = new GetRiskAssessmentQuery(id);
            GetRiskAssessmentQuery query2 = new GetRiskAssessmentQuery(id);

            assertThat(query1).isEqualTo(query2);
            assertThat(query1.hashCode()).hasSameHashCodeAs(query2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when transaction IDs differ")
        void shouldNotBeEqualWhenTransactionIdsDiffer() {
            GetRiskAssessmentQuery query1 = new GetRiskAssessmentQuery(UUID.randomUUID());
            GetRiskAssessmentQuery query2 = new GetRiskAssessmentQuery(UUID.randomUUID());

            assertThat(query1).isNotEqualTo(query2);
        }

        @Test
        @DisplayName("Should have proper toString representation")
        void shouldHaveProperToStringRepresentation() {
            UUID id = UUID.randomUUID();
            GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(id);

            String toString = query.toString();

            assertThat(toString).contains("GetRiskAssessmentQuery")
                    .contains(id.toString());
        }

    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should maintain same transaction ID after creation")
        void shouldMaintainSameTransactionIdAfterCreation() {
            UUID id = UUID.randomUUID();
            GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(id);

            UUID retrievedId1 = query.transactionId();
            UUID retrievedId2 = query.transactionId();

            assertThat(retrievedId1).isEqualTo(id);
            assertThat(retrievedId2).isEqualTo(id);
            assertThat(retrievedId1).isSameAs(retrievedId2);
        }
    }
}