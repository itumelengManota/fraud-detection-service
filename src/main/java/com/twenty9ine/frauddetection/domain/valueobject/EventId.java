package com.twenty9ine.frauddetection.domain.valueobject;

import java.util.UUID;

public record EventId(UUID eventId) {

    public UUID toUUID() {
        return eventId;
    }

    public static EventId generate() {
        return new EventId(UUID.randomUUID());
    }

    public static EventId of(String eventId) {
        return new EventId(UUID.fromString(eventId));
    }

    public static EventId of(UUID eventId) {
        return new EventId(eventId);
    }
}
