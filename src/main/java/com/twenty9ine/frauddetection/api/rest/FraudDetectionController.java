package com.twenty9ine.frauddetection.api.rest;

import com.twenty9ine.frauddetection.application.FraudDetectionApplicationService;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.dto.TransactionDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
@Validated
@Tag(name = "Fraud Detection", description = "Real-time fraud detection operations")
@Slf4j
public class FraudDetectionController {

    private final FraudDetectionApplicationService fraudDetectionService;
    private final MeterRegistry meterRegistry;

    public FraudDetectionController(
            FraudDetectionApplicationService fraudDetectionService,
            MeterRegistry meterRegistry) {
        this.fraudDetectionService = fraudDetectionService;
        this.meterRegistry = meterRegistry;
    }

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
            @Valid @RequestBody TransactionDto transaction,
            @RequestHeader("X-Request-ID") String requestId) {

        MDC.put("requestId", requestId);

        try {
            log.info("Received fraud analysis request for transaction: {}",
                    transaction.transactionId());

            RiskAssessmentDto assessment =
                fraudDetectionService.assessRisk(transaction.toDomain());

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
    @Operation(summary = "Get risk assessment by transaction ID")
    public ResponseEntity<RiskAssessmentDto> getAssessment(
            @PathVariable UUID transactionId) {

        return fraudDetectionService.getAssessment(transactionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
