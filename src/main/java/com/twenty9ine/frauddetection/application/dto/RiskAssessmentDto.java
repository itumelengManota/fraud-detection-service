package com.twenty9ine.frauddetection.application.dto;

import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RiskAssessmentDto(
    UUID assessmentId,
    UUID transactionId,
    int riskScore,
    TransactionRiskLevel transactionRiskLevel,
    Decision decision,
    Instant assessmentTime
) {

    public static RiskAssessmentDto from(RiskAssessment assessment) {
        return RiskAssessmentDto.builder()
            .assessmentId(assessment.getAssessmentId().toUUID())
            .transactionId(assessment.getTransactionId().toUUID())
            .riskScore(assessment.getRiskScore().value())
            .transactionRiskLevel(assessment.getTransactionRiskLevel())
            .decision(assessment.getDecision())
            .assessmentTime(assessment.getAssessmentTime())
            .build();
    }
}
