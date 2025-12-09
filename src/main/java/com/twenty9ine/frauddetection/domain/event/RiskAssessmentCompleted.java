package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.*;

import java.time.Instant;

public record RiskAssessmentCompleted(TransactionId id, AssessmentId assessmentId,
                                      RiskScore finalScore, TransactionRiskLevel transactionRiskLevel, Decision decision,
                                      Instant occurredAt) implements DomainEvent<TransactionId> {

    public RiskAssessmentCompleted(TransactionId id, AssessmentId assessmentId, RiskScore finalScore,
                                   TransactionRiskLevel transactionRiskLevel, Decision decision) {
        this(id, assessmentId, finalScore, transactionRiskLevel, decision, Instant.now());
    }

    public static RiskAssessmentCompleted of(TransactionId id, AssessmentId assessmentId,
                                             RiskScore finalScore, TransactionRiskLevel transactionRiskLevel, Decision decision) {
        return new RiskAssessmentCompleted(id, assessmentId, finalScore, transactionRiskLevel, decision);
    }

    @Override
    public TransactionId getEventId() {
        return id;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getEventType() {
        return RiskAssessmentCompleted.class.getSimpleName();
    }
}
