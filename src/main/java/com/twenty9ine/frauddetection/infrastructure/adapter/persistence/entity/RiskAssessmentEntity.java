package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.postgresql.util.PGobject;

@Data
@Table("risk_assessments")
public class RiskAssessmentEntity {

    @Id
    private UUID id;
    private UUID transactionId;
    private int riskScoreValue;
    private String riskLevel;
    private String decision;
    private PGobject mlPredictionJson;
    private Instant assessmentTime;
    private Instant createdAt;
    private Instant updatedAt;

    @MappedCollection(idColumn = "assessment_id")
    private Set<RuleEvaluationEntity> ruleEvaluations = new HashSet<>();
}
