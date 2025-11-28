package com.twenty9ine.frauddetection.domain.valueobject;

public enum Decision {
    ALLOW,
    CHALLENGE,
    REVIEW,
    BLOCK;

    public boolean isAllowed() {
        return this == ALLOW;
    }

    public boolean isChallenged() {
        return this == CHALLENGE;
    }

    public boolean isUnderReview() {
        return this == REVIEW;
    }

    public boolean isBlocked() {
        return this == BLOCK;
    }

    public boolean isProceed() {
        return this != BLOCK;
    }
}