package com.twenty9ine.frauddetection.domain.valueobject;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

public record EventId(UUID eventId) {

    public UUID toUUID() {
        return eventId;
    }

    public static EventId generate() {
        return new EventId(Generators.timeBasedEpochGenerator().generate());
    }

    public static EventId of(String eventId) {
        return new EventId(UUID.fromString(eventId));
    }

    public static EventId of(UUID eventId) {
        return new EventId(eventId);
    }

    @Override
    public String toString() {
        return eventId.toString();
    }
}
