package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisabledInAotMode
@ComponentScan(basePackages = "com.twenty9ine.frauddetection.infrastructure.adapter.persistence")
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("postgres")
class RiskAssessmentRepositoryAdapterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

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
        RiskAssessment assessment = createRiskAssessment(RiskScore.of(75));
        assessment.completeAssessment(Decision.REVIEW);

        RiskAssessment saved = repositoryAdapter.save(assessment);

        assertThat(saved).isNotNull();
        assertThat(saved.getAssessmentId()).isNotNull();
        assertThat(saved.getTransactionId()).isEqualTo(assessment.getTransactionId());
        assertThat(saved.getRiskScore().value()).isEqualTo(75);
        assertThat(saved.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.HIGH);
        assertThat(saved.getDecision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    void shouldSaveRiskAssessmentWithMLPrediction() {
        MLPrediction mlPrediction = new MLPrediction("modelId", "fraud_model_v1", 0.85,
                0.85, Map.of("feature1", 0.1, "feature2", 0.9));
        RiskAssessment assessment = new RiskAssessment(TransactionId.generate(), RiskScore.of(90), List.of(), mlPrediction);
        assessment.completeAssessment(Decision.BLOCK);

        RiskAssessment saved = repositoryAdapter.save(assessment);

        assertThat(saved.getMlPrediction()).isNotNull();
        assertThat(saved.getMlPrediction().confidence()).isEqualTo(0.85);
        assertThat(saved.getMlPrediction().modelVersion()).isEqualTo("fraud_model_v1");
    }

    @Test
    void shouldSaveRiskAssessmentWithRuleEvaluations() {
        RuleEvaluation evaluation1 = new RuleEvaluation("ruleId", "ruleName", RuleType.VELOCITY, true, 30, "High transaction velocity");
        RuleEvaluation evaluation2 = new RuleEvaluation("ruleId", "ruleName", RuleType.AMOUNT, true, 40, "Large amount threshold exceeded");
        RiskAssessment assessment = new RiskAssessment(TransactionId.generate(), RiskScore.of(50), List.of(evaluation1, evaluation2), null);
        assessment.completeAssessment(Decision.REVIEW);

        RiskAssessment saved = repositoryAdapter.save(assessment);

        assertThat(saved.getRuleEvaluations()).hasSize(2);
        assertThat(saved.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleType)
                .containsExactlyInAnyOrder(RuleType.VELOCITY, RuleType.AMOUNT);
    }

    @Test
    void shouldFindByTransactionId() {
        TransactionId transactionId = TransactionId.generate();
        RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(60));
        assessment.completeAssessment(Decision.REVIEW);
        repositoryAdapter.save(assessment);

        Optional<RiskAssessment> found = repositoryAdapter.findByTransactionId(transactionId.toUUID());

        assertThat(found).isPresent();
        assertThat(found.get().getTransactionId()).isEqualTo(transactionId);
        assertThat(found.get().getRiskScore().value()).isEqualTo(60);
        assertThat(found.get().getDecision()).isEqualTo(Decision.REVIEW);
    }

    @Test
    void shouldReturnEmptyWhenTransactionIdNotFound() {
        Optional<RiskAssessment> found = repositoryAdapter.findByTransactionId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByRiskLevelSinceWithPagination() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant searchTime = oneHourEarlier(now);

        createAndSaveAssessment(now, RiskScore.of(90), Decision.BLOCK);
        createAndSaveAssessment(thirtyMinutesLater(now), RiskScore.of(85), Decision.BLOCK);
        createAndSaveAssessment(fourtyFiveMinutesLater(now), RiskScore.of(95), Decision.BLOCK);
        createAndSaveAssessment(now, RiskScore.of(40), Decision.REVIEW);
        createAndSaveAssessment(threeHoursEarlier(now), RiskScore.of(88), Decision.BLOCK);

        PageRequest pageRequest = PageRequest.of(0, 10, SortDirection.DESC);

        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(Set.of(TransactionRiskLevel.HIGH, TransactionRiskLevel.CRITICAL), searchTime, pageRequest);

        assertThat(results.totalElements()).isEqualTo(3);
        assertThat(results.content())
                .hasSize(3)
                .allMatch(r -> r.getTransactionRiskLevel() == TransactionRiskLevel.HIGH || r.getTransactionRiskLevel() == TransactionRiskLevel.CRITICAL)
                .allMatch(r -> r.getAssessmentTime().isAfter(searchTime));
    }

    @Test
    void shouldHandlePaginationCorrectly() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        for (int i = 0; i < 5; i++) {
            createAndSaveAssessment(now.plus(i, ChronoUnit.MINUTES), RiskScore.of(85), Decision.BLOCK);
        }

        PageRequest firstPage = PageRequest.of(0, 2, SortDirection.DESC);
        PageRequest secondPage = PageRequest.of(1, 2, SortDirection.DESC);

        PagedResult<RiskAssessment> page1 = repositoryAdapter.findByRiskLevelSince(
                Set.of(TransactionRiskLevel.HIGH),
                now.minus(10, ChronoUnit.MINUTES),
                firstPage
        );

        PagedResult<RiskAssessment> page2 = repositoryAdapter.findByRiskLevelSince(
                Set.of(TransactionRiskLevel.HIGH),
                now.minus(10, ChronoUnit.MINUTES),
                secondPage
        );

        assertThat(page1.content()).hasSize(2);
        assertThat(page1.totalElements()).isEqualTo(5);
        assertThat(page1.totalPages()).isEqualTo(3);
        assertThat(page2.content()).hasSize(2);
        assertThat(page1.content().getFirst().getAssessmentId())
                .isNotEqualTo(page2.content().getFirst().getAssessmentId());
    }

    @Test
    void shouldSortResultsByAssessmentTimeDescending() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant searchTime = threeHoursEarlier(now);

        createAndSaveAssessment(now, RiskScore.of(85), Decision.BLOCK);
        createAndSaveAssessment(oneHourLater(now), RiskScore.of(87), Decision.BLOCK);
        createAndSaveAssessment(twoHoursLater(now), RiskScore.of(89), Decision.BLOCK);

        PageRequest pageRequest = PageRequest.of(0, 10, SortDirection.DESC);

        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(Set.of(TransactionRiskLevel.HIGH), searchTime, pageRequest);

        assertThat(results.content()).hasSize(3);
        assertThat(results.content().get(0).getAssessmentTime())
                .isAfter(results.content().get(1).getAssessmentTime());
        assertThat(results.content().get(1).getAssessmentTime())
                .isAfter(results.content().get(2).getAssessmentTime());
    }

    @Test
    void shouldSortResultsByAssessmentTimeAscending() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant searchTime = threeHoursEarlier(now);

        createAndSaveAssessment(now, RiskScore.of(85), Decision.BLOCK);
        createAndSaveAssessment(oneHourLater(now), RiskScore.of(87), Decision.BLOCK);
        createAndSaveAssessment(twoHoursLater(now), RiskScore.of(89), Decision.BLOCK);

        PageRequest pageRequest = PageRequest.of(0, 10, SortDirection.ASC);
        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(Set.of(TransactionRiskLevel.HIGH), searchTime, pageRequest);

        assertThat(results.content()).hasSize(3);
        assertThat(results.content().get(0).getAssessmentTime())
                .isBefore(results.content().get(1).getAssessmentTime());
        assertThat(results.content().get(1).getAssessmentTime())
                .isBefore(results.content().get(2).getAssessmentTime());
    }

    @Test
    void shouldReturnEmptyPageWhenNoMatchingRiskLevelAndTime() {
        Instant futureTime = Instant.now().plus(10, ChronoUnit.DAYS);
        PageRequest pageRequest = PageRequest.of(0, 10);

        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(
                Set.of(TransactionRiskLevel.HIGH),
                futureTime,
                pageRequest
        );

        assertThat(results.content()).isEmpty();
        assertThat(results.totalElements()).isZero();
    }

    @Test
    void shouldFindMultipleRiskLevels() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant searchTime = oneHourEarlier(now);

        createAndSaveAssessment(now, RiskScore.of(85), Decision.BLOCK);
        createAndSaveAssessment(now, RiskScore.of(50), Decision.REVIEW);
        createAndSaveAssessment(now, RiskScore.of(95), Decision.BLOCK);
        createAndSaveAssessment(now, RiskScore.of(10), Decision.ALLOW);

        PageRequest pageRequest = PageRequest.of(0, 10);

        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(
                Set.of(TransactionRiskLevel.HIGH, TransactionRiskLevel.MEDIUM, TransactionRiskLevel.CRITICAL), searchTime, pageRequest);

        assertThat(results.content()).hasSize(3);
        assertThat(results.content()).extracting(RiskAssessment::getTransactionRiskLevel)
                .containsExactlyInAnyOrder(TransactionRiskLevel.HIGH, TransactionRiskLevel.MEDIUM, TransactionRiskLevel.CRITICAL);
    }

    @Test
    void shouldFindAllRiskLevelsWhenEmptySetProvided() {
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        createAndSaveAssessment(baseTime, RiskScore.of(85), Decision.BLOCK);
        createAndSaveAssessment(baseTime, RiskScore.of(50), Decision.REVIEW);
        createAndSaveAssessment(baseTime, RiskScore.of(95), Decision.BLOCK);
        createAndSaveAssessment(baseTime, RiskScore.of(10), Decision.ALLOW);

        PageRequest pageRequest = PageRequest.of(0, 10);

        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(Set.of(), oneHourEarlier(baseTime), pageRequest);

        assertThat(results.content()).hasSize(4);
        assertThat(results.content()).extracting(RiskAssessment::getTransactionRiskLevel)
                .containsExactlyInAnyOrder(TransactionRiskLevel.HIGH, TransactionRiskLevel.MEDIUM, TransactionRiskLevel.CRITICAL, TransactionRiskLevel.LOW);
    }

    @Test
    void shouldFindAllRiskLevelsWhenNullSetProvided() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        createAndSaveAssessment(now, RiskScore.of(85), Decision.BLOCK);
        createAndSaveAssessment(now, RiskScore.of(50), Decision.REVIEW);

        PageRequest pageRequest = PageRequest.of(0, 10);
        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(null, oneHourEarlier(now), pageRequest);

        assertThat(results.content()).hasSize(2);
    }

    @Test
    void shouldHandleUnpagedRequest() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        for (int i = 0; i < 3; i++) {
            createAndSaveAssessment(now.plus(i, ChronoUnit.MINUTES), RiskScore.of(85), Decision.BLOCK);
        }

        PagedResult<RiskAssessment> results = repositoryAdapter.findByRiskLevelSince(Set.of(TransactionRiskLevel.HIGH),
                now.minus(10, ChronoUnit.MINUTES), null);

        assertThat(results.content()).hasSize(3);
    }

    @Test
    void shouldPreserveDomainEventsWhenSaving() {
        RiskAssessment assessment = createRiskAssessment(RiskScore.of(90));
        assessment.completeAssessment(Decision.BLOCK);
        int eventCountBeforeSave = assessment.getDomainEvents().size();

        RiskAssessment saved = repositoryAdapter.save(assessment);

        assertThat(saved.getDomainEvents()).isEmpty();
        assertThat(eventCountBeforeSave).isGreaterThan(0);
    }

    @Test
    void shouldHandleMultipleSavesCorrectly() {
        RiskAssessment assessment = createRiskAssessment(RiskScore.of(10));
        assessment.completeAssessment(Decision.REVIEW);

        RiskAssessment saved1 = repositoryAdapter.save(assessment);
        RiskAssessment saved2 = repositoryAdapter.save(saved1);

        Optional<RiskAssessment> found = repositoryAdapter.findByTransactionId(assessment.getTransactionId().toUUID());
        assertThat(found).isPresent();
        assertThat(found.get().getAssessmentId()).isEqualTo(saved2.getAssessmentId());
    }

    private RiskAssessment createRiskAssessment(RiskScore score) {
        return new RiskAssessment(TransactionId.generate(), score);
    }

    private void createAndSaveAssessment(Instant time, RiskScore score, Decision decision) {
        RiskAssessment assessment = createRiskAssessmentWithTime(time, score);
        assessment.completeAssessment(decision);
        repositoryAdapter.save(assessment);
    }

    private RiskAssessment createRiskAssessmentWithTime(Instant time, RiskScore riskScore) {
        return new RiskAssessment(TransactionId.generate(), riskScore, time);
    }

    private static Instant thirtyMinutesLater(Instant now) {
        return now.plus(30, ChronoUnit.MINUTES);
    }

    private static Instant fourtyFiveMinutesLater(Instant now) {
        return now.plus(45, ChronoUnit.MINUTES);
    }

    private static Instant oneHourLater(Instant now) {
        return now.plus(1, ChronoUnit.HOURS);
    }

    private static Instant twoHoursLater(Instant baseTime) {
        return baseTime.plus(2, ChronoUnit.HOURS);
    }

    private static Instant oneHourEarlier(Instant now) {
        return now.minus(1, ChronoUnit.HOURS);
    }

    private static Instant threeHoursEarlier(Instant now) {
        return now.minus(3, ChronoUnit.HOURS);
    }
}