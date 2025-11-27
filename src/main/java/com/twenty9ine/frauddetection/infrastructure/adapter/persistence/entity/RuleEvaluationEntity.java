package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("rule_evaluations")
public class RuleEvaluationEntity {

    @Id
    private Long id;

    @Column("rule_name")
    private String ruleName;

    @Column("rule_type")
    private String ruleType;

    @Column("score_impact")
    private int scoreImpact;

    @Column("description")
    private String description;
}
