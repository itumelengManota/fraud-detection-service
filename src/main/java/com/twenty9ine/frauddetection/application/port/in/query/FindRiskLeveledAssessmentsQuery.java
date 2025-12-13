package com.twenty9ine.frauddetection.application.port.in.query;

import jakarta.validation.constraints.PastOrPresent;
import lombok.Builder;

import java.time.Instant;
import java.util.Set;

/**
 * Query object for searching risk assessments by risk levels and/or assessment time.
 * Both fields are optional - null or empty values will be ignored in the search.
 */
@Builder
public record FindRiskLeveledAssessmentsQuery(
        Set<String> transactionRiskLevels,

        @PastOrPresent(message = "fromDate cannot be in the future")
        Instant fromDate
) {
}