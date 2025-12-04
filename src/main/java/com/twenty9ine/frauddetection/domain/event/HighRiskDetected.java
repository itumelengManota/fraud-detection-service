package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.AssessmentId;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.time.Instant;

public record HighRiskDetected(TransactionId id, AssessmentId assessmentId,
                               RiskLevel riskLevel,
                               Instant occurredAt) implements DomainEvent<TransactionId> {

    public HighRiskDetected(TransactionId id, AssessmentId assessmentId, RiskLevel riskLevel) {
        this(id, assessmentId, riskLevel, Instant.now());
    }

    public static HighRiskDetected of(TransactionId id, AssessmentId assessmentId, RiskLevel riskLevel) {
        return new HighRiskDetected(id, assessmentId, riskLevel);
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
        return HighRiskDetected.class.getSimpleName();
    }
}
