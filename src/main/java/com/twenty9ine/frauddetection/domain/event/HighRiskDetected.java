package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.model.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record HighRiskDetected(
    UUID eventId,
    UUID assessmentId,
    UUID transactionId,
    RiskLevel riskLevel,
    Instant occurredAt
) implements DomainEvent {

    public HighRiskDetected(UUID assessmentId,
                           UUID transactionId,
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
