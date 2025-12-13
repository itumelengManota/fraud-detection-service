package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisabledInAotMode
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("postgres")
class RiskAssessmentJdbcRepositoryIntegrationTest {

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
    private RiskAssessmentJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldSaveAndFindById() {
        RiskAssessmentEntity entity = createRiskAssessmentEntity();

        RiskAssessmentEntity saved = repository.save(entity);
        Optional<RiskAssessmentEntity> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getTransactionId()).isEqualTo(entity.getTransactionId());
        assertThat(found.get().getRiskScoreValue()).isEqualTo(entity.getRiskScoreValue());
        assertThat(found.get().getRiskLevel()).isEqualTo(entity.getRiskLevel());
        assertThat(found.get().getDecision()).isEqualTo(entity.getDecision());
    }

    @Test
    void shouldFindByTransactionId() {
        UUID transactionId = UUID.randomUUID();
        RiskAssessmentEntity entity = createRiskAssessmentEntity();
        entity.setTransactionId(transactionId);
        repository.save(entity);

        Optional<RiskAssessmentEntity> found = repository.findByTransactionId(transactionId);

        assertThat(found).isPresent();
        assertThat(found.get().getTransactionId()).isEqualTo(transactionId);
    }

    @Test
    void shouldReturnEmptyWhenTransactionIdNotFound() {
        Optional<RiskAssessmentEntity> found = repository.findByTransactionId(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByRiskLevelAndAssessmentTimeGreaterThanEqualWithPagination() {
        Instant now = now();
        Instant searchTime = oneHourEarlier(now);

        createAndSaveEntity("HIGH", now);
        createAndSaveEntity("HIGH", thirtyMinutesLater(now));
        createAndSaveEntity("CRITICAL", fourtyFiveMinutesLater(now));
        createAndSaveEntity("LOW", now);
        createAndSaveEntity("HIGH", oneHourLater(now));

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("assessmentTime").descending());

        Page<RiskAssessmentEntity> results = repository.findByRiskLevelInAndAssessmentTimeGreaterThanEqual(Set.of("HIGH", "CRITICAL"), searchTime, pageRequest);

        assertThat(results.getContent()).hasSize(4);
        assertThat(results.getTotalElements()).isEqualTo(4);
        assertThat(results.getContent()).allMatch(r -> Set.of("HIGH", "CRITICAL").contains(r.getRiskLevel()));
        assertThat(results.getContent()).allMatch(r -> r.getAssessmentTime().isAfter(searchTime));
    }

    private static Instant fourtyFiveMinutesLater(Instant now) {
        return now.plus(45, ChronoUnit.MINUTES);
    }

    private static Instant thirtyMinutesLater(Instant now) {
        return now.plus(30, ChronoUnit.MINUTES);
    }

    @Test
    void shouldHandlePaginationCorrectly() {
        Instant baseTime = now();

        for (int i = 0; i < 5; i++) {
            createAndSaveEntity("HIGH", baseTime.plus(i, ChronoUnit.MINUTES));
        }

        PageRequest firstPage = PageRequest.of(0, 2, Sort.by("assessmentTime").descending());
        PageRequest secondPage = PageRequest.of(1, 2, Sort.by("assessmentTime").descending());

        Page<RiskAssessmentEntity> page1 = repository.findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
                        Set.of("HIGH"),
                        baseTime.minus(10, ChronoUnit.MINUTES),
                        firstPage
                );

        Page<RiskAssessmentEntity> page2 = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
                        Set.of("HIGH"),
                        baseTime.minus( 10, ChronoUnit.MINUTES),
                        secondPage
                );

        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalPages()).isEqualTo(3);
        assertThat(page2.getContent()).hasSize(2);
        assertThat(page1.getContent().getFirst().getId()).isNotEqualTo(page2.getContent().getFirst().getId());
    }

    @Test
    void shouldSortResultsByAssessmentTimeDescending() {
        Instant now = now();

        createAndSaveEntity("HIGH", now);
        createAndSaveEntity("HIGH", oneHourLater(now));
        createAndSaveEntity("HIGH", twoHoursLater(now));

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("assessmentTime").descending());

        Page<RiskAssessmentEntity> results = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(Set.of("HIGH"), now.minus(3, ChronoUnit.HOURS), pageRequest);

        assertThat(results.getContent()).hasSize(3);
        assertThat(results.getContent().get(0).getAssessmentTime())
                .isAfter(results.getContent().get(1).getAssessmentTime());
        assertThat(results.getContent().get(1).getAssessmentTime())
                .isAfter(results.getContent().get(2).getAssessmentTime());
    }

    private static Instant twoHoursLater(Instant now) {
        return now.plus(2, ChronoUnit.HOURS);
    }

    private static Instant oneHourLater(Instant now) {
        return now.plus(1, ChronoUnit.HOURS);
    }

    private static Instant oneHourEarlier(Instant now) {
        return now.minus(1, ChronoUnit.HOURS);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Test
    void shouldReturnEmptyPageWhenNoMatchingRiskLevelAndTime() {
        Instant futureTime = Instant.now().plus(10, ChronoUnit.DAYS);
        PageRequest pageRequest = PageRequest.of(0, 10);

        Page<RiskAssessmentEntity> results = repository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(Set.of("HIGH"), futureTime, pageRequest);

        assertThat(results.getContent()).isEmpty();
        assertThat(results.getTotalElements()).isZero();
    }

    @Test
    void shouldUpdateEntity() {
        RiskAssessmentEntity entity = createRiskAssessmentEntity();
        entity.setRiskScoreValue(50);
        RiskAssessmentEntity saved = repository.save(entity);

        assertThat(saved.getRiskScoreValue()).isEqualTo(50);
        assertThat(saved.getRevision()).isEqualTo(1);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        saved.setRiskScoreValue(80);
        saved.setRiskLevel("HIGH");
        RiskAssessmentEntity updated = repository.save(saved);

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
    void shouldDeleteById() {
        RiskAssessmentEntity entity = repository.save(createRiskAssessmentEntity());

        Optional<RiskAssessmentEntity> found1 = repository.findById(entity.getId());
        assertThat(found1).isPresent();

        repository.deleteById(entity.getId());
        Optional<RiskAssessmentEntity> found2 = repository.findById(entity.getId());

        assertThat(found2).isEmpty();
    }

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
}