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

        @Test
        @DisplayName("Should return ALLOW for low risk score")
        void shouldReturnAllowForLowRiskScore() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(10));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.ALLOW, decision);
            assertEquals(TransactionRiskLevel.LOW, assessment.getTransactionRiskLevel());
        }

        @Test
        @DisplayName("Should return CHALLENGE for medium risk score")
        void shouldReturnChallengeForMediumRiskScore() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(50));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.CHALLENGE, decision);
            assertEquals(TransactionRiskLevel.MEDIUM, assessment.getTransactionRiskLevel());
        }

        @Test
        @DisplayName("Should return REVIEW for high risk score")
        void shouldReturnReviewForHighRiskScore() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(75));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.REVIEW, decision);
            assertEquals(TransactionRiskLevel.HIGH, assessment.getTransactionRiskLevel());
        }

        @Test
        @DisplayName("Should return BLOCK for critical risk score")
        void shouldReturnBlockForCriticalRiskScore() {
            RiskAssessment assessment = createAssessment(Decision.BLOCK, RiskScore.of(95));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.BLOCK, decision);
            assertEquals(TransactionRiskLevel.CRITICAL, assessment.getTransactionRiskLevel());
        }
    }

    @Nested
    @DisplayName("Risk Level Boundary Tests")
    class TransactionRiskLevelBoundaryTests {

        @Test
        @DisplayName("Should classify score 0 as LOW")
        void shouldClassifyZeroAsLow() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(0));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.LOW, assessment.getTransactionRiskLevel());
            assertEquals(Decision.ALLOW, decision);
        }

        @Test
        @DisplayName("Should classify score 29 as LOW")
        void shouldClassify29AsLow() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(29));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.LOW, assessment.getTransactionRiskLevel());
            assertEquals(Decision.ALLOW, decision);
        }

        @Test
        @DisplayName("Should classify score 30 as MEDIUM")
        void shouldClassify30AsMedium() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(30));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.LOW, assessment.getTransactionRiskLevel());
            assertEquals(Decision.ALLOW, decision);
        }

        @Test
        @DisplayName("Should classify score 59 as MEDIUM")
        void shouldClassify59AsMedium() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(59));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.MEDIUM, assessment.getTransactionRiskLevel());
            assertEquals(Decision.CHALLENGE, decision);
        }

        @Test
        @DisplayName("Should classify score 60 as HIGH")
        void shouldClassify60AsHigh() {
            RiskAssessment assessment = createAssessment(Decision.CHALLENGE, RiskScore.of(60));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.MEDIUM, assessment.getTransactionRiskLevel());
            assertEquals(Decision.CHALLENGE, decision);
        }

        @Test
        @DisplayName("Should classify score 84 as HIGH")
        void shouldClassify84AsHigh() {
            RiskAssessment assessment = createAssessment(Decision.ALLOW, RiskScore.of(84));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.HIGH, assessment.getTransactionRiskLevel());
            assertEquals(Decision.REVIEW, decision);
        }

        @Test
        @DisplayName("Should classify score 85 as CRITICAL")
        void shouldClassify85AsCritical() {
            RiskAssessment assessment = createAssessment(Decision.REVIEW, RiskScore.of(85));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.HIGH, assessment.getTransactionRiskLevel());
            assertEquals(Decision.REVIEW, decision);
        }

        @Test
        @DisplayName("Should classify score 100 as CRITICAL")
        void shouldClassify100AsCritical() {
            RiskAssessment assessment = createAssessment(Decision.BLOCK, RiskScore.of(100));
            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(TransactionRiskLevel.CRITICAL, assessment.getTransactionRiskLevel());
            assertEquals(Decision.BLOCK, decision);
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
//            DecisionService service = new DecisionService(strategies);
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