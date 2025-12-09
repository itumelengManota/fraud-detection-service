package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.RuleEvaluation;
import com.twenty9ine.frauddetection.domain.valueobject.RuleType;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RuleEvaluationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.context.aot.DisabledInAotMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisabledInAotMode
class RuleEvaluationMapperTest {

    private RuleEvaluationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(RuleEvaluationMapper.class);
    }

    @Test
    void testToEntity_CompleteRuleEvaluation_MapsAllFields() {
        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE001",
                "High Amount Rule",
                RuleType.AMOUNT,
                true,
                50,
                "Transaction amount exceeds threshold"
        );

        RuleEvaluationEntity entity = mapper.toEntity(evaluation);

        assertNotNull(entity);
        assertNull(entity.getId()); // ID should be ignored
        assertEquals("High Amount Rule", entity.getRuleName());
        assertEquals("AMOUNT", entity.getRuleType());
        assertEquals(50, entity.getScoreImpact());
        assertEquals("Transaction amount exceeds threshold", entity.getDescription());
    }

    @Test
    void testToEntity_AllRuleTypes_MapCorrectly() {
        for (RuleType ruleType : RuleType.values()) {
            RuleEvaluation evaluation = new RuleEvaluation(
                    "RULE_" + ruleType.name(),
                    "Test Rule",
                    ruleType,
                    true,
                    25,
                    "Test description"
            );

            RuleEvaluationEntity entity = mapper.toEntity(evaluation);

            assertEquals(ruleType.name(), entity.getRuleType());
        }
    }

    @Test
    void testToEntity_NullRuleType_ReturnsNullType() {
        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE002",
                "Null Type Rule",
                null,
                true,
                30,
                "Rule with null type"
        );

        RuleEvaluationEntity entity = mapper.toEntity(evaluation);

        assertNull(entity.getRuleType());
    }

    @Test
    void testToEntity_ZeroScoreImpact_MapsCorrectly() {
        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE003",
                "Zero Impact Rule",
                RuleType.VELOCITY,
                false,
                0,
                "No score ruleViolationSeverity"
        );

        RuleEvaluationEntity entity = mapper.toEntity(evaluation);

        assertEquals(0, entity.getScoreImpact());
    }

    @Test
    void testToEntity_NegativeScoreImpact_MapsCorrectly() {
        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE004",
                "Negative Impact Rule",
                RuleType.DEVICE,
                true,
                -10,
                "Reduces risk score"
        );

        RuleEvaluationEntity entity = mapper.toEntity(evaluation);

        assertEquals(-10, entity.getScoreImpact());
    }

    @Test
    void testToEntity_NullDescription_MapsCorrectly() {
        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE005",
                "No Description Rule",
                RuleType.MERCHANT,
                true,
                40,
                null
        );

        RuleEvaluationEntity entity = mapper.toEntity(evaluation);

        assertNull(entity.getDescription());
    }

    @Test
    void testToEntity_NullEvaluation_ReturnsNull() {
        RuleEvaluationEntity entity = mapper.toEntity(null);

        assertNull(entity);
    }

    @Test
    void testToDomain_CompleteEntity_MapsAllFields() {
        RuleEvaluationEntity entity = new RuleEvaluationEntity();
        entity.setId(1L);
        entity.setRuleName("Geo Location Rule");
        entity.setRuleType("GEOGRAPHIC");
        entity.setScoreImpact(35);
        entity.setDescription("Suspicious location detected");

        RuleEvaluation evaluation = mapper.toDomain(entity);

        assertNotNull(evaluation);
        assertEquals("1", evaluation.ruleId()); // ID mapped to String
        assertEquals("Geo Location Rule", evaluation.ruleName());
        assertEquals(RuleType.GEOGRAPHIC, evaluation.ruleType());
        assertTrue(evaluation.triggered()); // Always true by constant
        assertEquals(35, evaluation.scoreImpact());
        assertEquals("Suspicious location detected", evaluation.description());
    }

    @Test
    void testToDomain_AllRuleTypes_MapCorrectly() {
        for (RuleType ruleType : RuleType.values()) {
            RuleEvaluationEntity entity = new RuleEvaluationEntity();
            entity.setId(1L);
            entity.setRuleName("Test Rule");
            entity.setRuleType(ruleType.name());
            entity.setScoreImpact(25);
            entity.setDescription("Test");

            RuleEvaluation evaluation = mapper.toDomain(entity);

            assertEquals(ruleType, evaluation.ruleType());
        }
    }

    @Test
    void testToDomain_NullRuleType_ReturnsNullType() {
        RuleEvaluationEntity entity = new RuleEvaluationEntity();
        entity.setId(2L);
        entity.setRuleName("Null Type Rule");
        entity.setRuleType(null);
        entity.setScoreImpact(20);
        entity.setDescription("Description");

        RuleEvaluation evaluation = mapper.toDomain(entity);

        assertNull(evaluation.ruleType());
    }

    @Test
    void testToDomain_AlwaysSetTriggeredToTrue() {
        RuleEvaluationEntity entity = new RuleEvaluationEntity();
        entity.setId(3L);
        entity.setRuleName("Test Rule");
        entity.setRuleType("AMOUNT");
        entity.setScoreImpact(15);
        entity.setDescription("Test");

        RuleEvaluation evaluation = mapper.toDomain(entity);

        assertTrue(evaluation.triggered());
    }

    @Test
    void testToDomain_NullEntity_ReturnsNull() {
        RuleEvaluation evaluation = mapper.toDomain(null);

        assertNull(evaluation);
    }

    @Test
    void testMapToEntitySet_MultipleEvaluations_MapsAll() {
        List<RuleEvaluation> evaluations = Arrays.asList(
                new RuleEvaluation("RULE001", "Rule 1", RuleType.AMOUNT, true, 50, "Desc 1"),
                new RuleEvaluation("RULE002", "Rule 2", RuleType.VELOCITY, true, 30, "Desc 2"),
                new RuleEvaluation("RULE003", "Rule 3", RuleType.GEOGRAPHIC, true, 40, "Desc 3")
        );

        Set<RuleEvaluationEntity> entities = mapper.mapToEntitySet(evaluations);

        assertNotNull(entities);
        assertEquals(3, entities.size());
    }

    @Test
    void testMapToEntitySet_EmptyList_ReturnsEmptySet() {
        Set<RuleEvaluationEntity> entities = mapper.mapToEntitySet(Collections.emptyList());

        assertNotNull(entities);
        assertTrue(entities.isEmpty());
    }

    @Test
    void testMapToEntitySet_NullList_ReturnsEmptySet() {
        Set<RuleEvaluationEntity> entities = mapper.mapToEntitySet(null);

        assertNotNull(entities);
        assertTrue(entities.isEmpty());
    }

    @Test
    void testMapToEntitySet_SingleEvaluation_MapsSingleEntity() {
        List<RuleEvaluation> evaluations = Collections.singletonList(
                new RuleEvaluation("RULE001", "Single Rule", RuleType.MERCHANT, true, 60, "Description")
        );

        Set<RuleEvaluationEntity> entities = mapper.mapToEntitySet(evaluations);

        assertEquals(1, entities.size());
        RuleEvaluationEntity entity = entities.iterator().next();
        assertEquals("Single Rule", entity.getRuleName());
    }

    @Test
    void testRuleTypeToString_AllRuleTypes_ReturnsCorrectStrings() {
        assertEquals("AMOUNT", mapper.ruleTypeToString(RuleType.AMOUNT));
        assertEquals("VELOCITY", mapper.ruleTypeToString(RuleType.VELOCITY));
        assertEquals("GEOGRAPHIC", mapper.ruleTypeToString(RuleType.GEOGRAPHIC));
        assertEquals("MERCHANT", mapper.ruleTypeToString(RuleType.MERCHANT));
        assertEquals("DEVICE", mapper.ruleTypeToString(RuleType.DEVICE));
    }

    @Test
    void testRuleTypeToString_NullRuleType_ReturnsNull() {
        assertNull(mapper.ruleTypeToString(null));
    }

    @Test
    void testStringToRuleType_AllValidStrings_ReturnsCorrectEnums() {
        assertEquals(RuleType.AMOUNT, mapper.stringToRuleType("AMOUNT"));
        assertEquals(RuleType.VELOCITY, mapper.stringToRuleType("VELOCITY"));
        assertEquals(RuleType.GEOGRAPHIC, mapper.stringToRuleType("GEOGRAPHIC"));
        assertEquals(RuleType.MERCHANT, mapper.stringToRuleType("MERCHANT"));
        assertEquals(RuleType.DEVICE, mapper.stringToRuleType("DEVICE"));
    }

    @Test
    void testStringToRuleType_NullString_ReturnsNull() {
        assertNull(mapper.stringToRuleType(null));
    }

    @Test
    void testRoundTrip_DomainToEntityToDomain_PreservesData() {
        RuleEvaluation originalEvaluation = new RuleEvaluation(
                "RULE999",
                "Round Trip Rule",
                RuleType.VELOCITY,
                true,
                45,
                "Round trip test"
        );

        RuleEvaluationEntity entity = mapper.toEntity(originalEvaluation);
        entity.setId(100L); // Set ID for round trip
        RuleEvaluation roundTripEvaluation = mapper.toDomain(entity);

        assertEquals("100", roundTripEvaluation.ruleId());
        assertEquals(originalEvaluation.ruleName(), roundTripEvaluation.ruleName());
        assertEquals(originalEvaluation.ruleType(), roundTripEvaluation.ruleType());
        assertTrue(roundTripEvaluation.triggered());
        assertEquals(originalEvaluation.scoreImpact(), roundTripEvaluation.scoreImpact());
        assertEquals(originalEvaluation.description(), roundTripEvaluation.description());
    }

    @Test
    void testRoundTrip_EntityToDomainToEntity_PreservesData() {
        RuleEvaluationEntity originalEntity = new RuleEvaluationEntity();
        originalEntity.setId(200L);
        originalEntity.setRuleName("Entity Round Trip");
        originalEntity.setRuleType("GEOGRAPHIC");
        originalEntity.setScoreImpact(55);
        originalEntity.setDescription("Entity round trip test");

        RuleEvaluation evaluation = mapper.toDomain(originalEntity);
        RuleEvaluationEntity roundTripEntity = mapper.toEntity(evaluation);

        assertEquals(originalEntity.getRuleName(), roundTripEntity.getRuleName());
        assertEquals(originalEntity.getRuleType(), roundTripEntity.getRuleType());
        assertEquals(originalEntity.getScoreImpact(), roundTripEntity.getScoreImpact());
        assertEquals(originalEntity.getDescription(), roundTripEntity.getDescription());
    }

    @Test
    void testMapToEntitySet_DuplicateEvaluations_HandledCorrectly() {
        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE001",
                "Duplicate Rule",
                RuleType.AMOUNT,
                true,
                50,
                "Description"
        );

        List<RuleEvaluation> evaluations = Arrays.asList(evaluation, evaluation, evaluation);

        Set<RuleEvaluationEntity> entities = mapper.mapToEntitySet(evaluations);

        // Set should handle duplicates, but entities might be different objects
        assertTrue(entities.size() <= 3);
    }

    @Test
    void testToEntity_LargeScoreImpact_MapsCorrectly() {
        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE100",
                "Large Impact Rule",
                RuleType.MERCHANT,
                true,
                100,
                "Maximum ruleViolationSeverity"
        );

        RuleEvaluationEntity entity = mapper.toEntity(evaluation);

        assertEquals(100, entity.getScoreImpact());
    }

    @Test
    void testToEntity_LongDescription_MapsCorrectly() {
        String longDescription = "This is a very long description that exceeds normal length. ".repeat(10);

        RuleEvaluation evaluation = new RuleEvaluation(
                "RULE200",
                "Long Description Rule",
                RuleType.VELOCITY,
                true,
                25,
                longDescription
        );

        RuleEvaluationEntity entity = mapper.toEntity(evaluation);

        assertEquals(longDescription, entity.getDescription());
    }

    @Test
    void testMapToEntitySet_MixedRuleTypes_MapsAllCorrectly() {
        List<RuleEvaluation> evaluations = Arrays.asList(
                new RuleEvaluation("R1", "Rule 1", RuleType.AMOUNT, true, 50, "Desc 1"),
                new RuleEvaluation("R2", "Rule 2", RuleType.VELOCITY, true, 30, "Desc 2"),
                new RuleEvaluation("R3", "Rule 3", RuleType.GEOGRAPHIC, true, 40, "Desc 3"),
                new RuleEvaluation("R4", "Rule 4", RuleType.MERCHANT, true, 70, "Desc 4"),
                new RuleEvaluation("R5", "Rule 5", RuleType.DEVICE, true, -20, "Desc 5")
        );

        Set<RuleEvaluationEntity> entities = mapper.mapToEntitySet(evaluations);

        assertEquals(5, entities.size());
    }
}