package com.twenty9ine.frauddetection.application.service;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.*;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.query.FindHighRiskAssessmentsQuery;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;
import com.twenty9ine.frauddetection.application.port.out.EventPublisherPort;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.service.DecisionService;
import com.twenty9ine.frauddetection.domain.service.RiskScoringService;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service that orchestrates fraud detection use cases.
 *
 * Implements input ports (use case interfaces) and coordinates between
 * domain services and output ports. Acts as the application layer's
 * facade following Hexagonal Architecture principles.
 *
 * @author Fraud Detection Team
 */
@Service
@Transactional
@Slf4j
public class FraudDetectionApplicationService implements AssessTransactionRiskUseCase, GetRiskAssessmentUseCase,
        FindHighRiskAssessmentsUseCase {

    private final RiskScoringService riskScoringService;
    private final DecisionService decisionService;
    private final RiskAssessmentRepository repository;
    private final EventPublisherPort eventPublisher;

    public FraudDetectionApplicationService(
            RiskScoringService riskScoringService,
            DecisionService decisionService,
            RiskAssessmentRepository repository,
            EventPublisherPort eventPublisher) {
        this.riskScoringService = riskScoringService;
        this.decisionService = decisionService;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

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

        log.info("Completed risk assessment for transaction: {} with decision: {}", transaction.id(), decision);

        return RiskAssessmentDto.from(assessment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskAssessmentDto> get(GetRiskAssessmentQuery query) {
        log.debug("Retrieving risk assessment for transaction: {}", query.transactionId());

        return repository.findByTransactionId(query.transactionId())
                .map(RiskAssessmentDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskAssessmentDto> find(FindHighRiskAssessmentsQuery query) {
        log.debug("Finding {} risk assessments since {}",
                query.riskLevel(), query.since());

        return repository.findByRiskLevelSince(query.riskLevel(), query.since())
                .stream()
                .map(RiskAssessmentDto::from)
                .toList();
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
                .merchant(new Merchant(MerchantId.of(command.merchantId()), command.merchantName(), command.merchantCategory()))
                .location(command.location() != null ? toDomain(command.location()) : null)
                .deviceId(command.deviceId())
                .timestamp(command.transactionTimestamp())
                .build();
    }

    private Location toDomain(LocationDto location) {
        return new Location(location.latitude(), location.longitude(), location.country(), location.city(), location.timestamp());
    }
}