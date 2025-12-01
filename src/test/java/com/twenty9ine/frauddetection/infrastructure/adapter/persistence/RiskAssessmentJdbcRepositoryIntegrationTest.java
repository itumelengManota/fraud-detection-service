package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisabledInAotMode
class RiskAssessmentJdbcRepositoryIntegrationTest {

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
    private RiskAssessmentJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
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
    void shouldReturnEmptyWhenTransactionIdNotFound() {
        // When
        Optional<RiskAssessmentEntity> found = repository.findByTransactionId(UUID.randomUUID());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByRiskLevelAndAssessmentTimeGreaterThanEqual() {
        // Given
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant searchTime = baseTime.minus(1, ChronoUnit.HOURS);

        RiskAssessmentEntity highRisk1 = createRiskAssessmentEntity();
        highRisk1.setRiskLevel("HIGH");
        highRisk1.setAssessmentTime(baseTime);
        repository.save(highRisk1);

        RiskAssessmentEntity highRisk2 = createRiskAssessmentEntity();
        highRisk2.setRiskLevel("HIGH");
        highRisk2.setAssessmentTime(baseTime.plus(1, ChronoUnit.HOURS));
        repository.save(highRisk2);

        RiskAssessmentEntity lowRisk = createRiskAssessmentEntity();
        lowRisk.setRiskLevel("LOW");
        lowRisk.setAssessmentTime(baseTime);
        repository.save(lowRisk);

        RiskAssessmentEntity oldHighRisk = createRiskAssessmentEntity();
        oldHighRisk.setRiskLevel("HIGH");
        oldHighRisk.setAssessmentTime(searchTime.minus(1, ChronoUnit.HOURS));
        repository.save(oldHighRisk);

        // When
        List<RiskAssessmentEntity> results = repository
                .findByRiskLevelAndAssessmentTimeGreaterThanEqualOrderByAssessmentTimeDesc("HIGH", searchTime);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAssessmentTime()).isAfter(results.get(1).getAssessmentTime());
        assertThat(results).allMatch(r -> r.getRiskLevel().equals("HIGH"));
        assertThat(results).allMatch(r -> r.getAssessmentTime().compareTo(searchTime) >= 0);
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchingRiskLevelAndTime() {
        // Given
        Instant futureTime = Instant.now().plus(10, ChronoUnit.DAYS);

        // When
        List<RiskAssessmentEntity> results = repository
                .findByRiskLevelAndAssessmentTimeGreaterThanEqualOrderByAssessmentTimeDesc("HIGH", futureTime);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void shouldFindAll() {
        // Given
        repository.save(createRiskAssessmentEntity());
        repository.save(createRiskAssessmentEntity());
        repository.save(createRiskAssessmentEntity());

        // When
        List<RiskAssessmentEntity> all = (List<RiskAssessmentEntity>) repository.findAll();

        // Then
        assertThat(all).hasSize(3);
    }

    @Test
    void shouldDeleteById() {
        // Given
        RiskAssessmentEntity entity = repository.save(createRiskAssessmentEntity());

        // When
        repository.deleteById(entity.getId());
        Optional<RiskAssessmentEntity> found = repository.findById(entity.getId());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateEntity() {
        // Given
        RiskAssessmentEntity entity = createRiskAssessmentEntity();
        entity.setRiskScoreValue(50);
        RiskAssessmentEntity saved = repository.save(entity);

        // When
        saved.setRiskScoreValue(80);
        saved.setRiskLevel("HIGH");
        RiskAssessmentEntity updated = repository.save(saved);

        // Then
        Optional<RiskAssessmentEntity> found = repository.findById(updated.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRiskScoreValue()).isEqualTo(80);
        assertThat(found.get().getRiskLevel()).isEqualTo("HIGH");
    }

    private RiskAssessmentEntity createRiskAssessmentEntity() {
        return RiskAssessmentEntity.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .riskScoreValue(75)
                .riskLevel("MEDIUM")
                .decision("REVIEW")
                .mlPredictionJson(createMlPredictionJson())
                .assessmentTime(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .createdAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .updatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS)).build();
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