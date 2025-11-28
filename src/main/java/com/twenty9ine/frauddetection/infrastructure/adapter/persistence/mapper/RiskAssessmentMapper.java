package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RuleEvaluationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RiskAssessmentMapper {

    @Mapping(target = "id", source = "assessmentId")
    @Mapping(target = "transactionId", source = "transactionId", qualifiedByName = "transactionIdToUuid")
    @Mapping(target = "riskScoreValue", source = "riskScore.value")
    @Mapping(target = "riskLevel", source = "riskLevel", qualifiedByName = "riskLevelToString")
    @Mapping(target = "decision", source = "decision", qualifiedByName = "decisionToString")
    @Mapping(target = "mlPredictionJson", source = "mlPrediction", qualifiedByName = "mlPredictionToJson")
    @Mapping(target = "ruleEvaluations", source = "ruleEvaluations", qualifiedByName = "mapRuleEvaluations")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RiskAssessmentEntity toEntity(RiskAssessment domain);


    default RiskAssessment toDomain(RiskAssessmentEntity entity) {
        if (entity == null) return null;

        RiskAssessment assessment = new RiskAssessment(
            entity.getId(),
            TransactionId.of(entity.getTransactionId())
        );

        // Set ML prediction
        if (entity.getMlPredictionJson() != null) {
            assessment.setMlPrediction(jsonToMlPrediction(entity.getMlPredictionJson()));
        }

        // Add rule evaluations
        if (entity.getRuleEvaluations() != null) {
            entity.getRuleEvaluations().forEach(ruleEntity ->
                assessment.addRuleEvaluation(toRuleEvaluation(ruleEntity))
            );
        }

        // Complete the assessment with score and decision
        if (entity.getRiskScoreValue() != 0 && entity.getDecision() != null) {
            assessment.completeAssessment(
                new RiskScore(entity.getRiskScoreValue()),
                Decision.valueOf(entity.getDecision())
            );
        }

        // Clear events since they're already persisted
        assessment.clearDomainEvents();

        return assessment;
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

    @Named("mlPredictionToJson")
    default String mlPredictionToJson(MLPrediction prediction) {
        if (prediction == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(prediction);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize MLPrediction", e);
        }
    }

    @Named("jsonToMlPrediction")
    default MLPrediction jsonToMlPrediction(String json) {
        if (json == null) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, MLPrediction.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize MLPrediction", e);
        }
    }

    @Named("mapRuleEvaluations")
    default Set<RuleEvaluationEntity> mapRuleEvaluations(List<RuleEvaluation> evaluations) {
        if (evaluations == null) return new HashSet<>();
        return evaluations.stream()
            .map(this::toRuleEvaluationEntity)
            .collect(Collectors.toSet());
    }

    @Mapping(target = "id", ignore = true)
    RuleEvaluationEntity toRuleEvaluationEntity(RuleEvaluation evaluation);

    @Mapping(target = "ruleType", expression = "java(RuleType.valueOf(entity.getRuleType()))")
    @Mapping(target = "triggered", constant = "true")
    @Mapping(target = "ruleId", source = "id")
    RuleEvaluation toRuleEvaluation(RuleEvaluationEntity entity);
}
