package com.twenty9ine.frauddetection.application.port.in.query;

import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;

/**
 * Query for finding risk assessments by risk level and time period.
 *
 * Encapsulates search criteria for retrieving risk assessments.
 * Supports filtering by risk level and temporal boundaries.
 *
 * @author Fraud Detection Team
 */
@Builder
public record FindHighRiskAssessmentsQuery(
        @NotNull(message = "Risk level cannot be null")
        RiskLevel riskLevel,

        @NotNull(message = "Since transactionTimestamp cannot be null")
        Instant since
) {
}