package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.postgresql.util.PGobject;

@Builder
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

    @NotNull
    @CreatedDate
    private Instant createdAt;

    @NotNull
    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private int revision;

    @Builder.Default
    @MappedCollection(idColumn = "assessment_id")
    private Set<RuleEvaluationEntity> ruleEvaluations = new HashSet<>();
}
