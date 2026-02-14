package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import com.twenty9ine.frauddetection.domain.valueobject.RiskScore;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.CONCURRENT)
class DecisionServiceTest {

    private DecisionService decisionService;

    @BeforeEach
    void setUp() {
        List<DecisionStrategy> strategies = List.of(
                new LowRiskStrategy(),
                new MediumRiskStrategy(),
                new HighRiskStrategy(),
                new CriticalRiskStrategy()
        );
        decisionService = new DecisionService(strategies);
    }

    @Nested
    @DisplayName("Decision Making Tests")
    class DecisionMakingTests {

        @ParameterizedTest(name = "Should return {1} for risk score {0} with risk level {2}")
        @CsvSource({
                "10,  ALLOW,     LOW",
                "50,  CHALLENGE, MEDIUM",
                "75,  REVIEW,    HIGH",
                "95,  BLOCK,     CRITICAL"
        })
        @DisplayName("Should make correct decisions for different risk scores")
        void shouldMakeCorrectDecisionsForRiskScores(int score, Decision expectedDecision, TransactionRiskLevel expectedRiskLevel) {
            RiskAssessment assessment = createAssessment(expectedDecision, RiskScore.of(score));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(expectedDecision, decision);
            assertEquals(expectedRiskLevel, assessment.getTransactionRiskLevel());
        }
    }

    @Nested
    @DisplayName("Risk Level Boundary Tests")
    class TransactionRiskLevelBoundaryTests {

        @ParameterizedTest(name = "Score {0} should be classified as {1} with decision {2}")
        @CsvSource({
                "0,   LOW,      ALLOW",
                "29,  LOW,      ALLOW",
                "30,  LOW,      ALLOW",
                "59,  MEDIUM,   CHALLENGE",
                "60,  MEDIUM,   CHALLENGE",
                "84,  HIGH,     REVIEW",
                "85,  HIGH,     REVIEW",
                "100, CRITICAL, BLOCK"
        })
        @DisplayName("Should classify risk scores at boundary values correctly")
        void shouldClassifyRiskScoresAtBoundaries(int score, TransactionRiskLevel expectedRiskLevel, Decision expectedDecision) {
            Decision initialDecision = switch (expectedDecision) {
                case ALLOW -> Decision.ALLOW;
                case CHALLENGE -> Decision.ALLOW;
                case REVIEW -> Decision.REVIEW;
                case BLOCK -> Decision.BLOCK;
            };

            RiskAssessment assessment = createAssessment(initialDecision, RiskScore.of(score));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(expectedRiskLevel, assessment.getTransactionRiskLevel());
            assertEquals(expectedDecision, decision);
        }
    }

    @Nested
    @DisplayName("Strategy Selection Tests")
    class StrategySelectionTests {

        @Test
        @DisplayName("Should find correct strategy for each risk level")
        void shouldFindCorrectStrategyForEachRiskLevel() {
            assertDecisionForLevel(TransactionRiskLevel.LOW, Decision.ALLOW);
            assertDecisionForLevel(TransactionRiskLevel.MEDIUM, Decision.CHALLENGE);
            assertDecisionForLevel(TransactionRiskLevel.HIGH, Decision.REVIEW);
            assertDecisionForLevel(TransactionRiskLevel.CRITICAL, Decision.BLOCK);
        }

        private void assertDecisionForLevel(TransactionRiskLevel level, Decision expectedDecision) {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(50));
            assessment.completeAssessment(Decision.ALLOW);

            List<DecisionStrategy> strategies = List.of(
                    new LowRiskStrategy(),
                    new MediumRiskStrategy(),
                    new HighRiskStrategy(),
                    new CriticalRiskStrategy()
            );
            DecisionStrategy strategy = strategies.stream()
                    .filter(s -> s.getRiskLevel().equals(level))
                    .findFirst()
                    .orElseThrow();

            Decision decision = strategy.decide(assessment);

            assertEquals(expectedDecision, decision);
        }
    }

    @Nested
    @DisplayName("Assessment State Tests")
    class AssessmentStateTests {

        @Test
        @DisplayName("Should complete assessment with correct risk level and decision")
        void shouldCompleteAssessmentWithCorrectRiskLevelAndDecision() {
            RiskAssessment assessment = createAssessment(Decision.CHALLENGE, RiskScore.of(70));
            decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.MEDIUM, assessment.getTransactionRiskLevel());
            assertEquals(Decision.CHALLENGE, assessment.getDecision());
        }

        @Test
        @DisplayName("Should set assessment time when completing assessment")
        void shouldSetAssessmentTimeWhenCompletingAssessment() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(40));
            decisionService.makeDecision(assessment);

            assertNotNull(assessment.getAssessmentTime());
        }
    }

    @Nested
    @DisplayName("Multiple Assessment Tests")
    class MultipleAssessmentTests {

        @Test
        @DisplayName("Should handle multiple assessments independently")
        void shouldHandleMultipleAssessmentsIndependently() {
            RiskAssessment lowRisk = createAssessment(Decision.ALLOW, RiskScore.of(10));
            RiskAssessment mediumRisk = createAssessment(Decision.CHALLENGE, RiskScore.of(45));
            RiskAssessment highRisk = createAssessment(Decision.REVIEW, RiskScore.of(90));
            RiskAssessment criticalRisk = createAssessment(Decision.BLOCK, RiskScore.of(91));

            Decision lowDecision = decisionService.makeDecision(lowRisk);
            Decision mediumDecision = decisionService.makeDecision(mediumRisk);
            Decision highDecision = decisionService.makeDecision(highRisk);
            Decision criticalDecision = decisionService.makeDecision(criticalRisk);

            assertEquals(Decision.ALLOW, lowDecision);
            assertEquals(Decision.CHALLENGE, mediumDecision);
            assertEquals(Decision.REVIEW, highDecision);
            assertEquals(Decision.BLOCK, criticalDecision);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle assessment without ML prediction")
        void shouldHandleAssessmentWithoutMLPrediction() {
            RiskAssessment assessment = new RiskAssessment(TransactionId.of(UUID.randomUUID()), RiskScore.of(70));
            assessment.completeAssessment(Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertNotNull(decision);
            assertEquals(TransactionRiskLevel.MEDIUM, assessment.getTransactionRiskLevel());
        }

        @Test
        @DisplayName("Should handle assessment without rule evaluations")
        void shouldHandleAssessmentWithoutRuleEvaluations() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(35));

            Decision decision = decisionService.makeDecision(assessment);

            assertNotNull(decision);
            assertEquals(Decision.ALLOW, decision);
        }
    }

    private RiskAssessment createAssessment(Decision decision, RiskScore riskScore) {
        RiskAssessment assessment = new RiskAssessment(TransactionId.generate(), riskScore);
        assessment.completeAssessment(decision);

        return assessment;
    }
}