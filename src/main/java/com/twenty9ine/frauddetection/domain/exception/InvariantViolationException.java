package com.twenty9ine.frauddetection.domain.exception;

public class InvariantViolationException extends RuntimeException {
    public InvariantViolationException(String message) {
        super(message);
    }
}
