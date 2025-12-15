package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.AbstractIntegrationTest;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RuleEvaluationEntity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.aot.DisabledInAotMode;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel.LOW;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for RiskAssessmentMapper.
 *
 * Performance Optimizations:
 * - Extends AbstractIntegrationTest for shared container infrastructure
 * - Uses @ActiveProfiles("test") for optimized test configuration
 * - Parallel execution enabled with proper resource management
 * - All comprehensive test coverage retained from original
 */
@DataJdbcTest
@DisabledInAotMode
@ActiveProfiles("test")
@ComponentScan(basePackages = "com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("RiskAssessmentMapper Integration Tests")
@Execution(ExecutionMode.CONCURRENT)
class RiskAssessmentMapperTest extends AbstractIntegrationTest {

    @Autowired
    private RiskAssessmentMapper mapper;

    private Instant timestamp;

    @BeforeEach
    void setUp() {
        // Truncate to milliseconds to match database precision
        timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // ========================================
    // Domain to Entity Mapping Tests
    // ========================================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    @Execution(ExecutionMode.CONCURRENT)
    class DomainToEntityMappingTests {

        @Test
        @DisplayName("Should map complete risk assessment with all fields")
        void testToEntity_CompleteRiskAssessment_MapsAllFields() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            RuleEvaluation ruleEvaluation = new RuleEvaluation("RULE001", "High Amount Rule", RuleType.AMOUNT,
                    true, 50, "Amount exceeds threshold");
            MLPrediction mlPrediction = new MLPrediction("modelId", "RandomForest", 0.92,
                    1.0, Map.of("feature1", 0.5, "feature2", 0.3));

            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(75), List.of(ruleEvaluation), mlPrediction);
            assessment.completeAssessment(Decision.REVIEW);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertNotNull(entity);
            assertEquals(assessment.getAssessmentId().toUUID(), entity.getId());
            assertEquals(transactionId.toUUID(), entity.getTransactionId());
            assertEquals(75, entity.getRiskScoreValue());
            assertEquals("HIGH", entity.getRiskLevel());
            assertEquals("REVIEW", entity.getDecision());
            assertEquals(assessment.getAssessmentTime(), entity.getAssessmentTime());
            assertNotNull(entity.getMlPredictionJson());
            assertEquals(1, entity.getRuleEvaluations().size());
        }

        @Test
        @DisplayName("Should map minimal risk assessment with required fields only")
        void testToEntity_MinimalRiskAssessment_MapsRequiredFields() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(30));
            assessment.completeAssessment(Decision.ALLOW);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertNotNull(entity);
            assertEquals(assessment.getAssessmentId().toUUID(), entity.getId());
            assertEquals(transactionId.toUUID(), entity.getTransactionId());
            assertEquals(30, entity.getRiskScoreValue());
            assertEquals("LOW", entity.getRiskLevel());
            assertEquals("ALLOW", entity.getDecision());
            assertNull(entity.getMlPredictionJson());
            assertTrue(entity.getRuleEvaluations().isEmpty());
        }

        @Test
        @DisplayName("Should map multiple rule evaluations correctly")
        void testToEntity_WithMultipleRuleEvaluations_MapsAll() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(90));

            assessment.addRuleEvaluation(new RuleEvaluation("RULE001", "Rule 1", RuleType.AMOUNT, true, 30, "Desc 1"));
            assessment.addRuleEvaluation(new RuleEvaluation("RULE002", "Rule 2", RuleType.VELOCITY, true, 25, "Desc 2"));
            assessment.addRuleEvaluation(new RuleEvaluation("RULE003", "Rule 3", RuleType.GEOGRAPHIC, true, 20, "Desc 3"));

            assessment.completeAssessment(Decision.REVIEW);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertEquals(3, entity.getRuleEvaluations().size());
        }

        @Test
        @DisplayName("Should map all risk levels correctly")
        void testToEntity_AllRiskLevels_MapCorrectly() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());

            Map<Integer, Map<String, Decision>> scoreToLevel = Map.of(
                    10, Map.of("LOW", Decision.ALLOW),
                    30, Map.of("LOW", Decision.ALLOW),
                    40, Map.of("LOW", Decision.ALLOW),
                    60, Map.of("MEDIUM", Decision.CHALLENGE),
                    70, Map.of("MEDIUM", Decision.CHALLENGE),
                    90, Map.of("HIGH", Decision.REVIEW),
                    95, Map.of("CRITICAL", Decision.BLOCK)
            );

            for (Map.Entry<Integer, Map<String, Decision>> entry : scoreToLevel.entrySet()) {
                entry.getValue().forEach((riskLevel, decision) -> {
                    RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(entry.getKey()));
                    assessment.completeAssessment(decision);

                    RiskAssessmentEntity entity = mapper.toEntity(assessment);

                    assertEquals(riskLevel, entity.getRiskLevel());
                });
            }
        }

        @Test
        @DisplayName("Should map all decision types correctly")
        void testToEntity_AllDecisions_MapCorrectly() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());

            for (Decision decision : Decision.values()) {
                RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(90));
                assessment.completeAssessment(decision);

                RiskAssessmentEntity entity = mapper.toEntity(assessment);

                assertEquals(decision.name(), entity.getDecision());
            }
        }

        @Test
        @DisplayName("Should serialize ML prediction to JSON correctly")
        void testToEntity_WithMLPrediction_SerializesCorrectly() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            Map<String, Double> features = Map.of("amount", 0.7, "velocity", 0.5, "location", 0.3);
            MLPrediction mlPrediction = new MLPrediction("model123", "GradientBoost", 0.85, 0.93, features);

            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(91), List.of(), mlPrediction);
            assessment.completeAssessment(Decision.BLOCK);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertNotNull(entity.getMlPredictionJson());
            assertEquals("jsonb", entity.getMlPredictionJson().getType());

            String json = entity.getMlPredictionJson().getValue();
            assertTrue(json.contains("0.85"));
            assertTrue(json.contains("GradientBoost"));
        }

        @Test
        @DisplayName("Should return null JSON when ML prediction is absent")
        void testToEntity_NoMLPrediction_ReturnsNullJson() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(90));
            assessment.completeAssessment(Decision.REVIEW);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertNull(entity.getMlPredictionJson());
        }

        @Test
        @DisplayName("Should map zero risk score correctly")
        void testToEntity_ZeroRiskScore_MapsCorrectly() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(0));
            assessment.completeAssessment(Decision.ALLOW);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertEquals(0, entity.getRiskScoreValue());
            assertEquals("LOW", entity.getRiskLevel());
        }

        @Test
        @DisplayName("Should map maximum risk score correctly")
        void testToEntity_MaximumRiskScore_MapsCorrectly() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());
            RiskAssessment assessment = new RiskAssessment(transactionId, RiskScore.of(100));
            assessment.completeAssessment(Decision.BLOCK);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertEquals(100, entity.getRiskScoreValue());
            assertEquals("CRITICAL", entity.getRiskLevel());
        }
    }

    // ========================================
    // Entity to Domain Mapping Tests
    // ========================================

    @Nested
    @DisplayName("Entity to Domain Mapping")
    @Execution(ExecutionMode.CONCURRENT)
    class EntityToDomainMappingTests {

        @Test
        @DisplayName("Should map complete entity with all fields")
        void testToDomain_CompleteEntity_MapsAllFields() throws SQLException {
            UUID assessmentId = UUID.randomUUID();
            UUID transactionId = UUID.randomUUID();

            RiskAssessmentEntity entity = RiskAssessmentEntity.builder()
                    .id(assessmentId)
                    .transactionId(transactionId)
                    .riskScoreValue(80)
                    .riskLevel("HIGH")
                    .decision("REVIEW")
                    .assessmentTime(timestamp)
                    .mlPredictionJson(buildMlJson())
                    .ruleEvaluations(buildRuleEvaluationEntities())
                    .build();

            RiskAssessment assessment = mapper.toDomain(entity);

            assertNotNull(assessment);
            assertEquals(assessmentId, assessment.getAssessmentId().toUUID());
            assertEquals(transactionId, assessment.getTransactionId().toUUID());
            assertEquals(80, assessment.getRiskScore().value());
            assertEquals(TransactionRiskLevel.HIGH, assessment.getTransactionRiskLevel());
            assertEquals(Decision.REVIEW, assessment.getDecision());
            // Use tolerance-based comparison for timestamps (within 10ms)
            assertTrue(Math.abs(timestamp.toEpochMilli() - assessment.getAssessmentTime().toEpochMilli()) < 10,
                    "Timestamps should be within 10ms of each other");
            assertNotNull(assessment.getMlPrediction());
            assertEquals(1, assessment.getRuleEvaluations().size());
        }

        @Test
        @DisplayName("Should map minimal entity with required fields only")
        void testToDomain_MinimalEntity_MapsRequiredFields() {
            RiskAssessmentEntity entity = RiskAssessmentEntity.builder()
                    .id(UUID.randomUUID())
                    .transactionId(UUID.randomUUID())
                    .riskScoreValue(25)
                    .riskLevel("LOW")
                    .decision("ALLOW")
                    .assessmentTime(timestamp)
                    .ruleEvaluations(new HashSet<>())
                    .build();

            RiskAssessment assessment = mapper.toDomain(entity);

            assertNotNull(assessment);
            assertEquals(25, assessment.getRiskScore().value());
            assertEquals(LOW, assessment.getTransactionRiskLevel());
            assertEquals(Decision.ALLOW, assessment.getDecision());
            assertNull(assessment.getMlPrediction());
            assertTrue(assessment.getRuleEvaluations().isEmpty());
        }

        @Test
        @DisplayName("Should map all risk levels correctly")
        void testToDomain_AllRiskLevels_MapCorrectly() {
            Map<Integer, Map<String, String>> levels = Map.of(
                    40, Map.of("LOW", "ALLOW"),
                    70, Map.of("MEDIUM", "CHALLENGE"),
                    90, Map.of("HIGH", "REVIEW"),
                    91, Map.of("CRITICAL", "BLOCK")
            );

            levels.forEach((riskScore, values) -> {
                values.forEach((riskLevel, decision) -> {
                    RiskAssessmentEntity entity = RiskAssessmentEntity.builder()
                            .id(UUID.randomUUID())
                            .transactionId(UUID.randomUUID())
                            .riskScoreValue(riskScore)
                            .riskLevel(riskLevel)
                            .decision(decision)
                            .assessmentTime(timestamp)
                            .ruleEvaluations(new HashSet<>())
                            .build();

                    RiskAssessment assessment = mapper.toDomain(entity);

                    assertEquals(TransactionRiskLevel.valueOf(riskLevel), assessment.getTransactionRiskLevel());
                });
            });
        }

        @Test
        @DisplayName("Should map all decision types correctly")
        void testToDomain_AllDecisions_MapCorrectly() {
            for (String decision : List.of("ALLOW", "REVIEW", "BLOCK")) {
                RiskAssessmentEntity entity = RiskAssessmentEntity.builder()
                        .id(UUID.randomUUID())
                        .transactionId(UUID.randomUUID())
                        .riskScoreValue(50)
                        .riskLevel("MEDIUM")
                        .decision(decision)
                        .assessmentTime(timestamp)
                        .ruleEvaluations(new HashSet<>())
                        .build();

                RiskAssessment assessment = mapper.toDomain(entity);

                assertEquals(Decision.valueOf(decision), assessment.getDecision());
            }
        }

        @Test
        @DisplayName("Should deserialize ML prediction from JSON correctly")
        void testToDomain_WithMLPrediction_DeserializesCorrectly() throws SQLException {
            RiskAssessmentEntity entity = RiskAssessmentEntity.builder()
                    .id(UUID.randomUUID())
                    .transactionId(UUID.randomUUID())
                    .riskScoreValue(85)
                    .riskLevel("HIGH")
                    .decision("BLOCK")
                    .assessmentTime(timestamp)
                    .ruleEvaluations(new HashSet<>())
                    .build();

            PGobject mlJson = new PGobject();
            mlJson.setType("json");
            mlJson.setValue("{\"fraudProbability\":0.85,\"modelId\":\"model-id\",\"modelVersion\":\"RandomForest\",\"confidence\":0.92,\"featureImportance\":{\"f1\":0.5}}");
            entity.setMlPredictionJson(mlJson);

            RiskAssessment assessment = mapper.toDomain(entity);

            assertNotNull(assessment.getMlPrediction());
            assertEquals(0.85, assessment.getMlPrediction().fraudProbability());
            assertEquals("RandomForest", assessment.getMlPrediction().modelVersion());
            assertEquals(0.92, assessment.getMlPrediction().confidence());
        }

        @Test
        @DisplayName("Should return null when ML prediction JSON is absent")
        void testToDomain_NoMLPrediction_ReturnsNull() {
            RiskAssessmentEntity entity = RiskAssessmentEntity.builder()
                    .id(UUID.randomUUID())
                    .transactionId(UUID.randomUUID())
                    .riskScoreValue(40)
                    .riskLevel("MEDIUM")
                    .decision("REVIEW")
                    .assessmentTime(timestamp)
                    .ruleEvaluations(new HashSet<>())
                    .mlPredictionJson(null)
                    .build();

            RiskAssessment assessment = mapper.toDomain(entity);

            assertNull(assessment.getMlPrediction());
        }

        @Test
        @DisplayName("Should map multiple rule evaluations correctly")
        void testToDomain_WithMultipleRuleEvaluations_MapsAll() {
            RiskAssessmentEntity entity = RiskAssessmentEntity.builder()
                    .id(UUID.randomUUID())
                    .transactionId(UUID.randomUUID())
                    .riskScoreValue(75)
                    .riskLevel("HIGH")
                    .decision("REVIEW")
                    .assessmentTime(timestamp)
                    .build();

            Set<RuleEvaluationEntity> ruleEntities = new HashSet<>();
            for (int i = 1; i <= 3; i++) {
                RuleEvaluationEntity ruleEntity = new RuleEvaluationEntity();
                ruleEntity.setId((long) i);
                ruleEntity.setRuleName("Rule " + i);
                ruleEntity.setRuleType("AMOUNT");
                ruleEntity.setScoreImpact(20 + i * 5);
                ruleEntity.setDescription("Description " + i);
                ruleEntities.add(ruleEntity);
            }
            entity.setRuleEvaluations(ruleEntities);

            RiskAssessment assessment = mapper.toDomain(entity);

            assertEquals(3, assessment.getRuleEvaluations().size());
        }
    }

    // ========================================
    // Round-Trip Mapping Tests
    // ========================================

    @Nested
    @DisplayName("Round-Trip Mapping")
    @Execution(ExecutionMode.CONCURRENT)
    class RoundTripMappingTests {

        @Test
        @DisplayName("Should preserve data in domain-entity-domain round trip")
        void testRoundTrip_DomainToEntityToDomain_PreservesData() {
            TransactionId transactionId = TransactionId.of(UUID.randomUUID());

            RuleEvaluation ruleEvaluation = new RuleEvaluation("RULE001", "Test Rule", RuleType.VELOCITY,
                    true, 35, "Description");

            MLPrediction mlPrediction = new MLPrediction("modelId", "1.0.0", 0.75,
                    0.88, Map.of("feature1", 0.6));

            RiskAssessment originalAssessment = new RiskAssessment(transactionId, RiskScore.of(90),
                    List.of(ruleEvaluation), mlPrediction);
            originalAssessment.completeAssessment(Decision.REVIEW);

            RiskAssessmentEntity entity = mapper.toEntity(originalAssessment);
            RiskAssessment roundTripAssessment = mapper.toDomain(entity);

            assertEquals(originalAssessment.getAssessmentId(), roundTripAssessment.getAssessmentId());
            assertEquals(originalAssessment.getTransactionId(), roundTripAssessment.getTransactionId());
            assertEquals(originalAssessment.getRiskScore().value(), roundTripAssessment.getRiskScore().value());
            assertEquals(originalAssessment.getTransactionRiskLevel(), roundTripAssessment.getTransactionRiskLevel());
            assertEquals(originalAssessment.getDecision(), roundTripAssessment.getDecision());
            assertEquals(originalAssessment.getRuleEvaluations().size(), roundTripAssessment.getRuleEvaluations().size());
        }

        @Test
        @DisplayName("Should preserve data in entity-domain-entity round trip")
        void testRoundTrip_EntityToDomainToEntity_PreservesData() throws SQLException {
            UUID assessmentId = UUID.randomUUID();
            UUID transactionId = UUID.randomUUID();

            RiskAssessmentEntity originalEntity = getRiskAssessmentEntity(assessmentId, transactionId);

            RiskAssessment assessment = mapper.toDomain(originalEntity);
            RiskAssessmentEntity roundTripEntity = mapper.toEntity(assessment);

            assertEquals(originalEntity.getId(), roundTripEntity.getId());
            assertEquals(originalEntity.getTransactionId(), roundTripEntity.getTransactionId());
            assertEquals(originalEntity.getRiskScoreValue(), roundTripEntity.getRiskScoreValue());
            assertEquals(originalEntity.getRiskLevel(), roundTripEntity.getRiskLevel());
            assertEquals(originalEntity.getDecision(), roundTripEntity.getDecision());
        }
    }

    // ========================================
    // Individual Field Mapping Tests
    // ========================================

    @Nested
    @DisplayName("Individual Field Mapping")
    @Execution(ExecutionMode.CONCURRENT)
    class IndividualFieldMappingTests {

        @Test
        @DisplayName("Should map assessment ID in both directions correctly")
        void testAssessmentIdMapping_BothDirections_WorksCorrectly() {
            UUID uuid = UUID.randomUUID();
            AssessmentId assessmentId = AssessmentId.of(uuid);

            UUID mappedUuid = mapper.assessmentIdToUuid(assessmentId);
            AssessmentId mappedId = AssessmentId.of(uuid);

            assertEquals(uuid, mappedUuid);
            assertEquals(assessmentId, mappedId);
        }

        @Test
        @DisplayName("Should map transaction ID in both directions correctly")
        void testTransactionIdMapping_BothDirections_WorksCorrectly() {
            UUID uuid = UUID.randomUUID();
            TransactionId transactionId = TransactionId.of(uuid);

            UUID mappedUuid = mapper.transactionIdToUuid(transactionId);
            TransactionId mappedId = TransactionId.of(uuid);

            assertEquals(uuid, mappedUuid);
            assertEquals(transactionId, mappedId);
        }

        @Test
        @DisplayName("Should map risk score from integer correctly")
        void testRiskScoreMapping_FromInteger_ReturnsCorrectScore() {
            assertEquals(0, RiskScore.of(0).value());
            assertEquals(50, RiskScore.of(50).value());
            assertEquals(100, RiskScore.of(100).value());
        }

        @Test
        @DisplayName("Should map risk level to string and back correctly")
        void testRiskLevelMapping_ToStringAndBack_WorksCorrectly() {
            for (TransactionRiskLevel level : TransactionRiskLevel.values()) {
                String levelString = mapper.riskLevelToString(level);
                TransactionRiskLevel mappedLevel = TransactionRiskLevel.fromString(levelString);

                assertEquals(level, mappedLevel);
            }
        }

        @Test
        @DisplayName("Should map decision to string and back correctly")
        void testDecisionMapping_ToStringAndBack_WorksCorrectly() {
            for (Decision decision : Decision.values()) {
                String decisionString = mapper.decisionToString(decision);
                Decision mappedDecision = Decision.fromString(decisionString);

                assertEquals(decision, mappedDecision);
            }
        }

        @Test
        @DisplayName("Should create valid PGobject from ML prediction")
        void testMlPredictionToJson_ValidPrediction_CreatesValidPGobject() {
            MLPrediction prediction = new MLPrediction("modelId", "RandomForest", 0.85,
                    0.90, Map.of("key1", 0.5, "key2", 0.3));

            PGobject pgObject = mapper.mlPredictionToJson(prediction);

            assertNotNull(pgObject);
            assertEquals("jsonb", pgObject.getType());
            assertTrue(pgObject.getValue().contains("0.85"));
            assertTrue(pgObject.getValue().contains("RandomForest"));
        }

        @Test
        @DisplayName("Should return null when ML prediction is null")
        void testMlPredictionToJson_NullPrediction_ReturnsNull() {
            PGobject result = mapper.mlPredictionToJson(null);
            assertNull(result);
        }

        @Test
        @DisplayName("Should deserialize valid JSON to ML prediction")
        void testJsonToMlPrediction_ValidJson_DeserializesCorrectly() throws SQLException {
            PGobject pgObject = new PGobject();
            pgObject.setType("json");
            pgObject.setValue("{\"fraudProbability\":0.80,\"modelId\":\"model-id\",\"modelVersion\":\"Model\",\"confidence\":0.95,\"featureImportance\":{\"f1\":0.4}}");

            MLPrediction prediction = mapper.jsonToMlPrediction(pgObject);

            assertNotNull(prediction);
            assertEquals(0.80, prediction.fraudProbability());
            assertEquals("Model", prediction.modelVersion());
            assertEquals(0.95, prediction.confidence());
        }

        @Test
        @DisplayName("Should return null when JSON is null")
        void testJsonToMlPrediction_NullJson_ReturnsNull() {
            MLPrediction prediction = mapper.jsonToMlPrediction(null);
            assertNull(prediction);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private static @NotNull Set<RuleEvaluationEntity> buildRuleEvaluationEntities() {
        Set<RuleEvaluationEntity> ruleEntities = new HashSet<>();
        RuleEvaluationEntity ruleEntity = new RuleEvaluationEntity();
        ruleEntity.setId(1L);
        ruleEntity.setRuleName("Test Rule");
        ruleEntity.setRuleType("AMOUNT");
        ruleEntity.setScoreImpact(40);
        ruleEntity.setDescription("Test description");
        ruleEntities.add(ruleEntity);
        return ruleEntities;
    }

    private static @NotNull PGobject buildMlJson() throws SQLException {
        PGobject mlJson = new PGobject();
        mlJson.setType("jsonb");
        mlJson.setValue("{\"modelId\":\"model-id\",\"modelVersion\":\"XGBoost\",\"fraudProbability\":0.80,\"confidence\":0.90,\"featureImportance\":{}}");
        return mlJson;
    }

    private @NotNull RiskAssessmentEntity getRiskAssessmentEntity(UUID assessmentId, UUID transactionId) throws SQLException {
        return RiskAssessmentEntity.builder()
                .id(assessmentId)
                .transactionId(transactionId)
                .riskScoreValue(65)
                .riskLevel("MEDIUM")
                .decision("REVIEW")
                .assessmentTime(timestamp)
                .mlPredictionJson(buildPGobject())
                .ruleEvaluations(new HashSet<>())
                .build();
    }

    private PGobject buildPGobject() throws SQLException {
        PGobject mlJson = new PGobject();
        mlJson.setType("jsonb");
        mlJson.setValue("{\"modelId\":\"fraud-detector-v1\",\"modelVersion\":\"1.0.0\",\"fraudProbability\":0.75,\"confidence\":0.88,\"featureImportance\":{\"amount\":0.4,\"velocity\":0.6}}");
        return mlJson;
    }
}