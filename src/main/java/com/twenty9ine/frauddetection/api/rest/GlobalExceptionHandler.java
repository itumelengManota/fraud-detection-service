package com.twenty9ine.frauddetection.api.rest;

import com.twenty9ine.frauddetection.api.rest.dto.ErrorResponse;
import com.twenty9ine.frauddetection.domain.exception.InvariantViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ConstraintViolationException exception) {

        log.warn("Validation error: {}", exception.getMessage());

        return ResponseEntity.badRequest()
                             .body(buildConstraintViolation(exception));
    }

    @ExceptionHandler(InvariantViolationException.class)
    public ResponseEntity<ErrorResponse> handleInvariantViolation(InvariantViolationException exception) {

        log.error("Business rule violation: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                             .body(buildBusinessRuleViolation(exception));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(buildInternalServerError());
    }

    private static ErrorResponse buildConstraintViolation(ConstraintViolationException ex) {
        return ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Invalid request data")
                .details(ex.getConstraintViolations().stream()
                        .map(ConstraintViolation::getMessage)
                        .toList())
                .timestamp(Instant.now())
                .build();
    }

    private static ErrorResponse buildInternalServerError() {
        return ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred")
                .timestamp(Instant.now())
                .build();
    }

    private static ErrorResponse buildBusinessRuleViolation(InvariantViolationException ex) {
        return ErrorResponse.builder()
                .code("BUSINESS_RULE_VIOLATION")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
}
