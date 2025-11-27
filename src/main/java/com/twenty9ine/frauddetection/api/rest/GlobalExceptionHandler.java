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
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ConstraintViolationException ex) {

        log.warn("Validation error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Invalid request data")
            .details(ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList()))
            .timestamp(Instant.now())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(InvariantViolationException.class)
    public ResponseEntity<ErrorResponse> handleInvariantViolation(
            InvariantViolationException ex) {

        log.error("Business rule violation: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
            .code("BUSINESS_RULE_VIOLATION")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .build();
return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
}

@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    log.error("Unexpected error", ex);

    ErrorResponse error = ErrorResponse.builder()
        .code("INTERNAL_SERVER_ERROR")
        .message("An unexpected error occurred")
        .timestamp(Instant.now())
        .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
}
}
