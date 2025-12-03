package com.twenty9ine.frauddetection.domain.aggregate;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.InvariantViolationException;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@ToString
@EqualsAndHashCode(of = {"assessmentId"})
public class RiskAssessment {
    private final AssessmentId assessmentId;
    private final TransactionId transactionId;
    private final RiskScore riskScore;
    private final RiskLevel riskLevel;
    private Decision decision;
    private final List<RuleEvaluation> ruleEvaluations;
    private final MLPrediction mlPrediction;
    private final Instant assessmentTime;
    private final List<DomainEvent> domainEvents;

    public static RiskAssessment of(TransactionId transactionId) {
        return new RiskAssessment(transactionId);
    }

    public RiskAssessment(TransactionId transactionId) {
        this(AssessmentId.generate(), transactionId);
    }

    public RiskAssessment(TransactionId transactionId, RiskScore riskScore) {
        this(transactionId, riskScore, new ArrayList<>(), null);
    }

    public RiskAssessment(TransactionId transactionId, RiskScore riskScore, List<RuleEvaluation> evaluations, MLPrediction mlPrediction) {
        this(AssessmentId.generate(), transactionId, riskScore, evaluations, mlPrediction);
    }

    public RiskAssessment(AssessmentId assessmentId, TransactionId transactionId) {
        this(assessmentId, transactionId, null, List.of(), null);
    }

    public RiskAssessment(AssessmentId assessmentId, TransactionId transactionId, RiskScore riskScore) {
        this(assessmentId, transactionId, riskScore, List.of(), null);
    }

    public RiskAssessment(AssessmentId assessmentId, TransactionId transactionId, RiskScore riskScore, List<RuleEvaluation> evaluations, MLPrediction mlPrediction) {
        this.assessmentId = assessmentId;
        this.transactionId = transactionId;
        this.riskScore = riskScore;
        this.riskLevel = determineRiskLevel(riskScore);
        this.mlPrediction = mlPrediction;
        this.ruleEvaluations = new ArrayList<>(evaluations);
        this.domainEvents = new ArrayList<>();
        this.assessmentTime = Instant.now();
    }

    public void completeAssessment(Decision decision) {
        this.decision = decision;
        validateDecisionAlignment();

        publishEvent(RiskAssessmentCompleted.of(this.assessmentId, this.transactionId, this.riskScore, this.riskLevel, decision));

        if (hasHighRisk()) {
            publishEvent(HighRiskDetected.of(this.assessmentId, this.transactionId, this.riskLevel));
        }
    }

    public void addRuleEvaluation(RuleEvaluation evaluation) {
        this.ruleEvaluations.add(evaluation);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    private void publishEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    private void validateDecisionAlignment() {
        if (hasCriticalRisk() && this.decision.isProceed())
            throw new InvariantViolationException("Critical risk must result in BLOCK decision");

        if (hasLowRisk() && this.decision.isBlocked())
            throw new InvariantViolationException("Low risk cannot result in BLOCK decision");
    }

    private boolean hasLowRisk() {
        return determineRiskLevel(this.riskScore) == RiskLevel.LOW;
    }

    private boolean hasCriticalRisk() {
        return determineRiskLevel(this.riskScore) == RiskLevel.CRITICAL;
    }

    private boolean hasHighRisk() {
        return determineRiskLevel(this.riskScore) == RiskLevel.HIGH ||
                determineRiskLevel(this.riskScore) == RiskLevel.CRITICAL;
    }

    private RiskLevel determineRiskLevel(RiskScore riskScore) {
        int score = riskScore.value();

        if (score <= 40) {
            return RiskLevel.LOW;
        } else if (score <= 70) {
            return RiskLevel.MEDIUM;
        } else if (score <= 90) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.CRITICAL;
        }
    }
}
