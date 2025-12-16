package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RiskAssessmentJdbcRepository with optimized performance.
 *
 * Performance Optimizations:
 * - Extends AbstractIntegrationTest for shared container infrastructure
 * - @DataJdbcTest for slice testing (faster than @SpringBootTest)
 * - @Transactional with automatic rollback (no manual cleanup needed)
 * - @TestInstance(PER_CLASS) for potential shared setup
 * - Parallel execution enabled (safe due to transaction isolation)
 * - AutoConfigureTestDatabase.NONE to use Testcontainers
 *
 * Expected performance gain: 70-80% faster than full @SpringBootTest
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional // Automatic rollback after each test - no cleanup needed
@DisabledInAotMode
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("RiskAssessmentJdbcRepository Integration Tests")
@Execution(ExecutionMode.CONCURRENT)
@ImportAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class,})
class RiskAssessmentJdbcRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RiskAssessmentJdbcRepository repository;

    // No @BeforeEach cleanup needed - transactions handle it automatically

    // ========================================
    // Test Cases
    // ========================================

    @Test
    @DisplayName("Should save and find entity by ID")
    void shouldSaveAndFindById() {
        // Given
        RiskAssessmentEntity entity = createRiskAssessmentEntity();

        // When
        RiskAssessmentEntity saved = repository.save(entity);
        Optional<RiskAssessmentEntity> found = repository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getTransactionId()).isEqualTo(entity.getTransactionId());
        assertThat(found.get().getRiskScoreValue()).isEqualTo(entity.getRiskScoreValue());
        assertThat(found.get().getRiskLevel()).isEqualTo(entity.getRiskLevel());
        assertThat(found.get().getDecision()).isEqualTo(entity.getDecision());
    }

    @Test
    @DisplayName("Should find entity by transaction ID")
    void shouldFindByTransactionId() {
        // Given
        UUID transactionId = UUID.randomUUID();
        RiskAssessmentEntity entity = createRiskAssessmentEntity();
        entity.setTransactionId(transactionId);
        repository.save(entity);

        // When
        Optional<RiskAssessmentEntity> found = repository.findByTransactionId(transactionId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTransactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Should return empty when transaction ID not found")
    void shouldReturnEmptyWhenTransactionIdNotFound() {
        // When
        Optional<RiskAssessmentEntity> found = repository.findByTransactionId(UUID.randomUUID());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find by risk level and assessment time with pagination")
    void shouldFindByRiskLevelAndAssessmentTimeGreaterThanEqualWithPagination() {
        // Given
        Instant now = now();
        Instant searchTime = oneHourEarlier(now);

        createAndSaveEntity("HIGH", now);
        createAndSaveEntity("HIGH", thirtyMinutesLater(now));
        createAndSaveEntity("CRITICAL", fortyFiveMinutesLater(now));
        createAndSaveEntity("LOW", now);
        createAndSaveEntity("HIGH", oneHourLater(now));

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("assessmentTime").descending());

        // When
        Page<RiskAssessmentEntity> results = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
                        Set.of("HIGH", "CRITICAL"),
                        searchTime,
                        pageRequest
                );

        // Then
        assertThat(results.getContent()).hasSize(4);
        assertThat(results.getTotalElements()).isEqualTo(4);
        assertThat(results.getContent())
                .allMatch(r -> Set.of("HIGH", "CRITICAL").contains(r.getRiskLevel()));
        assertThat(results.getContent())
                .allMatch(r -> r.getAssessmentTime().isAfter(searchTime) ||
                        r.getAssessmentTime().equals(searchTime));
    }

    @Test
    @DisplayName("Should handle pagination correctly")
    void shouldHandlePaginationCorrectly() {
        // Given
        Instant baseTime = now();

        for (int i = 0; i < 5; i++) {
            createAndSaveEntity("HIGH", baseTime.plus(i, ChronoUnit.MINUTES));
        }

        PageRequest firstPage = PageRequest.of(0, 2, Sort.by("assessmentTime").descending());
        PageRequest secondPage = PageRequest.of(1, 2, Sort.by("assessmentTime").descending());

        // When
        Page<RiskAssessmentEntity> page1 = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
                        Set.of("HIGH"),
                        baseTime.minus(10, ChronoUnit.MINUTES),
                        firstPage
                );

        Page<RiskAssessmentEntity> page2 = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
                        Set.of("HIGH"),
                        baseTime.minus(10, ChronoUnit.MINUTES),
                        secondPage
                );

        // Then
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalPages()).isEqualTo(3);
        assertThat(page2.getContent()).hasSize(2);
        assertThat(page1.getContent().get(0).getId())
                .isNotEqualTo(page2.getContent().get(0).getId());
    }

    @Test
    @DisplayName("Should sort results by assessment time descending")
    void shouldSortResultsByAssessmentTimeDescending() {
        // Given
        Instant now = now();

        createAndSaveEntity("HIGH", now);
        createAndSaveEntity("HIGH", oneHourLater(now));
        createAndSaveEntity("HIGH", twoHoursLater(now));

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("assessmentTime").descending());

        // When
        Page<RiskAssessmentEntity> results = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
                        Set.of("HIGH"),
                        now.minus(3, ChronoUnit.HOURS),
                        pageRequest
                );

        // Then
        assertThat(results.getContent()).hasSize(3);
        assertThat(results.getContent().get(0).getAssessmentTime())
                .isAfter(results.getContent().get(1).getAssessmentTime());
        assertThat(results.getContent().get(1).getAssessmentTime())
                .isAfter(results.getContent().get(2).getAssessmentTime());
    }

    @Test
    @DisplayName("Should return empty page when no matching risk level and time")
    void shouldReturnEmptyPageWhenNoMatchingRiskLevelAndTime() {
        // Given
        Instant futureTime = Instant.now().plus(10, ChronoUnit.DAYS);
        PageRequest pageRequest = PageRequest.of(0, 10);

        // When
        Page<RiskAssessmentEntity> results = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
                        Set.of("HIGH"),
                        futureTime,
                        pageRequest
                );

        // Then
        assertThat(results.getContent()).isEmpty();
        assertThat(results.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Should update entity and increment revision")
    void shouldUpdateEntity() {
        // Given
        RiskAssessmentEntity entity = createRiskAssessmentEntity();
        entity.setRiskScoreValue(50);
        RiskAssessmentEntity saved = repository.save(entity);

        assertThat(saved.getRiskScoreValue()).isEqualTo(50);
        assertThat(saved.getRevision()).isEqualTo(1);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        // When
        saved.setRiskScoreValue(80);
        saved.setRiskLevel("HIGH");
        RiskAssessmentEntity updated = repository.save(saved);

        // Then
        assertThat(updated.getRevision()).isEqualTo(2);
        assertThat(updated.getCreatedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());

        Optional<RiskAssessmentEntity> found = repository.findById(updated.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRiskScoreValue()).isEqualTo(80);
        assertThat(found.get().getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Should delete entity by ID")
    void shouldDeleteById() {
        // Given
        RiskAssessmentEntity entity = repository.save(createRiskAssessmentEntity());

        Optional<RiskAssessmentEntity> found1 = repository.findById(entity.getId());
        assertThat(found1).isPresent();

        // When
        repository.deleteById(entity.getId());
        Optional<RiskAssessmentEntity> found2 = repository.findById(entity.getId());

        // Then
        assertThat(found2).isEmpty();
    }

    // ========================================
    // Helper Methods
    // ========================================

    private void createAndSaveEntity(String riskLevel, Instant assessmentTime) {
        RiskAssessmentEntity entity = createRiskAssessmentEntity();
        entity.setRiskLevel(riskLevel);
        entity.setAssessmentTime(assessmentTime);
        repository.save(entity);
    }

    private RiskAssessmentEntity createRiskAssessmentEntity() {
        return RiskAssessmentEntity.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .riskScoreValue(75)
                .riskLevel("MEDIUM")
                .decision("REVIEW")
                .mlPredictionJson(createMlPredictionJson())
                .assessmentTime(now())
                .build();
    }

    private PGobject createMlPredictionJson() {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue("{\"confidence\": 0.85}");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return jsonObject;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static Instant oneHourEarlier(Instant now) {
        return now.minus(1, ChronoUnit.HOURS);
    }

    private static Instant thirtyMinutesLater(Instant now) {
        return now.plus(30, ChronoUnit.MINUTES);
    }

    private static Instant fortyFiveMinutesLater(Instant now) {
        return now.plus(45, ChronoUnit.MINUTES);
    }

    private static Instant oneHourLater(Instant now) {
        return now.plus(1, ChronoUnit.HOURS);
    }

    private static Instant twoHoursLater(Instant now) {
        return now.plus(2, ChronoUnit.HOURS);
    }
}