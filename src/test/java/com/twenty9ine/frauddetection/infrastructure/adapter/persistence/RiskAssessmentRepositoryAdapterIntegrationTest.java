package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisabledInAotMode
@ComponentScan(basePackages = "com.twenty9ine.frauddetection.infrastructure.adapter.persistence")
class RiskAssessmentRepositoryAdapterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RiskAssessmentRepositoryAdapter repositoryAdapter;

    @Autowired
    private RiskAssessmentJdbcRepository jdbcRepository;

    @BeforeEach
    void setUp() {
        jdbcRepository.deleteAll();
    }

    @Test
    void shouldSaveRiskAssessment() {
        // Given
        RiskAssessment assessment = createRiskAssessment(RiskScore.of(75));
        assessment.completeAssessment(Decision.REVIEW);

        // When
        RiskAssessment saved = repositoryAdapter.save(assessment);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getAssessmentId()).isNotNull();
        assertThat(saved.getTransactionId()).isEqualTo(assessment.getTransactionId());
        assertThat(saved.getRiskScore().value()).isEqualTo(75);
        assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(saved.getDecision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    void shouldSaveRiskAssessmentWithMLPrediction() {
        // Given
        MLPrediction mlPrediction = new MLPrediction("modelId", "fraud_model_v1", 0.85,
                0.85, Map.of("feature1", 0.1, "feature2", 0.9));
        RiskAssessment assessment = new RiskAssessment(TransactionId.generate(), RiskScore.of(90), List.of(), mlPrediction);
        assessment.completeAssessment(Decision.BLOCK);

        // When
        RiskAssessment saved = repositoryAdapter.save(assessment);

        // Then
        assertThat(saved.getMlPrediction()).isNotNull();
        assertThat(saved.getMlPrediction().confidence()).isEqualTo(0.85);
        assertThat(saved.getMlPrediction().modelVersion()).isEqualTo("fraud_model_v1");
    }

    @Test
    void shouldSaveRiskAssessmentWithRuleEvaluations() {
        // Given
        RuleEvaluation evaluation1 = new RuleEvaluation("ruleId", "ruleName", RuleType.VELOCITY, true, 30, "High transaction velocity");
        RuleEvaluation evaluation2 = new RuleEvaluation("ruleId", "ruleName", RuleType.AMOUNT, true, 40, "Large amount threshold exceeded");
        RiskAssessment assessment = new RiskAssessment(TransactionId.generate(), RiskScore.of(50), List.of(evaluation1, evaluation2), null);
        assessment.completeAssessment(Decision.REVIEW);

        // When
        RiskAssessment saved = repositoryAdapter.save(assessment);

        // Then
        assertThat(saved.getRuleEvaluations()).hasSize(2);
        assertThat(saved.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleType)
                .containsExactlyInAnyOrder(RuleType.VELOCITY, RuleType.AMOUNT);
    }

    @Test
    void shouldFindByTransactionId() {
        // Given
        TransactionId transactionId = TransactionId.generate();
        RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(60));
        assessment.completeAssessment(Decision.REVIEW);
        repositoryAdapter.save(assessment);

        // When
        Optional<RiskAssessment> found = repositoryAdapter.findByTransactionId(transactionId.toUUID());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTransactionId()).isEqualTo(transactionId);
        assertThat(found.get().getRiskScore().value()).isEqualTo(60);
        assertThat(found.get().getDecision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    void shouldReturnEmptyWhenTransactionIdNotFound() {
        // When
        Optional<RiskAssessment> found = repositoryAdapter.findByTransactionId(UUID.randomUUID());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByRiskLevelSince() {
        // Given
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant searchTime = baseTime.minus(1, ChronoUnit.HOURS);

        RiskAssessment highRisk1 = createRiskAssessmentWithTime(baseTime, RiskScore.of(90));
        highRisk1.completeAssessment(Decision.BLOCK);
        repositoryAdapter.save(highRisk1);

        RiskAssessment highRisk2 = createRiskAssessmentWithTime(baseTime.plus(30, ChronoUnit.MINUTES), RiskScore.of(90));
        highRisk2.completeAssessment(Decision.BLOCK);
        repositoryAdapter.save(highRisk2);

        RiskAssessment mediumRisk = createRiskAssessmentWithTime(baseTime, RiskScore.of(40));
        mediumRisk.completeAssessment(Decision.REVIEW);
        repositoryAdapter.save(mediumRisk);

        RiskAssessment oldHighRisk = createRiskAssessmentWithTime(searchTime.minus(2, ChronoUnit.HOURS), RiskScore.of(80));
        oldHighRisk.completeAssessment(Decision.BLOCK);
        repositoryAdapter.save(oldHighRisk);

        // When
        List<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(RiskLevel.HIGH, searchTime);

        // Then
        assertThat(results).hasSize(2)
                .allMatch(r -> r.getRiskLevel() == RiskLevel.HIGH)
                .allMatch(r -> r.getAssessmentTime().compareTo(searchTime) >= 0);
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchingRiskLevelSince() {
        // Given
        Instant futureTime = Instant.now().plus(10, ChronoUnit.DAYS);

        // When
        List<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(RiskLevel.HIGH, futureTime);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void shouldFindCriticalRiskLevelSince() {
        // Given
        Instant now = Instant.now();
        Instant searchTime = now.minus(1, ChronoUnit.HOURS);

        RiskAssessment criticalRisk = createRiskAssessmentWithTime(now, RiskScore.of(91));
        criticalRisk.completeAssessment(Decision.BLOCK);
        repositoryAdapter.save(criticalRisk);

        RiskAssessment highRisk = createRiskAssessmentWithTime(now, RiskScore.of(90));
        highRisk.completeAssessment(Decision.BLOCK);
        repositoryAdapter.save(highRisk);

        // When
        List<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(RiskLevel.CRITICAL, searchTime);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void shouldFindLowRiskLevelSince() {
        // Given
        Instant searchTime = Instant.now().minus(1, ChronoUnit.HOURS);

        RiskAssessment lowRisk1 = createRiskAssessment(RiskScore.of(10));
        lowRisk1.completeAssessment(Decision.ALLOW);
        repositoryAdapter.save(lowRisk1);

        RiskAssessment lowRisk2 = createRiskAssessment(RiskScore.of(10));
        lowRisk2.completeAssessment(Decision.ALLOW);
        repositoryAdapter.save(lowRisk2);

        RiskAssessment mediumRisk = createRiskAssessment(RiskScore.of(50));
        mediumRisk.completeAssessment(Decision.REVIEW);
        repositoryAdapter.save(mediumRisk);

        // When
        List<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(RiskLevel.LOW, searchTime);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getRiskLevel() == RiskLevel.LOW);
    }

    @Test
    void shouldPreserveDomainEventsWhenSaving() {
        // Given
        RiskAssessment assessment = createRiskAssessment(RiskScore.of(90));
        assessment.completeAssessment(Decision.BLOCK);
        int eventCountBeforeSave = assessment.getDomainEvents().size();

        // When
        RiskAssessment saved = repositoryAdapter.save(assessment);

        // Then
        assertThat(saved.getDomainEvents()).isEmpty(); // Events are cleared after persistence
        assertThat(eventCountBeforeSave).isGreaterThan(0);
    }

    @Test
    void shouldHandleMultipleSavesCorrectly() {
        // Given
        RiskAssessment assessment = createRiskAssessment(RiskScore.of(10));
        assessment.completeAssessment(Decision.REVIEW);

        // When
        RiskAssessment saved1 = repositoryAdapter.save(assessment);
        RiskAssessment saved2 = repositoryAdapter.save(saved1);

        // Then
        Optional<RiskAssessment> found = repositoryAdapter.findByTransactionId(assessment.getTransactionId().toUUID());
        assertThat(found).isPresent();
        assertThat(found.get().getAssessmentId()).isEqualTo(saved2.getAssessmentId());
    }

    private RiskAssessment createRiskAssessment() {
        return RiskAssessment.of(TransactionId.generate());
    }

    private RiskAssessment createRiskAssessment(RiskScore score) {
        return new RiskAssessment(TransactionId.generate(), score);
    }

    private RiskAssessment createRiskAssessmentWithTime(Instant time, RiskScore riskScore) {
        try {
            RiskAssessment assessment = new RiskAssessment(TransactionId.generate(), riskScore);
            java.lang.reflect.Field assessmentTimeField = RiskAssessment.class.getDeclaredField("assessmentTime");
            assessmentTimeField.setAccessible(true);
            assessmentTimeField.set(assessment, time);

            return assessment;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set assessment time via reflection", e);
        }
    }
}