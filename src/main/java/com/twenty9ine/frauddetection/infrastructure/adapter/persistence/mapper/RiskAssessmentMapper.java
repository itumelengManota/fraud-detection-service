package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RuleEvaluationEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.postgresql.util.PGobject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = RuleEvaluationMapper.class, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RiskAssessmentMapper {

    @Mapping(target = "id", source = "assessmentId", qualifiedByName = "assessmentIdToUuid")
    @Mapping(target = "transactionId", source = "transactionId", qualifiedByName = "transactionIdToUuid")
    @Mapping(target = "riskScoreValue", source = "riskScore.value")
    @Mapping(target = "riskLevel", source = "riskLevel", qualifiedByName = "riskLevelToString")
    @Mapping(target = "decision", source = "decision", qualifiedByName = "decisionToString")
    @Mapping(target = "mlPredictionJson", source = "mlPrediction", qualifiedByName = "mlPredictionToJson")
    @Mapping(target = "ruleEvaluations", source = "ruleEvaluations", qualifiedByName = "mapToEntitySet")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "revision", ignore = true)
    RiskAssessmentEntity toEntity(RiskAssessment domain);

    default RiskAssessment toDomain(RiskAssessmentEntity entity) {
        if (entity == null) return null;

        RiskAssessment assessment = new RiskAssessment(AssessmentId.of(entity.getId()), TransactionId.of(entity.getTransactionId()),
                new RiskScore(entity.getRiskScoreValue()), toRuleEvaluations(entity), jsonToMlPrediction(entity.getMlPredictionJson()));

//        if (entity.getMlPredictionJson() != null) {
//            assessment.setMlPrediction(jsonToMlPrediction(entity.getMlPredictionJson()));
//        }

//        if (entity.getRuleEvaluations() != null) {
//            entity.getRuleEvaluations().forEach(ruleEntity -> assessment.addRuleEvaluation(buildRuleEvaluation(ruleEntity)));
//            toRuleEvaluations(entity);
//        }

        if (entity.getRiskScoreValue() != 0 && entity.getDecision() != null) {
            assessment.completeAssessment(
                    Decision.valueOf(entity.getDecision())
            );
        }

        assessment.clearDomainEvents();
        return assessment;
    }

    private static List<RuleEvaluation> toRuleEvaluations(RiskAssessmentEntity entity) {
        return entity.getRuleEvaluations().stream()
                .map(RiskAssessmentMapper::buildRuleEvaluation)
                .toList();
    }

    private static RuleEvaluation buildRuleEvaluation(RuleEvaluationEntity ruleEntity) {
        return RuleEvaluation.builder()
                .ruleId(getRuleId(ruleEntity.getId()))
                .ruleName(ruleEntity.getRuleName())
                .ruleType(RuleType.valueOf(ruleEntity.getRuleType()))
                .triggered(true)
                .scoreImpact(ruleEntity.getScoreImpact())
                .description(ruleEntity.getDescription())
                .build();
    }

    private static String getRuleId(Long id) {
        return id != null ? id.toString() : null;
    }

    @Named("riskLevelToString")
    default String riskLevelToString(RiskLevel level) {
        return level != null ? level.name() : null;
    }

    @Named("decisionToString")
    default String decisionToString(Decision decision) {
        return decision != null ? decision.name() : null;
    }

    @Named("transactionIdToUuid")
    default UUID transactionIdToUuid(TransactionId transactionId) {
        return transactionId != null ? transactionId.toUUID() : null;
    }

    @Named("assessmentIdToUuid")
    default UUID assessmentIdToUuid(AssessmentId assessmentId) {
        return assessmentId != null ? assessmentId.toUUID() : null;
    }

    @Named("jsonToMlPrediction")
    default MLPrediction jsonToMlPrediction(PGobject json) {
        if (json == null || json.getValue() == null) {
            return null;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode node = objectMapper.readTree(json.getValue());

            return new MLPrediction(
                    node.get("modelId").asText(),
                    node.get("modelVersion").asText(),
                    node.get("fraudProbability").asDouble(),
                    node.get("confidence").asDouble(),
                    objectMapper.convertValue(
                            node.get("featureImportance"),
                            new TypeReference<Map<String, Double>>() {}
                    )
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize MLPrediction", e);
        }
    }

    @Named("mlPredictionToJson")
    default PGobject mlPredictionToJson(MLPrediction prediction) {
        if (prediction == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> json = Map.of(
                    "modelId", prediction.modelId(),
                    "modelVersion", prediction.modelVersion(),
                    "fraudProbability", prediction.fraudProbability(),
                    "confidence", prediction.confidence(),
                    "featureImportance", prediction.featureImportance()
            );

            pgObject.setValue(objectMapper.writeValueAsString(json));
            return pgObject;
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize MLPrediction", e);
        }
    }
}