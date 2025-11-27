package com.twenty9ine.frauddetection.application.dto;

import com.twenty9ine.frauddetection.domain.model.Decision;
import com.twenty9ine.frauddetection.domain.model.RiskAssessment;
import com.twenty9ine.frauddetection.domain.model.RiskLevel;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RiskAssessmentDto(
    UUID assessmentId,
    UUID transactionId,
    int riskScore,
    RiskLevel riskLevel,
    Decision decision,
    Instant assessmentTime
) {
    public static RiskAssessmentDto from(RiskAssessment assessment) {
        return RiskAssessmentDto.builder()
            .assessmentId(assessment.getAssessmentId())
            .transactionId(assessment.getTransactionId())
            .riskScore(assessment.getRiskScore().value())
            .riskLevel(assessment.getRiskLevel())
            .decision(assessment.getDecision())
            .assessmentTime(assessment.getAssessmentTime())
            .build();
    }
}
