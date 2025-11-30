package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.RuleEvaluation;
import com.twenty9ine.frauddetection.domain.valueobject.RuleType;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RuleEvaluationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RuleEvaluationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ruleType", source = "ruleType", qualifiedByName = "ruleTypeToString")
    RuleEvaluationEntity toEntity(RuleEvaluation evaluation);

    @Mapping(target = "ruleId", source = "id")
    @Mapping(target = "ruleType", source = "ruleType", qualifiedByName = "stringToRuleType")
    @Mapping(target = "triggered", constant = "true")
    RuleEvaluation toDomain(RuleEvaluationEntity entity);

    @Named("mapToEntitySet")
    default Set<RuleEvaluationEntity> mapToEntitySet(List<RuleEvaluation> evaluations) {
        if (evaluations == null) return new HashSet<>();
        return evaluations.stream()
                .map(this::toEntity)
                .collect(Collectors.toSet());
    }

    @Named("ruleTypeToString")
    default String ruleTypeToString(RuleType ruleType) {
        return ruleType != null ? ruleType.name() : null;
    }

    @Named("stringToRuleType")
    default RuleType stringToRuleType(String ruleType) {
        return ruleType != null ? RuleType.valueOf(ruleType) : null;
    }
}