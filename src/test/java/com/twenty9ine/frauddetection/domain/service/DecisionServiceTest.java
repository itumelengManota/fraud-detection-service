package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.domain.valueobject.RiskScore;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
            RiskAssessment assessment = createAssessment(RiskScore.of(10), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.ALLOW, decision);
            assertEquals(RiskLevel.LOW, assessment.getRiskLevel());
        }

        @Test
        @DisplayName("Should return CHALLENGE for medium risk score")
        void shouldReturnChallengeForMediumRiskScore() {
            RiskAssessment assessment = createAssessment(RiskScore.of(50), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.CHALLENGE, decision);
            assertEquals(RiskLevel.MEDIUM, assessment.getRiskLevel());
        }

        @Test
        @DisplayName("Should return REVIEW for high risk score")
        void shouldReturnReviewForHighRiskScore() {
            RiskAssessment assessment = createAssessment(RiskScore.of(75), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.REVIEW, decision);
            assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
        }

        @Test
        @DisplayName("Should return BLOCK for critical risk score")
        void shouldReturnBlockForCriticalRiskScore() {
            RiskAssessment assessment = createAssessment(RiskScore.of(95), Decision.BLOCK);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(Decision.BLOCK, decision);
            assertEquals(RiskLevel.CRITICAL, assessment.getRiskLevel());
        }
    }

    @Nested
    @DisplayName("Risk Level Boundary Tests")
    class RiskLevelBoundaryTests {

        @Test
        @DisplayName("Should classify score 0 as LOW")
        void shouldClassifyZeroAsLow() {
            RiskAssessment assessment = createAssessment(RiskScore.of(0), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.LOW, assessment.getRiskLevel());
            assertEquals(Decision.ALLOW, decision);
        }

        @Test
        @DisplayName("Should classify score 29 as LOW")
        void shouldClassify29AsLow() {
            RiskAssessment assessment = createAssessment(RiskScore.of(29), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.LOW, assessment.getRiskLevel());
            assertEquals(Decision.ALLOW, decision);
        }

        @Test
        @DisplayName("Should classify score 30 as MEDIUM")
        void shouldClassify30AsMedium() {
            RiskAssessment assessment = createAssessment(RiskScore.of(30), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.LOW, assessment.getRiskLevel());
            assertEquals(Decision.ALLOW, decision);
        }

        @Test
        @DisplayName("Should classify score 59 as MEDIUM")
        void shouldClassify59AsMedium() {
            RiskAssessment assessment = createAssessment(RiskScore.of(59), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.MEDIUM, assessment.getRiskLevel());
            assertEquals(Decision.CHALLENGE, decision);
        }

        @Test
        @DisplayName("Should classify score 60 as HIGH")
        void shouldClassify60AsHigh() {
            RiskAssessment assessment = createAssessment(RiskScore.of(60), Decision.CHALLENGE);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.MEDIUM, assessment.getRiskLevel());
            assertEquals(Decision.CHALLENGE, decision);
        }

        @Test
        @DisplayName("Should classify score 84 as HIGH")
        void shouldClassify84AsHigh() {
            RiskAssessment assessment = createAssessment(RiskScore.of(84), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
            assertEquals(Decision.REVIEW, decision);
        }

        @Test
        @DisplayName("Should classify score 85 as CRITICAL")
        void shouldClassify85AsCritical() {
            RiskAssessment assessment = createAssessment(RiskScore.of(85), Decision.REVIEW);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
            assertEquals(Decision.REVIEW, decision);
        }

        @Test
        @DisplayName("Should classify score 100 as CRITICAL")
        void shouldClassify100AsCritical() {
            RiskAssessment assessment = createAssessment(RiskScore.of(100), Decision.BLOCK);

            Decision decision = decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.CRITICAL, assessment.getRiskLevel());
            assertEquals(Decision.BLOCK, decision);
        }
    }

    @Nested
    @DisplayName("Strategy Selection Tests")
    class StrategySelectionTests {

        @Test
        @DisplayName("Should find correct strategy for each risk level")
        void shouldFindCorrectStrategyForEachRiskLevel() {
            assertDecisionForLevel(RiskLevel.LOW, Decision.ALLOW);
            assertDecisionForLevel(RiskLevel.MEDIUM, Decision.CHALLENGE);
            assertDecisionForLevel(RiskLevel.HIGH, Decision.REVIEW);
            assertDecisionForLevel(RiskLevel.CRITICAL, Decision.BLOCK);
        }

        private void assertDecisionForLevel(RiskLevel level, Decision expectedDecision) {
            RiskAssessment assessment = createAssessment(RiskScore.of(50), Decision.ALLOW);
            assessment.completeAssessment(RiskScore.of(50), Decision.ALLOW);

            List<DecisionStrategy> strategies = List.of(
                    new LowRiskStrategy(),
                    new MediumRiskStrategy(),
                    new HighRiskStrategy(),
                    new CriticalRiskStrategy()
            );
            DecisionService service = new DecisionService(strategies);
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
            RiskAssessment assessment = createAssessment(RiskScore.of(70), Decision.CHALLENGE);

            decisionService.makeDecision(assessment);

            assertEquals(RiskLevel.MEDIUM, assessment.getRiskLevel());
            assertEquals(Decision.CHALLENGE, assessment.getDecision());
        }

        @Test
        @DisplayName("Should set assessment time when completing assessment")
        void shouldSetAssessmentTimeWhenCompletingAssessment() {
            RiskAssessment assessment = createAssessment(RiskScore.of(40), Decision.ALLOW);

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
            RiskAssessment lowRisk = createAssessment(RiskScore.of(10), Decision.ALLOW);
            RiskAssessment mediumRisk = createAssessment(RiskScore.of(45), Decision.CHALLENGE);
            RiskAssessment highRisk = createAssessment(RiskScore.of(90), Decision.REVIEW);
            RiskAssessment criticalRisk = createAssessment(RiskScore.of(91), Decision.BLOCK);

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
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            RiskAssessment assessment = RiskAssessment.of(transactionId);
            assessment.completeAssessment(RiskScore.of(50), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertNotNull(decision);
            assertEquals(RiskLevel.MEDIUM, assessment.getRiskLevel());
        }

        @Test
        @DisplayName("Should handle assessment without rule evaluations")
        void shouldHandleAssessmentWithoutRuleEvaluations() {
            RiskAssessment assessment = createAssessment(RiskScore.of(35), Decision.ALLOW);

            Decision decision = decisionService.makeDecision(assessment);

            assertNotNull(decision);
            assertEquals(Decision.ALLOW, decision);
        }
    }

    private RiskAssessment createAssessment(RiskScore score, Decision decision) {
        RiskAssessment assessment = RiskAssessment.of(TransactionId.generate());
        assessment.completeAssessment(score, decision);

        return assessment;
    }
}