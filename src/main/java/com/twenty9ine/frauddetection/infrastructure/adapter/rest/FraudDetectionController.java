package com.twenty9ine.frauddetection.infrastructure.adapter.rest;

import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.*;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.query.FindHighRiskAssessmentsQuery;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST adapter for fraud detection use cases.
 *
 * Translates HTTP requests into use case commands/queries and delegates
 * to input ports. Follows Hexagonal Architecture by depending on
 * port interfaces rather than concrete implementations.
 *
 * @author Fraud Detection Team
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/fraud")
@Validated
@Tag(name = "Fraud Detection", description = "Real-time fraud detection operations")
@Slf4j
public class FraudDetectionController {

    private final AssessTransactionRiskUseCase assessTransactionRiskUseCase;
    private final GetRiskAssessmentUseCase getRiskAssessmentUseCase;
    private final FindHighRiskAssessmentsUseCase findHighRiskAssessmentsUseCase;
    private final MeterRegistry meterRegistry;

    @PostMapping("/analyze")
    @Operation(
            summary = "Analyze transaction for fraud",
            description = "Performs real-time fraud analysis on a transaction"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")
    })
    public ResponseEntity<RiskAssessmentDto> analyzeTransaction(
            @Valid @RequestBody AssessTransactionRiskCommand command,
            @RequestHeader("X-Request-ID") String requestId) {

        MDC.put("requestId", requestId);

        try {
            log.info("Received fraud analysis request for transaction: {}", command.transactionId());

            RiskAssessmentDto assessment = assessTransactionRiskUseCase.assess(command);

            meterRegistry.counter("fraud.api.requests",
                    "endpoint", "analyze",
                    "status", "success"
            ).increment();

            return ResponseEntity.ok()
                    .header("X-Request-ID", requestId)
                    .body(assessment);

        } catch (Exception e) {
            log.error("Error processing fraud analysis", e);
            meterRegistry.counter("fraud.api.requests",
                    "endpoint", "analyze",
                    "status", "error"
            ).increment();
            throw e;
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/assessment/{transactionId}")
    @Operation(
            summary = "Get risk assessment by transaction ID",
            description = "Retrieves a previously completed risk assessment"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assessment found"),
            @ApiResponse(responseCode = "404", description = "Assessment not found")
    })
    public ResponseEntity<RiskAssessmentDto> getAssessment(
            @PathVariable UUID transactionId) {

        GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(transactionId);

        return getRiskAssessmentUseCase.get(query)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assessments/high-risk")
    @Operation(
            summary = "Find high-risk assessments",
            description = "Retrieves risk assessments with HIGH or CRITICAL risk levels since a specified time"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assessments retrieved successfully")
    })
    public ResponseEntity<List<RiskAssessmentDto>> findHighRiskAssessments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {

        // Query for HIGH risk assessments
        FindHighRiskAssessmentsQuery highQuery = FindHighRiskAssessmentsQuery.builder()
                .riskLevel(com.twenty9ine.frauddetection.domain.valueobject.RiskLevel.HIGH)
                .since(since)
                .build();

        List<RiskAssessmentDto> highRiskAssessments = findHighRiskAssessmentsUseCase.find(highQuery);

        // Query for CRITICAL risk assessments
        FindHighRiskAssessmentsQuery criticalQuery = FindHighRiskAssessmentsQuery.builder()
                .riskLevel(com.twenty9ine.frauddetection.domain.valueobject.RiskLevel.CRITICAL)
                .since(since)
                .build();

        List<RiskAssessmentDto> criticalRiskAssessments = findHighRiskAssessmentsUseCase.find(criticalQuery);

        // Combine both lists
        List<RiskAssessmentDto> allHighRiskAssessments = new java.util.ArrayList<>(highRiskAssessments);
        allHighRiskAssessments.addAll(criticalRiskAssessments);

        return ResponseEntity.ok(allHighRiskAssessments);
    }
}