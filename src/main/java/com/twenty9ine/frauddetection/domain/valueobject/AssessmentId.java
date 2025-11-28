package com.twenty9ine.frauddetection.domain.valueobject;

import java.util.UUID;

public record AssessmentId(UUID assessmentId) {

    public UUID toUUID() {
        return assessmentId;
    }

    public static AssessmentId generate() {
        return new AssessmentId(UUID.randomUUID());
    }

    public static AssessmentId of(String assessmentId) {
        return new AssessmentId(UUID.fromString(assessmentId));
    }

    public static AssessmentId of(UUID assessmentId) {
        return new AssessmentId(assessmentId);
    }
}
