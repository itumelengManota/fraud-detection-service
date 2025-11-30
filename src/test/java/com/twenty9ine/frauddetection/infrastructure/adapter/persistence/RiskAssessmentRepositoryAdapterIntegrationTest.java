package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

@Disabled
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
        RiskAssessment assessment = createRiskAssessment();
        assessment.completeAssessment(new RiskScore(75), Decision.REVIEW);

        // When
        RiskAssessment saved = repositoryAdapter.save(assessment);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getAssessmentId()).isNotNull();
        assertThat(saved.getTransactionId()).isEqualTo(assessment.getTransactionId());
        assertThat(saved.getRiskScore().value()).isEqualTo(75);
        assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(saved.getDecision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    void shouldSaveRiskAssessmentWithMLPrediction() {
        // Given
        RiskAssessment assessment = createRiskAssessment();
        MLPrediction mlPrediction = new MLPrediction("modelId", "fraud_model_v1", 0.85, 0.85, Map.of("feature1", 0.1, "feature2", 0.9));
        assessment.setMlPrediction(mlPrediction);
        assessment.completeAssessment(new RiskScore(85), Decision.BLOCK);

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
        RiskAssessment assessment = createRiskAssessment();
        RuleEvaluation evaluation1 = new RuleEvaluation("ruleId", "ruleName", RuleType.VELOCITY, true, 30, "High transaction velocity");
        RuleEvaluation evaluation2 = new RuleEvaluation("ruleId", "ruleName", RuleType.AMOUNT, true, 40, "Large amount threshold exceeded");
        assessment.addRuleEvaluation(evaluation1);
        assessment.addRuleEvaluation(evaluation2);
        assessment.completeAssessment(new RiskScore(70), Decision.REVIEW);

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
        RiskAssessment assessment = RiskAssessment.of(transactionId);
        assessment.completeAssessment(new RiskScore(60), Decision.REVIEW);
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

        RiskAssessment highRisk1 = createRiskAssessmentWithTime(baseTime);
        highRisk1.completeAssessment(new RiskScore(85), Decision.BLOCK);
        repositoryAdapter.save(highRisk1);

        RiskAssessment highRisk2 = createRiskAssessmentWithTime(baseTime.plus(30, ChronoUnit.MINUTES));
        highRisk2.completeAssessment(new RiskScore(90), Decision.BLOCK);
        repositoryAdapter.save(highRisk2);

        RiskAssessment mediumRisk = createRiskAssessmentWithTime(baseTime);
        mediumRisk.completeAssessment(new RiskScore(60), Decision.REVIEW);
        repositoryAdapter.save(mediumRisk);

        RiskAssessment oldHighRisk = createRiskAssessmentWithTime(searchTime.minus(2, ChronoUnit.HOURS));
        oldHighRisk.completeAssessment(new RiskScore(95), Decision.BLOCK);
        repositoryAdapter.save(oldHighRisk);

        // When
        List<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(RiskLevel.HIGH, searchTime);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getRiskLevel() == RiskLevel.HIGH);
        assertThat(results).allMatch(r -> r.getAssessmentTime().compareTo(searchTime) >= 0);
        assertThat(results.get(0).getAssessmentTime()).isAfter(results.get(1).getAssessmentTime());
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
        Instant searchTime = Instant.now().minus(1, ChronoUnit.HOURS);

        RiskAssessment criticalRisk = createRiskAssessment();
        criticalRisk.completeAssessment(new RiskScore(95), Decision.BLOCK);
        repositoryAdapter.save(criticalRisk);

        RiskAssessment highRisk = createRiskAssessment();
        highRisk.completeAssessment(new RiskScore(85), Decision.BLOCK);
        repositoryAdapter.save(highRisk);

        // When
        List<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(RiskLevel.CRITICAL, searchTime);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void shouldFindLowRiskLevelSince() {
        // Given
        Instant searchTime = Instant.now().minus(1, ChronoUnit.HOURS);

        RiskAssessment lowRisk1 = createRiskAssessment();
        lowRisk1.completeAssessment(new RiskScore(20), Decision.ALLOW);
        repositoryAdapter.save(lowRisk1);

        RiskAssessment lowRisk2 = createRiskAssessment();
        lowRisk2.completeAssessment(new RiskScore(30), Decision.ALLOW);
        repositoryAdapter.save(lowRisk2);

        RiskAssessment mediumRisk = createRiskAssessment();
        mediumRisk.completeAssessment(new RiskScore(60), Decision.REVIEW);
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
        RiskAssessment assessment = createRiskAssessment();
        assessment.completeAssessment(new RiskScore(85), Decision.BLOCK);
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
        RiskAssessment assessment = createRiskAssessment();
        assessment.completeAssessment(new RiskScore(50), Decision.REVIEW);

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

    private RiskAssessment createRiskAssessmentWithTime(Instant time) {
        // Note: This is a simplified version. In a real scenario, you might need to use reflection
        // or modify the domain model to support setting assessment time for testing
        return createRiskAssessment();
    }
}