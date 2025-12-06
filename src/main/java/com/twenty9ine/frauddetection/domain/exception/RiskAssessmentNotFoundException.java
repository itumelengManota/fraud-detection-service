package com.twenty9ine.frauddetection.domain.exception;

public class RiskAssessmentNotFoundException extends RuntimeException {
    public RiskAssessmentNotFoundException(String message) {
        super(message);
    }

    public RiskAssessmentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
