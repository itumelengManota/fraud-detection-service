package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.*;

import java.time.Instant;

public record RiskAssessmentCompleted(EventId eventId, AssessmentId assessmentId, TransactionId transactionId,
                                      RiskScore finalScore, RiskLevel riskLevel, Decision decision,
                                      Instant occurredAt) implements DomainEvent {

    public RiskAssessmentCompleted(AssessmentId assessmentId, TransactionId transactionId, RiskScore finalScore,
                                   RiskLevel riskLevel, Decision decision) {
        this(assessmentId, transactionId, finalScore, riskLevel, decision, Instant.now());
    }

    public RiskAssessmentCompleted(AssessmentId assessmentId, TransactionId transactionId, RiskScore finalScore,
                                   RiskLevel riskLevel, Decision decision, Instant occurredAt) {
        this(EventId.generate(), assessmentId, transactionId, finalScore, riskLevel, decision, occurredAt);
    }

    public static RiskAssessmentCompleted of(AssessmentId assessmentId, TransactionId transactionId,
                                             RiskScore finalScore, RiskLevel riskLevel, Decision decision) {
        return new RiskAssessmentCompleted(assessmentId, transactionId, finalScore, riskLevel, decision);
    }

    @Override
    public EventId getEventId() {
        return eventId;
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
