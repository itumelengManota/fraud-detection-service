package com.twenty9ine.frauddetection.domain.event;

import java.time.Instant;

public interface DomainEvent<T> {
    T getEventId();
    Instant getOccurredAt();
    String getEventType();
}
