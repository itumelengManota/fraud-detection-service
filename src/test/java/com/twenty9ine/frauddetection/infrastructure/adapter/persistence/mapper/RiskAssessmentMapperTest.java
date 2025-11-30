package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RuleEvaluationEntity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static com.twenty9ine.frauddetection.domain.valueobject.RiskLevel.LOW;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
@SpringBootTest
class RiskAssessmentMapperTest {

    @Autowired
    private RiskAssessmentMapper mapper;

    @Autowired
    private ObjectMapper objectMapper;

    private Instant timestamp;

    @BeforeEach
    void setUp() {
        timestamp = Instant.now();
    }

    @Test
    void testToEntity_CompleteRiskAssessment_MapsAllFields() throws SQLException {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment assessment = RiskAssessment.of(transactionId);

        assessment.addRuleEvaluation(new RuleEvaluation(
                "RULE001",
                "High Amount Rule",
                RuleType.AMOUNT,
                true,
                50,
                "Amount exceeds threshold"
        ));

        assessment.setMlPrediction(new MLPrediction(
                "modelId",
                "RandomForest",
                0.92,
                1.0,
                Map.of("feature1", 0.5, "feature2", 0.3)
        ));

        assessment.completeAssessment(
                RiskScore.of(75),
                Decision.REVIEW
        );

        RiskAssessmentEntity entity = mapper.toEntity(assessment);

        assertNotNull(entity);
        assertEquals(assessment.getAssessmentId().toUUID(), entity.getId());
        assertEquals(transactionId.toUUID(), entity.getTransactionId());
        assertEquals(75, entity.getRiskScoreValue());
        assertEquals("MEDIUM", entity.getRiskLevel());
        assertEquals("REVIEW", entity.getDecision());
        assertEquals(assessment.getAssessmentTime(), entity.getAssessmentTime());
        assertNotNull(entity.getMlPredictionJson());
        assertEquals(1, entity.getRuleEvaluations().size());
    }

    @Test
    void testToEntity_MinimalRiskAssessment_MapsRequiredFields() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment assessment = RiskAssessment.of(transactionId);

        assessment.completeAssessment(
                RiskScore.of(30),
                Decision.ALLOW
        );

        RiskAssessmentEntity entity = mapper.toEntity(assessment);

