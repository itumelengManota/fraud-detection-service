package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.time.Instant;
import java.util.UUID;

public record HighRiskDetected(
    UUID eventId,
    UUID assessmentId,
    TransactionId transactionId,
    RiskLevel riskLevel,
    Instant occurredAt
) implements DomainEvent {

    public HighRiskDetected(UUID assessmentId,
                           TransactionId transactionId,
                           RiskLevel riskLevel,
                           Instant occurredAt) {
        this(
            UUID.randomUUID(),
            assessmentId,
            transactionId,
            riskLevel,
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
        return "HighRiskDetected";
    }
}
