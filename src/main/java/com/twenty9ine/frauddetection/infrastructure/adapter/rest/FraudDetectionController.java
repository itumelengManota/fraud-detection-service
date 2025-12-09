package com.twenty9ine.frauddetection.infrastructure.adapter.rest;

import com.twenty9ine.frauddetection.application.dto.PagedResultDto;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.*;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.query.FindRiskLeveledAssessmentsQuery;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;
import com.twenty9ine.frauddetection.application.port.in.query.PageRequestQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST adapter for fraud detection use cases.
 * <p>
 * Translates HTTP requests into use case commands/queries and delegates
 * to input ports. Follows Hexagonal Architecture by depending on
 * port interfaces rather than concrete implementations.
 *
 * @author Ignatius Itumeleng Manota
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
    private final FindRiskLeveledAssessmentsUseCase findRiskLeveledAssessmentsUseCase;

    @PostMapping("/assessments")
    @Operation(summary = "Analyze transaction for fraud", description = "Performs real-time fraud analysis on a transaction")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Analysis completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "503", description = "Service unavailable")})
    public RiskAssessmentDto assessTransaction(@Valid @RequestBody AssessTransactionRiskCommand command) {
        return assessTransactionRiskUseCase.assess(command);
    }

    @GetMapping("/assessments/{transactionId}")
    @Operation(summary = "Get risk assessment by transaction ID", description = "Retrieves a previously completed risk assessment")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Assessment found"),
                   @ApiResponse(responseCode = "404", description = "Assessment not found")})
    public RiskAssessmentDto getAssessment(@PathVariable UUID transactionId) {
        return getRiskAssessmentUseCase.get(new GetRiskAssessmentQuery(transactionId));
    }

    @GetMapping("/assessments")
    @Operation(summary = "Find high-risk assessments", description = "Retrieves risk assessments with HIGH or CRITICAL risk levels from a specified time")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Assessments retrieved successfully")})
    public Page<RiskAssessmentDto> findAssessmentsByRiskLevelAndFromDate(@RequestParam FindRiskLeveledAssessmentsQuery query, Pageable pageable) {
        return toPage(pageable, findAssessmentsByQuery(query, pageable));
    }

    private PagedResultDto<RiskAssessmentDto> findAssessmentsByQuery(FindRiskLeveledAssessmentsQuery query, Pageable pageable) {
        return findRiskLeveledAssessmentsUseCase.find(query, buildPageRequestQuery(pageable));
    }

    private static PageRequestQuery buildPageRequestQuery(Pageable pageable) {
        return PageRequestQuery.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private static Page<RiskAssessmentDto> toPage(Pageable pageable, PagedResultDto<RiskAssessmentDto> result) {
        return new PageImpl<>(result.content(), pageable, result.totalElements());
    }
}