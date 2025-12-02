package com.twenty9ine.frauddetection.application.port.in.query;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Query for retrieving a risk assessment by transaction ID.
 *
 * Simple query object following CQRS pattern. Contains only the minimum
 * information required to locate a risk assessment.
 *
 * @author Fraud Detection Team
 */
public record GetRiskAssessmentQuery(
        @NotNull(message = "Transaction ID cannot be null")
        UUID transactionId
) {
}