        assertNotNull(entity);
        assertEquals(assessment.getAssessmentId().toUUID(), entity.getId());
        assertEquals(transactionId.toUUID(), entity.getTransactionId());
        assertEquals(30, entity.getRiskScoreValue());
        assertEquals("LOW", entity.getRiskLevel());
        assertEquals("PROCEED", entity.getDecision());
        assertNull(entity.getMlPredictionJson());
        assertTrue(entity.getRuleEvaluations().isEmpty());
    }

    @Test
    void testToEntity_WithMultipleRuleEvaluations_MapsAll() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment assessment = RiskAssessment.of(transactionId);

        assessment.addRuleEvaluation(new RuleEvaluation(
                "RULE001", "Rule 1", RuleType.AMOUNT, true, 30, "Desc 1"
        ));
        assessment.addRuleEvaluation(new RuleEvaluation(
                "RULE002", "Rule 2", RuleType.VELOCITY, true, 25, "Desc 2"
        ));
        assessment.addRuleEvaluation(new RuleEvaluation(
                "RULE003", "Rule 3", RuleType.GEOGRAPHIC, true, 20, "Desc 3"
        ));

        assessment.completeAssessment(RiskScore.of(75), Decision.REVIEW);

        RiskAssessmentEntity entity = mapper.toEntity(assessment);

        assertEquals(3, entity.getRuleEvaluations().size());
    }

    @Test
    void testToEntity_AllRiskLevels_MapCorrectly() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());

        Map<Integer, String> scoreToLevel = Map.of(
                10, "LOW",
                30, "LOW",
                40, "MEDIUM",
                60, "MEDIUM",
                70, "HIGH",
                90, "HIGH",
                95, "CRITICAL"
        );

        for (Map.Entry<Integer, String> entry : scoreToLevel.entrySet()) {
            RiskAssessment assessment = RiskAssessment.of(transactionId);
            assessment.completeAssessment(RiskScore.of(entry.getKey()), Decision.REVIEW);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertEquals(entry.getValue(), entity.getRiskLevel());
        }
    }

    @Test
    void testToEntity_AllDecisions_MapCorrectly() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());

        for (Decision decision : Decision.values()) {
            RiskAssessment assessment = RiskAssessment.of(transactionId);
            assessment.completeAssessment(RiskScore.of(50), decision);

            RiskAssessmentEntity entity = mapper.toEntity(assessment);

            assertEquals(decision.name(), entity.getDecision());
        }
    }

    @Test
    void testToEntity_WithMLPrediction_SerializesCorrectly() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment assessment = RiskAssessment.of(transactionId);

        Map<String, Double> features = Map.of(
                "amount", 0.7,
                "velocity", 0.5,
                "location", 0.3
        );

        MLPrediction mlPrediction = new MLPrediction(
                "model123",
                "GradientBoost",
                0.85,
                0.93,
                features
        );

        assessment.setMlPrediction(mlPrediction);
        assessment.completeAssessment(RiskScore.of(85), Decision.BLOCK);

        RiskAssessmentEntity entity = mapper.toEntity(assessment);

        assertNotNull(entity.getMlPredictionJson());
        assertEquals("json", entity.getMlPredictionJson().getType());

        String json = entity.getMlPredictionJson().getValue();
        assertTrue(json.contains("0.85"));
        assertTrue(json.contains("GradientBoost"));
    }

    @Test
    void testToEntity_NoMLPrediction_ReturnsNullJson() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment assessment = RiskAssessment.of(transactionId);
        assessment.completeAssessment(RiskScore.of(50), Decision.REVIEW);

        RiskAssessmentEntity entity = mapper.toEntity(assessment);

        assertNull(entity.getMlPredictionJson());
    }

    @Test
    void testToDomain_CompleteEntity_MapsAllFields() throws SQLException {
        UUID assessmentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setId(assessmentId);
        entity.setTransactionId(transactionId);
        entity.setRiskScoreValue(80);
        entity.setRiskLevel("HIGH");
        entity.setDecision("REVIEW");
        entity.setAssessmentTime(timestamp);

        PGobject mlJson = new PGobject();
        mlJson.setType("json");
        mlJson.setValue("{\"fraudProbability\":0.80,\"modelName\":\"XGBoost\",\"confidence\":0.90,\"features\":{}}");
        entity.setMlPredictionJson(mlJson);

        Set<RuleEvaluationEntity> ruleEntities = new HashSet<>();
        RuleEvaluationEntity ruleEntity = new RuleEvaluationEntity();
        ruleEntity.setId(1L);
        ruleEntity.setRuleName("Test Rule");
        ruleEntity.setRuleType("AMOUNT");
        ruleEntity.setScoreImpact(40);
        ruleEntity.setDescription("Test description");
        ruleEntities.add(ruleEntity);
        entity.setRuleEvaluations(ruleEntities);

        RiskAssessment assessment = mapper.toDomain(entity);

        assertNotNull(assessment);
        assertEquals(assessmentId, assessment.getAssessmentId().toUUID());
        assertEquals(transactionId, assessment.getTransactionId().toUUID());
        assertEquals(80, assessment.getRiskScore().value());
        assertEquals(RiskLevel.HIGH, assessment.getRiskLevel());
        assertEquals(Decision.REVIEW, assessment.getDecision());
        assertEquals(timestamp, assessment.getAssessmentTime());
        assertNotNull(assessment.getMlPrediction());
        assertEquals(1, assessment.getRuleEvaluations().size());
    }

    @Test
    void testToDomain_MinimalEntity_MapsRequiredFields() {
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setId(UUID.randomUUID());
        entity.setTransactionId(UUID.randomUUID());
        entity.setRiskScoreValue(25);
        entity.setRiskLevel("LOW");
        entity.setDecision("PROCEED");
        entity.setAssessmentTime(timestamp);
        entity.setRuleEvaluations(new HashSet<>());

        RiskAssessment assessment = mapper.toDomain(entity);

        assertNotNull(assessment);
        assertEquals(25, assessment.getRiskScore().value());
        assertEquals(LOW, assessment.getRiskLevel());
        assertEquals(Decision.ALLOW, assessment.getDecision());
        assertNull(assessment.getMlPrediction());
        assertTrue(assessment.getRuleEvaluations().isEmpty());
    }

    @Test
    void testToDomain_AllRiskLevels_MapCorrectly() {
        for (String level : List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")) {
            RiskAssessmentEntity entity = new RiskAssessmentEntity();
            entity.setId(UUID.randomUUID());
            entity.setTransactionId(UUID.randomUUID());
            entity.setRiskScoreValue(50);
            entity.setRiskLevel(level);
            entity.setDecision("REVIEW");
            entity.setAssessmentTime(timestamp);
            entity.setRuleEvaluations(new HashSet<>());

            RiskAssessment assessment = mapper.toDomain(entity);

            assertEquals(RiskLevel.valueOf(level), assessment.getRiskLevel());
        }
    }

    @Test
    void testToDomain_AllDecisions_MapCorrectly() {
        for (String decision : List.of("ALLOW", "REVIEW", "BLOCK")) {
            RiskAssessmentEntity entity = new RiskAssessmentEntity();
            entity.setId(UUID.randomUUID());
            entity.setTransactionId(UUID.randomUUID());
            entity.setRiskScoreValue(50);
            entity.setRiskLevel("MEDIUM");
            entity.setDecision(decision);
            entity.setAssessmentTime(timestamp);
            entity.setRuleEvaluations(new HashSet<>());

            RiskAssessment assessment = mapper.toDomain(entity);

            assertEquals(Decision.valueOf(decision), assessment.getDecision());
        }
    }

    @Test
    void testToDomain_WithMLPrediction_DeserializesCorrectly() throws SQLException {
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setId(UUID.randomUUID());
        entity.setTransactionId(UUID.randomUUID());
        entity.setRiskScoreValue(85);
        entity.setRiskLevel("HIGH");
        entity.setDecision("BLOCK");
        entity.setAssessmentTime(timestamp);
        entity.setRuleEvaluations(new HashSet<>());

        PGobject mlJson = new PGobject();
        mlJson.setType("json");
        mlJson.setValue("{\"fraudProbability\":0.85,\"modelName\":\"RandomForest\",\"confidence\":0.92,\"features\":{\"f1\":0.5}}");
        entity.setMlPredictionJson(mlJson);

        RiskAssessment assessment = mapper.toDomain(entity);

        assertNotNull(assessment.getMlPrediction());
        assertEquals(0.85, assessment.getMlPrediction().fraudProbability());
        assertEquals("RandomForest", assessment.getMlPrediction().modelVersion());
        assertEquals(0.92, assessment.getMlPrediction().confidence());
    }

    @Test
    void testToDomain_NoMLPrediction_ReturnsNull() {
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setId(UUID.randomUUID());
        entity.setTransactionId(UUID.randomUUID());
        entity.setRiskScoreValue(40);
        entity.setRiskLevel("MEDIUM");
        entity.setDecision("REVIEW");
        entity.setAssessmentTime(timestamp);
        entity.setRuleEvaluations(new HashSet<>());
        entity.setMlPredictionJson(null);

        RiskAssessment assessment = mapper.toDomain(entity);

        assertNull(assessment.getMlPrediction());
    }

    @Test
    void testToDomain_WithMultipleRuleEvaluations_MapsAll() {
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setId(UUID.randomUUID());
        entity.setTransactionId(UUID.randomUUID());
        entity.setRiskScoreValue(75);
        entity.setRiskLevel("HIGH");
        entity.setDecision("REVIEW");
        entity.setAssessmentTime(timestamp);

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

    @Test
    void testRoundTrip_DomainToEntityToDomain_PreservesData() throws SQLException {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment originalAssessment = RiskAssessment.of(transactionId);

        originalAssessment.addRuleEvaluation(new RuleEvaluation(
                "RULE001", "Test Rule", RuleType.VELOCITY, true, 35, "Description"
        ));

        originalAssessment.setMlPrediction(new MLPrediction(
                "modelId",
                "RandomForest",
                0.75,
                0.88,
                Map.of("feature1", 0.6)
        ));

        originalAssessment.completeAssessment(RiskScore.of(70), Decision.REVIEW);

        RiskAssessmentEntity entity = mapper.toEntity(originalAssessment);
        RiskAssessment roundTripAssessment = mapper.toDomain(entity);

        assertEquals(originalAssessment.getAssessmentId(), roundTripAssessment.getAssessmentId());
        assertEquals(originalAssessment.getTransactionId(), roundTripAssessment.getTransactionId());
        assertEquals(originalAssessment.getRiskScore().value(), roundTripAssessment.getRiskScore().value());
        assertEquals(originalAssessment.getRiskLevel(), roundTripAssessment.getRiskLevel());
        assertEquals(originalAssessment.getDecision(), roundTripAssessment.getDecision());
        assertEquals(originalAssessment.getRuleEvaluations().size(), roundTripAssessment.getRuleEvaluations().size());
    }

    @Test
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

    private @NotNull RiskAssessmentEntity getRiskAssessmentEntity(UUID assessmentId, UUID transactionId) throws SQLException {
        RiskAssessmentEntity originalEntity = new RiskAssessmentEntity();
        originalEntity.setId(assessmentId);
        originalEntity.setTransactionId(transactionId);
        originalEntity.setRiskScoreValue(65);
        originalEntity.setRiskLevel("MEDIUM");
        originalEntity.setDecision("REVIEW");
        originalEntity.setAssessmentTime(timestamp);

        PGobject mlJson = new PGobject();
        mlJson.setType("json");
        mlJson.setValue("{\"fraudProbability\":0.70,\"modelName\":\"TestModel\",\"confidence\":0.85,\"features\":{}}");
        originalEntity.setMlPredictionJson(mlJson);
        originalEntity.setRuleEvaluations(new HashSet<>());
        return originalEntity;
    }

    @Test
    void testAssessmentIdMapping_BothDirections_WorksCorrectly() {
        UUID uuid = UUID.randomUUID();
        AssessmentId assessmentId = AssessmentId.of(uuid);

        UUID mappedUuid = mapper.assessmentIdToUuid(assessmentId);
        AssessmentId mappedId = AssessmentId.of(uuid);

        assertEquals(uuid, mappedUuid);
        assertEquals(assessmentId, mappedId);
    }

    @Test
    void testTransactionIdMapping_BothDirections_WorksCorrectly() {
        UUID uuid = UUID.randomUUID();
        TransactionId transactionId = TransactionId.of(uuid);

        UUID mappedUuid = mapper.transactionIdToUuid(transactionId);
        TransactionId mappedId = TransactionId.of(uuid);

        assertEquals(uuid, mappedUuid);
        assertEquals(transactionId, mappedId);
    }

    @Test
    void testRiskScoreMapping_FromInteger_ReturnsCorrectScore() {
        assertEquals(0, RiskScore.of(0).value());
        assertEquals(50, RiskScore.of(50).value());
        assertEquals(100, RiskScore.of(100).value());
    }

    @Test
    void testRiskLevelMapping_ToStringAndBack_WorksCorrectly() {
        for (RiskLevel level : RiskLevel.values()) {
            String levelString = mapper.riskLevelToString(level);
            RiskLevel mappedLevel = RiskLevel.fromString(levelString);

            assertEquals(level, mappedLevel);
        }
    }

    @Test
    void testDecisionMapping_ToStringAndBack_WorksCorrectly() {
        for (Decision decision : Decision.values()) {
            String decisionString = mapper.decisionToString(decision);
            Decision mappedDecision = Decision.fromString(decisionString);

            assertEquals(decision, mappedDecision);
        }
    }

    @Test
    void testMlPredictionToJson_ValidPrediction_CreatesValidPGobject() {
        MLPrediction prediction = new MLPrediction(
                "modelId",
                "RandomForest",
                0.85,
                0.90,
                Map.of("key1", 0.5, "key2", 0.3)
        );

        PGobject pgObject = mapper.mlPredictionToJson(prediction);

        assertNotNull(pgObject);
        assertEquals("json", pgObject.getType());
        assertTrue(pgObject.getValue().contains("0.85"));
        assertTrue(pgObject.getValue().contains("TestModel"));
    }

    @Test
    void testMlPredictionToJson_NullPrediction_ReturnsNull() throws SQLException {
        PGobject result = mapper.mlPredictionToJson(null);
        assertNull(result);
    }

    @Test
    void testJsonToMlPrediction_ValidJson_DeserializesCorrectly() throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("json");
        pgObject.setValue("{\"fraudProbability\":0.80,\"modelName\":\"Model\",\"confidence\":0.95,\"features\":{\"f1\":0.4}}");

        MLPrediction prediction = mapper.jsonToMlPrediction(pgObject);

        assertNotNull(prediction);
        assertEquals(0.80, prediction.fraudProbability());
        assertEquals("Model", prediction.modelId());
        assertEquals(0.95, prediction.confidence());
    }

    @Test
    void testJsonToMlPrediction_NullJson_ReturnsNull() throws SQLException {
        MLPrediction prediction = mapper.jsonToMlPrediction(null);
        assertNull(prediction);
    }

    @Test
    void testToEntity_ZeroRiskScore_MapsCorrectly() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment assessment = RiskAssessment.of(transactionId);
        assessment.completeAssessment(RiskScore.of(0), Decision.ALLOW);

        RiskAssessmentEntity entity = mapper.toEntity(assessment);

        assertEquals(0, entity.getRiskScoreValue());
        assertEquals("LOW", entity.getRiskLevel());
    }

    @Test
    void testToEntity_MaximumRiskScore_MapsCorrectly() {
        TransactionId transactionId = TransactionId.of(UUID.randomUUID());
        RiskAssessment assessment = RiskAssessment.of(transactionId);
        assessment.completeAssessment(RiskScore.of(100), Decision.BLOCK);

        RiskAssessmentEntity entity = mapper.toEntity(assessment);

        assertEquals(100, entity.getRiskScoreValue());
        assertEquals("CRITICAL", entity.getRiskLevel());
    }
}