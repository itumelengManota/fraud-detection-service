package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.AssessmentId;
import com.twenty9ine.frauddetection.domain.valueobject.EventId;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.time.Instant;

public record HighRiskDetected(EventId eventId, AssessmentId assessmentId, TransactionId transactionId,
                               RiskLevel riskLevel,
                               Instant occurredAt) implements DomainEvent {

    public HighRiskDetected(AssessmentId assessmentId, TransactionId transactionId, RiskLevel riskLevel) {
        this(assessmentId, transactionId, riskLevel, Instant.now());
    }

    public HighRiskDetected(AssessmentId assessmentId, TransactionId transactionId, RiskLevel riskLevel, Instant occurredAt) {
        this(EventId.generate(), assessmentId, transactionId, riskLevel, occurredAt);
    }

    public static HighRiskDetected of(AssessmentId assessmentId, TransactionId transactionId, RiskLevel riskLevel) {
        return new HighRiskDetected(assessmentId, transactionId, riskLevel);
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
        return HighRiskDetected.class.getSimpleName();
    }
}
