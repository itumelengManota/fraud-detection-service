package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.domain.valueobject.RiskScore;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.time.Instant;
import java.util.UUID;

public record RiskAssessmentCompleted(
    UUID eventId,
    UUID assessmentId,
    TransactionId transactionId,
    RiskScore finalScore,
    RiskLevel riskLevel,
    Decision decision,
    Instant occurredAt
) implements DomainEvent {

    public RiskAssessmentCompleted(UUID assessmentId,
                                  TransactionId transactionId,
                                  RiskScore finalScore,
                                  RiskLevel riskLevel,
                                  Decision decision,
                                  Instant occurredAt) {
        this(
            UUID.randomUUID(),
            assessmentId,
            transactionId,
            finalScore,
            riskLevel,
            decision,
            occurredAt
        );
    }

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getEventType() {
        return "RiskAssessmentCompleted";
    }
}
