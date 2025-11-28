package com.twenty9ine.frauddetection.domain.aggregate;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.InvariantViolationException;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
    private RiskScore riskScore;
    private RiskLevel riskLevel;
    private Decision decision;
    private final List<RuleEvaluation> ruleEvaluations;

    @Setter
    private MLPrediction mlPrediction;
    private final Instant assessmentTime;
    private final List<DomainEvent> domainEvents;

    public static RiskAssessment of(TransactionId transactionId) {
        return new RiskAssessment(transactionId);
    }

    public RiskAssessment(TransactionId transactionId) {
        this(AssessmentId.generate(), transactionId);
    }

    public RiskAssessment(AssessmentId assessmentId, TransactionId transactionId) {
        this.assessmentId = assessmentId;
        this.transactionId = transactionId;
        this.ruleEvaluations = new ArrayList<>();
        this.domainEvents = new ArrayList<>();
        this.assessmentTime = Instant.now();
    }

    public void completeAssessment(RiskScore finalScore, Decision decision) {
        validateDecisionAlignment(finalScore, decision);

        this.riskScore = finalScore;
        this.riskLevel = finalScore.toRiskLevel();
        this.decision = decision;

        publishEvent(RiskAssessmentCompleted.of(this.assessmentId, this.transactionId, finalScore, this.riskLevel, decision));

        if (finalScore.hasHighRisk()) {
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

    private void validateDecisionAlignment(RiskScore score, Decision decision) {
        if (score.hasCriticalRisk() && decision.isProceed())
            throw new InvariantViolationException("Critical risk must result in BLOCK decision");

        if (score.hasLowRisk() && decision.isBlocked())
            throw new InvariantViolationException("Low risk cannot result in BLOCK decision");
    }
}
