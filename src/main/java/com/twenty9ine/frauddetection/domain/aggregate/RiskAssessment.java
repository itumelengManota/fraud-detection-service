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
    private final TransactionRiskLevel transactionRiskLevel;
    private Decision decision;
    private final List<RuleEvaluation> ruleEvaluations;
    private final MLPrediction mlPrediction;
    private final Instant assessmentTime;
    private final List<DomainEvent<TransactionId>> domainEvents;

    public static RiskAssessment of(TransactionId transactionId) {
        return new RiskAssessment(transactionId);
    }

    public RiskAssessment(TransactionId transactionId) {
        this(AssessmentId.generate(), transactionId);
    }

    public RiskAssessment(TransactionId transactionId, RiskScore riskScore) {
        this(transactionId, riskScore, new ArrayList<>(), null);
    }

    public RiskAssessment(TransactionId transactionId, RiskScore riskScore, Instant assessmentTime) {
        this(AssessmentId.generate(), transactionId, riskScore, new ArrayList<>(), null, assessmentTime);
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
        this(assessmentId, transactionId, riskScore, evaluations, mlPrediction, Instant.now());
    }

    public RiskAssessment(AssessmentId assessmentId, TransactionId transactionId, RiskScore riskScore, List<RuleEvaluation> evaluations, MLPrediction mlPrediction, Instant assessmentTime) {
        this.assessmentId = assessmentId;
        this.transactionId = transactionId;
        this.riskScore = riskScore;
        this.transactionRiskLevel = determineRiskLevel(riskScore);
        this.mlPrediction = mlPrediction;
        this.ruleEvaluations = new ArrayList<>(evaluations);
        this.domainEvents = new ArrayList<>();
        this.assessmentTime = assessmentTime;
    }

    public void completeAssessment(Decision decision) {
        this.decision = decision;
        validateDecisionAlignment();

        publishEvent(RiskAssessmentCompleted.of(this.transactionId, this.assessmentId, this.riskScore, this.transactionRiskLevel, decision));

        if (hasHighRisk()) {
            publishEvent(HighRiskDetected.of(this.transactionId, this.assessmentId, this.transactionRiskLevel));
        }
    }

    public void addRuleEvaluation(RuleEvaluation evaluation) {
        this.ruleEvaluations.add(evaluation);
    }

    public List<DomainEvent<TransactionId>> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    private void publishEvent(DomainEvent<TransactionId> event) {
        this.domainEvents.add(event);
    }

    private void validateDecisionAlignment() {
        if (hasCriticalRisk() && this.decision.isProceed())
            throw new InvariantViolationException("Critical risk must result in BLOCK decision");

        if (hasLowRisk() && this.decision.isBlocked())
            throw new InvariantViolationException("Low risk cannot result in BLOCK decision");
    }

    private boolean hasLowRisk() {
        return determineRiskLevel(this.riskScore) == TransactionRiskLevel.LOW;
    }

    private boolean hasCriticalRisk() {
        return determineRiskLevel(this.riskScore) == TransactionRiskLevel.CRITICAL;
    }

    private boolean hasHighRisk() {
        return determineRiskLevel(this.riskScore) == TransactionRiskLevel.HIGH ||
                determineRiskLevel(this.riskScore) == TransactionRiskLevel.CRITICAL;
    }

    private TransactionRiskLevel determineRiskLevel(RiskScore riskScore) {
        int score = riskScore.value();

        if (score <= 40) {
            return TransactionRiskLevel.LOW;
        } else if (score <= 70) {
            return TransactionRiskLevel.MEDIUM;
        } else if (score <= 90) {
            return TransactionRiskLevel.HIGH;
        } else {
            return TransactionRiskLevel.CRITICAL;
        }
    }
}
