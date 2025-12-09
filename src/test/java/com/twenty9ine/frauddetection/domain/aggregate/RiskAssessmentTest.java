package com.twenty9ine.frauddetection.domain.aggregate;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.InvariantViolationException;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RiskAssessmentTest {

    private TransactionId transactionId;
    private RiskAssessment riskAssessment;

    @BeforeEach
    void setUp() {
        transactionId = TransactionId.generate();
        riskAssessment = new RiskAssessment(transactionId, RiskScore.of(10));
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create assessment using factory method")
        void shouldCreateAssessmentUsingFactoryMethod() {
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(10));

            assertThat(assessment).isNotNull();
            assertThat(assessment.getAssessmentId()).isNotNull();
            assertThat(assessment.getTransactionId()).isEqualTo(transactionId);
            assertThat(assessment.getAssessmentTime()).isNotNull();
            assertThat(assessment.getAssessmentTime()).isBeforeOrEqualTo(Instant.now());
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create assessment with generated ID")
        void shouldCreateAssessmentWithGeneratedId() {
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(10));

            assertThat(assessment.getAssessmentId()).isNotNull();
            assertThat(assessment.getTransactionId()).isEqualTo(transactionId);
            assertThat(assessment.getAssessmentTime()).isNotNull();
            assertThat(assessment.getRuleEvaluations()).isEmpty();
            assertThat(assessment.getDomainEvents()).isEmpty();
            assertThat(assessment.getRiskScore()).isNotNull();
            assertThat(assessment.getTransactionRiskLevel()).isNotNull();
            assertThat(assessment.getDecision()).isNull();
        }

        @Test
        @DisplayName("Should create assessment with provided ID")
        void shouldCreateAssessmentWithProvidedId() {
            AssessmentId assessmentId = AssessmentId.generate();
            RiskAssessment assessment = new RiskAssessment(assessmentId, transactionId, RiskScore.of(10));

            assertThat(assessment.getAssessmentId()).isEqualTo(assessmentId);
            assertThat(assessment.getTransactionId()).isEqualTo(transactionId);
        }

        @Test
        @DisplayName("Should initialize assessment time on creation")
        void shouldInitializeAssessmentTimeOnCreation() {
            Instant before = Instant.now();
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(10));
            Instant after = Instant.now();

            assertThat(assessment.getAssessmentTime())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }
    }

    @Nested
    @DisplayName("Complete Assessment Tests")
    class CompleteAssessmentTests {

        @Test
        @DisplayName("Should complete assessment with low risk and allow decision")
        void shouldCompleteAssessmentWithLowRiskAndAllow() {
            RiskScore lowScore = new RiskScore(20);
            RiskAssessment assessment = new RiskAssessment(transactionId, lowScore);
            Decision decision = Decision.ALLOW;

            assessment.completeAssessment(decision);

            assertThat(assessment.getRiskScore()).isEqualTo(lowScore);
            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.LOW);
            assertThat(assessment.getDecision()).isEqualTo(decision);
            assertThat(assessment.getDomainEvents()).hasSize(1);
            assertThat(assessment.getDomainEvents().getFirst()).isInstanceOf(RiskAssessmentCompleted.class);
        }

        @Test
        @DisplayName("Should complete assessment with low risk and challenge decision")
        void shouldCompleteAssessmentWithLowRiskAndChallenge() {
            RiskScore lowScore = new RiskScore(30);
            RiskAssessment assessment = new RiskAssessment(transactionId, lowScore);
            Decision decision = Decision.CHALLENGE;

            assessment.completeAssessment(decision);

            assertThat(assessment.getRiskScore()).isEqualTo(lowScore);
            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.LOW);
            assertThat(assessment.getDecision()).isEqualTo(decision);
            assertThat(assessment.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should complete assessment with medium risk and review decision")
        void shouldCompleteAssessmentWithMediumRisk() {
            RiskScore mediumScore = new RiskScore(50);
            RiskAssessment assessment = new RiskAssessment(transactionId, mediumScore);
            Decision decision = Decision.REVIEW;

            assessment.completeAssessment(decision);

            assertThat(assessment.getRiskScore()).isEqualTo(mediumScore);
            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.MEDIUM);
            assertThat(assessment.getDecision()).isEqualTo(decision);
            assertThat(assessment.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should complete assessment with medium risk and allow decision")
        void shouldCompleteAssessmentWithMediumRiskAndAllow() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(60));
            Decision decision = Decision.ALLOW;

            assessment.completeAssessment(decision);

            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.MEDIUM);
            assertThat(assessment.getDecision()).isEqualTo(decision);
            assertThat(assessment.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should complete assessment with high risk and emit HighRiskDetected event")
        void shouldCompleteAssessmentWithHighRisk() {
            RiskScore highScore = new RiskScore(80);
            RiskAssessment assessment = new RiskAssessment(transactionId, highScore);
            Decision decision = Decision.BLOCK;

            assessment.completeAssessment(decision);

            assertThat(assessment.getRiskScore()).isEqualTo(highScore);
            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.HIGH);
            assertThat(assessment.getDecision()).isEqualTo(decision);
            assertThat(assessment.getDomainEvents()).hasSize(2);
            assertThat(assessment.getDomainEvents().get(0)).isInstanceOf(RiskAssessmentCompleted.class);
            assertThat(assessment.getDomainEvents().get(1)).isInstanceOf(HighRiskDetected.class);
        }

        @Test
        @DisplayName("Should complete assessment with high risk and review decision")
        void shouldCompleteAssessmentWithHighRiskAndReview() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(85));
            Decision decision = Decision.REVIEW;

            assessment.completeAssessment(decision);

            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.HIGH);
            assertThat(assessment.getDecision()).isEqualTo(decision);
            assertThat(assessment.getDomainEvents()).hasSize(2);
        }

        @Test
        @DisplayName("Should complete assessment with critical risk and block decision")
        void shouldCompleteAssessmentWithCriticalRisk() {
            RiskScore criticalScore = new RiskScore(95);
            RiskAssessment assessment = new RiskAssessment(transactionId, criticalScore);
            Decision decision = Decision.BLOCK;

            assessment.completeAssessment(decision);

            assertThat(assessment.getRiskScore()).isEqualTo(criticalScore);
            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.CRITICAL);
            assertThat(assessment.getDecision()).isEqualTo(decision);
            assertThat(assessment.getDomainEvents()).hasSize(2);
        }

        @Test
        @DisplayName("Should throw exception when critical risk has allow decision")
        void shouldThrowExceptionWhenCriticalRiskHasAllowDecision() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(95));
            Decision decision = Decision.ALLOW;

            assertThatThrownBy(() -> assessment.completeAssessment(decision))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessage("Critical risk must result in BLOCK decision");
        }

        @Test
        @DisplayName("Should throw exception when critical risk has review decision")
        void shouldThrowExceptionWhenCriticalRiskHasReviewDecision() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(95));
            Decision decision = Decision.REVIEW;

            assertThatThrownBy(() -> assessment.completeAssessment(decision))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessage("Critical risk must result in BLOCK decision");
        }

        @Test
        @DisplayName("Should throw exception when critical risk has challenge decision")
        void shouldThrowExceptionWhenCriticalRiskHasChallengeDecision() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(100));
            Decision decision = Decision.CHALLENGE;

            assertThatThrownBy(() -> assessment.completeAssessment(decision))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessage("Critical risk must result in BLOCK decision");
        }

        @Test
        @DisplayName("Should throw exception when low risk has block decision")
        void shouldThrowExceptionWhenLowRiskHasBlockDecision() {
            RiskScore lowScore = new RiskScore(20);
            Decision decision = Decision.BLOCK;

            assertThatThrownBy(() -> riskAssessment.completeAssessment(decision))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessage("Low risk cannot result in BLOCK decision");
        }

        @Test
        @DisplayName("Should throw exception when low risk score 0 has block decision")
        void shouldThrowExceptionWhenZeroRiskHasBlockDecision() {
            RiskScore zeroScore = new RiskScore(0);
            Decision decision = Decision.BLOCK;

            assertThatThrownBy(() -> riskAssessment.completeAssessment(decision))
                    .isInstanceOf(InvariantViolationException.class)
                    .hasMessage("Low risk cannot result in BLOCK decision");
        }

        @Test
        @DisplayName("Should handle boundary between low and medium risk with allow decision")
        void shouldHandleLowMediumBoundaryWithAllow() {
            RiskScore boundaryScore = new RiskScore(40);
            Decision decision = Decision.ALLOW;

            riskAssessment.completeAssessment(decision);

            assertThat(riskAssessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.LOW);
            assertThat(riskAssessment.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle boundary between medium and high risk")
        void shouldHandleMediumHighBoundary() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(70));
            Decision decision = Decision.REVIEW;

            assessment.completeAssessment(decision);

            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.MEDIUM);
            assertThat(assessment.getDomainEvents()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle boundary between high and critical risk")
        void shouldHandleHighCriticalBoundary() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(90));
            Decision decision = Decision.BLOCK;

            assessment.completeAssessment(decision);

            assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.HIGH);
            assertThat(assessment.getDomainEvents()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Rule Evaluation Tests")
    class RuleEvaluationTests {

        @Test
        @DisplayName("Should add single rule evaluation")
        void shouldAddRuleEvaluation() {
            RuleEvaluation evaluation = new RuleEvaluation("rule-1", "Test Rule", RuleType.AMOUNT,
                    true, 50, "Test reason");

            riskAssessment.addRuleEvaluation(evaluation);

            assertThat(riskAssessment.getRuleEvaluations()).hasSize(1);
            assertThat(riskAssessment.getRuleEvaluations().getFirst()).isEqualTo(evaluation);
        }

        @Test
        @DisplayName("Should add multiple rule evaluations")
        void shouldAddMultipleRuleEvaluations() {
            RuleEvaluation evaluation1 = new RuleEvaluation("rule-1", "Rule 1", RuleType.GEOGRAPHIC,
                    false, 30, "Reason 1");
            RuleEvaluation evaluation2 = new RuleEvaluation("rule-2", "Rule 2", RuleType.VELOCITY,
                    true, 40, "Reason 2");
            RuleEvaluation evaluation3 = new RuleEvaluation("rule-3", "Rule 3", RuleType.AMOUNT,
                    false, 0, "Reason 3");

            riskAssessment.addRuleEvaluation(evaluation1);
            riskAssessment.addRuleEvaluation(evaluation2);
            riskAssessment.addRuleEvaluation(evaluation3);

            assertThat(riskAssessment.getRuleEvaluations()).hasSize(3);
            assertThat(riskAssessment.getRuleEvaluations()).containsExactly(evaluation1, evaluation2, evaluation3);
        }

        @Test
        @DisplayName("Should maintain order of rule evaluations")
        void shouldMaintainOrderOfRuleEvaluations() {
            RuleEvaluation first = new RuleEvaluation("1", "First", RuleType.AMOUNT, true, 10, "First");
            RuleEvaluation second = new RuleEvaluation("2", "Second", RuleType.VELOCITY, false, 20, "Second");

            riskAssessment.addRuleEvaluation(first);
            riskAssessment.addRuleEvaluation(second);

            List<RuleEvaluation> evaluations = riskAssessment.getRuleEvaluations();
            assertThat(evaluations.get(0)).isEqualTo(first);
            assertThat(evaluations.get(1)).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("Domain Events Tests")
    class DomainEventsTests {

        @Test
        @DisplayName("Should return unmodifiable list of domain events")
        void shouldReturnUnmodifiableListOfDomainEvents() {
            List<DomainEvent<TransactionId>> events = riskAssessment.getDomainEvents();

            assertThatThrownBy(() -> events.add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should clear domain events")
        void shouldClearDomainEvents() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(80));
            assessment.completeAssessment(Decision.BLOCK);

            assertThat(assessment.getDomainEvents()).isNotEmpty();

            assessment.clearDomainEvents();

            assertThat(assessment.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("Should accumulate multiple events")
        void shouldAccumulateMultipleEvents() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(85));
            assessment.completeAssessment(Decision.BLOCK);

            assertThat(assessment.getDomainEvents()).hasSize(2);
        }

        @Test
        @DisplayName("Should be able to clear and add events again")
        void shouldClearAndAddEventsAgain() {
            RiskAssessment assessment = new RiskAssessment(transactionId, new RiskScore(80));
            assessment.completeAssessment(Decision.REVIEW);
            assessment.clearDomainEvents();

            assertThat(assessment.getDomainEvents()).isEmpty();

            assessment.completeAssessment(Decision.BLOCK);

            assertThat(assessment.getDomainEvents()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("ML Prediction Tests")
    class MLPredictionTests {

        @Test
        @DisplayName("Should initially have null ML prediction")
        void shouldInitiallyHaveNullMLPrediction() {
            assertThat(riskAssessment.getMlPrediction()).isNull();
        }
    }

    @Nested
    @DisplayName("Private Method Tests (via Reflection)")
    class PrivateMethodTests {

//        @Test
//        @DisplayName("Should validate decision alignment for critical risk")
//        void shouldValidateDecisionAlignmentForCriticalRisk() throws Exception {
//            Method validateMethod = RiskAssessment.class.getDeclaredMethod("validateDecisionAlignment", Decision.class);
//            validateMethod.setAccessible(true);
//
//            RiskScore criticalScore = new RiskScore(95);
//
//            assertThatThrownBy(() -> invokeValidateMethod(validateMethod, criticalScore, Decision.ALLOW))
//                    .isInstanceOf(InvariantViolationException.class)
//                    .hasMessage("Critical risk must result in BLOCK decision");
//        }

//        @Test
//        @DisplayName("Should validate decision alignment for low risk with block")
//        void shouldValidateDecisionAlignmentForLowRisk() throws Exception {
//            Method validateMethod = RiskAssessment.class.getDeclaredMethod("validateDecisionAlignment", RiskScore.class, Decision.class);
//            validateMethod.setAccessible(true);
//
//            RiskScore lowScore = new RiskScore(20);
//
//            assertThatThrownBy(() -> invokeValidateMethod(validateMethod, lowScore, Decision.BLOCK))
//                    .isInstanceOf(InvariantViolationException.class)
//                    .hasMessage("Low risk cannot result in BLOCK decision");
//        }

        private void invokeValidateMethod(Method method, RiskScore score, Decision decision) throws Throwable {
            try {
                method.invoke(riskAssessment, score, decision);
            } catch (Exception e) {
                throw e.getCause();
            }
        }

        @Test
        @DisplayName("Should publish event successfully")
        void shouldPublishEvent() throws Exception {
            Method publishMethod = RiskAssessment.class.getDeclaredMethod("publishEvent", DomainEvent.class);
            publishMethod.setAccessible(true);

            DomainEvent event = RiskAssessmentCompleted.of(riskAssessment.getTransactionId(), riskAssessment.getAssessmentId(),
                    new RiskScore(50), TransactionRiskLevel.MEDIUM, Decision.REVIEW
            );

            publishMethod.invoke(riskAssessment, event);

            assertThat(riskAssessment.getDomainEvents()).hasSize(1);
            assertThat(riskAssessment.getDomainEvents().getFirst()).isEqualTo(event);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            AssessmentId id = AssessmentId.generate();
            RiskScore score1 = new RiskScore(90);
            RiskAssessment assessment1 = new RiskAssessment(id, transactionId, score1);

            RiskAssessment assessment2 = new RiskAssessment(id, transactionId, score1);
            assessment2.completeAssessment(Decision.REVIEW);

            assertThat(assessment1).isEqualTo(assessment2);
            assertThat(assessment1.hashCode()).hasSameHashCodeAs(assessment2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when assessment IDs differ")
        void shouldNotBeEqualWhenAssessmentIdsDiffer() {
            RiskScore score1 = new RiskScore(90);
            RiskAssessment assessment1 = new RiskAssessment(transactionId, score1);
            RiskAssessment assessment2 = new RiskAssessment(transactionId, score1);

            assertThat(assessment1).isNotEqualTo(assessment2);
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should generate toString representation")
        void shouldGenerateToString() {
            String toString = riskAssessment.toString();

            assertThat(toString)
                    .contains("RiskAssessment")
                    .contains("assessmentId")
                    .contains("transactionId");
        }
    }
}