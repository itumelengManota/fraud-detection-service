package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Table("risk_assessments")
public class RiskAssessmentEntity {

    @Id
    private UUID id;

    @Column("transaction_id")
    private UUID transactionId;

    @Column("risk_score_value")
    private int riskScoreValue;

    @Column("risk_level")
    private String riskLevel;

    @Column("decision")
    private String decision;

    @Column("ml_prediction_json")
    private String mlPredictionJson;

    @Column("assessment_time")
    private Instant assessmentTime;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @MappedCollection(idColumn = "assessment_id")
    private Set<RuleEvaluationEntity> ruleEvaluations = new HashSet<>();
}
