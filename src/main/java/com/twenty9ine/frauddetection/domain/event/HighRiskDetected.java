package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.AssessmentId;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.time.Instant;

public record HighRiskDetected(TransactionId id, AssessmentId assessmentId,
                               TransactionRiskLevel transactionRiskLevel,
                               Instant occurredAt) implements DomainEvent<TransactionId> {

    public HighRiskDetected(TransactionId id, AssessmentId assessmentId, TransactionRiskLevel transactionRiskLevel) {
        this(id, assessmentId, transactionRiskLevel, Instant.now());
    }

    public static HighRiskDetected of(TransactionId id, AssessmentId assessmentId, TransactionRiskLevel transactionRiskLevel) {
        return new HighRiskDetected(id, assessmentId, transactionRiskLevel);
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
