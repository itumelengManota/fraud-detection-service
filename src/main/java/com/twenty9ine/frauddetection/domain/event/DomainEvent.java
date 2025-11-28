package com.twenty9ine.frauddetection.domain.event;

import com.twenty9ine.frauddetection.domain.valueobject.EventId;

import java.time.Instant;

public interface DomainEvent {
    EventId getEventId();
    Instant getOccurredAt();
    String getEventType();
}
