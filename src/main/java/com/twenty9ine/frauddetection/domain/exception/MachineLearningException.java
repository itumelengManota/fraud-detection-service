package com.twenty9ine.frauddetection.domain.exception;

public class MachineLearningException extends RuntimeException {
    public MachineLearningException(String message) {
        super(message);
    }

    public MachineLearningException(String message, Throwable cause) {
        super(message, cause);
    }
}
