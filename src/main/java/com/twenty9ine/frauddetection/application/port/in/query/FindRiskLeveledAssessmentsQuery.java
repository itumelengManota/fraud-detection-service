package com.twenty9ine.frauddetection.application.port.in.query;

import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;

/**
 * Query for finding risk assessments by risk level and time period.
 *
 * Encapsulates search criteria for retrieving risk assessments.
 * Supports filtering by risk level and temporal boundaries.
 *
 * @author Ignatius Itumeleng Manota
 */
@Builder
public record FindRiskLeveledAssessmentsQuery(
        Set<TransactionRiskLevel> transactionRiskLevels,

        @PastOrPresent(message = "From timestamp cannot be in the future")
        @NotNull(message = "From transactionTimestamp cannot be null")
        Instant from
) {
}