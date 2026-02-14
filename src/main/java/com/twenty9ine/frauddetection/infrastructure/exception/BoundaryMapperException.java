package com.twenty9ine.frauddetection.infrastructure.exception;

public class BoundaryMapperException extends RuntimeException {
    public BoundaryMapperException(String message) {
        super(message);
    }
    public BoundaryMapperException(String message, Exception exception) {
        super(message, exception);
    }
}
