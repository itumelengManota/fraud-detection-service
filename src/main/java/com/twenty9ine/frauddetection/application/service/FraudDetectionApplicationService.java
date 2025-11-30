package com.twenty9ine.frauddetection.application.service;

import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.application.port.out.EventPublisherPort;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.domain.service.DecisionService;
import com.twenty9ine.frauddetection.domain.service.RiskScoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class FraudDetectionApplicationService {

    private final RiskScoringService riskScoringService;
    private final DecisionService decisionService;
    private final RiskAssessmentRepository repository;
    private final EventPublisherPort eventPublisher;

    public FraudDetectionApplicationService(RiskScoringService riskScoringService, DecisionService decisionService,
                                            RiskAssessmentRepository repository, EventPublisherPort eventPublisher) {
        this.riskScoringService = riskScoringService;
        this.decisionService = decisionService;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public RiskAssessmentDto assessRisk(Transaction transaction) {
        log.info("Starting risk assessment for transaction: {}", transaction.id());

        RiskAssessment assessment = riskScoringService.assessRisk(transaction);
        Decision decision = decisionService.makeDecision(assessment);
        assessment.completeAssessment(assessment.getRiskScore(), decision);

        repository.save(assessment);
        eventPublisher.publishAll(assessment.getDomainEvents());
        assessment.clearDomainEvents();

        log.info("Completed risk assessment for transaction: {} with decision: {}", transaction.id(), decision);

        return RiskAssessmentDto.from(assessment);
    }

    @Transactional(readOnly = true)
    public Optional<RiskAssessmentDto> getAssessment(UUID transactionId) {
        return repository.findByTransactionId(transactionId)
            .map(RiskAssessmentDto::from);
    }
}
