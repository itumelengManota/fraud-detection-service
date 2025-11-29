package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("rule_evaluations")
public class RuleEvaluationEntity {

    @Id
    private Long id;
    private String ruleName;
    private String ruleType;
    private int scoreImpact;
    private String description;
}
