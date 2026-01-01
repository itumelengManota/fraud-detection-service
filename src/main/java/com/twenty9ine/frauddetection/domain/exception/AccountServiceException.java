package com.twenty9ine.frauddetection.domain.exception;

public class AccountServiceException extends RuntimeException {
    private final int statusCode;

    public AccountServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
