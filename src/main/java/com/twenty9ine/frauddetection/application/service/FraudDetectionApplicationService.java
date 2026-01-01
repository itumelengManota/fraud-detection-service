package com.twenty9ine.frauddetection.application.service;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.dto.PagedResultDto;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.*;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.query.FindRiskLeveledAssessmentsQuery;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;
import com.twenty9ine.frauddetection.application.port.in.query.PageRequestQuery;
import com.twenty9ine.frauddetection.application.port.out.EventPublisherPort;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.exception.RiskAssessmentNotFoundException;
import com.twenty9ine.frauddetection.domain.service.DecisionService;
import com.twenty9ine.frauddetection.domain.service.RiskScoringService;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Application service that orchestrates fraud detection use cases.
 * <p>
 * Implements input ports (use case interfaces) and coordinates between
 * domain services and output ports. Acts as the application layer's
 * facade following Hexagonal Architecture principles.
 *
 * @author Ignatius Itumeleng Manota
 */
@RequiredArgsConstructor
@Service
@Transactional
@Slf4j
public class FraudDetectionApplicationService implements AssessTransactionRiskUseCase, GetRiskAssessmentUseCase,
        FindRiskLeveledAssessmentsUseCase {

    private final RiskScoringService riskScoringService;
    private final DecisionService decisionService;
    private final RiskAssessmentRepository repository;
    private final EventPublisherPort eventPublisher;
    private final VelocityServicePort velocityService;

    @Override
    public RiskAssessmentDto assess(AssessTransactionRiskCommand command) {
        Transaction transaction = toDomain(command);

        log.info("Starting risk assessment for transaction: {}", transaction.id());

        RiskAssessment assessment = riskScoringService.assessRisk(transaction);
        Decision decision = decisionService.makeDecision(assessment);
        assessment.completeAssessment(decision);

        repository.save(assessment);
        eventPublisher.publishAll(assessment.getDomainEvents());
        assessment.clearDomainEvents();

        velocityService.incrementCounters(transaction);

        log.info("Completed risk assessment for transaction: {} with decision: {}", transaction.id(), decision);

        return RiskAssessmentDto.from(assessment);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskAssessmentDto get(GetRiskAssessmentQuery query) {
        log.debug("Retrieving risk assessment for transaction: {}", query.transactionId());

        return repository.findByTransactionId(TransactionId.of(query.transactionId()))
                .map(RiskAssessmentDto::from)
                .orElseThrow(() -> new RiskAssessmentNotFoundException("Risk assessment not found for transaction ID: " + query.transactionId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResultDto<RiskAssessmentDto> find(FindRiskLeveledAssessmentsQuery query, PageRequestQuery pageRequestQuery) {
        log.debug("Finding {} risk assessments fromDate {}", query.transactionRiskLevels(), query.fromDate());
        return getRiskAssessmentDtoPagedResultDto(findAssessmentsByRiskLevelsAndFromDate(query, pageRequestQuery));
    }

    private PagedResult<RiskAssessment> findAssessmentsByRiskLevelsAndFromDate(FindRiskLeveledAssessmentsQuery query, PageRequestQuery pageRequestQuery) {
        return repository.findByRiskLevelSince(toTransactionRiskLevels(query.transactionRiskLevels()), query.fromDate(), toPageRequest(pageRequestQuery));
    }

    private static Set<TransactionRiskLevel> toTransactionRiskLevels(Set<String> riskLevelStrings) {
        if(riskLevelStrings == null || riskLevelStrings.isEmpty()) {
            return Set.of();
        }

        return riskLevelStrings.stream()
                .map(TransactionRiskLevel::fromString)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static PagedResultDto<RiskAssessmentDto> getRiskAssessmentDtoPagedResultDto(PagedResult<RiskAssessment> pagedResult) {
        return new PagedResultDto<>(toRiskAssessmentDtos(pagedResult.content()), pagedResult.pageNumber(), pagedResult.pageSize(),
                pagedResult.totalElements(), pagedResult.totalPages());
    }

    private static List<RiskAssessmentDto> toRiskAssessmentDtos(List<RiskAssessment> riskAssessments) {
        return riskAssessments
                .stream()
                .map(RiskAssessmentDto::from)
                .toList();
    }

    private static PageRequest toPageRequest(PageRequestQuery pageRequestQuery) {
        return new PageRequest(
                pageRequestQuery.pageNumber(),
                pageRequestQuery.pageSize(),
                pageRequestQuery.sortBy(),
                SortDirection.valueOf(pageRequestQuery.sortDirection())
        );
    }

    /**
     * Converts this command to a Transaction domain object.
     *
     * @return Transaction value object ready for domain processing
     */
    private Transaction toDomain(AssessTransactionRiskCommand command) {
        return Transaction.builder()
                .id(TransactionId.of(command.transactionId()))
                .accountId(command.accountId())
                .amount(new Money(command.amount(), java.util.Currency.getInstance(command.currency())))
                .type(TransactionType.fromString(command.type()))
                .channel(Channel.fromString(command.channel()))
                .merchant(new Merchant(MerchantId.of(command.merchantId()), command.merchantName(), MerchantCategory.fromString(command.merchantCategory())))
                .location(command.location() != null ? toDomain(command.location()) : null)
                .deviceId(command.deviceId())
                .timestamp(command.transactionTimestamp())
                .build();
    }

    private Location toDomain(LocationDto location) {
        return new Location(location.latitude(), location.longitude(), location.country(), location.city());
    }
}