package com.twenty9ine.frauddetection.domain.model;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.exception.InvariantViolationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
public class RiskAssessment {

    private final UUID assessmentId;
    private final UUID transactionId;
    private RiskScore riskScore;
    private RiskLevel riskLevel;
    private Decision decision;
    private final List<RuleEvaluation> ruleEvaluations;
    private MLPrediction mlPrediction;
    private final Instant assessmentTime;
    private final List<DomainEvent> domainEvents;

    public RiskAssessment(UUID assessmentId, UUID transactionId) {
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

        this.domainEvents.add(new RiskAssessmentCompleted(
            this.assessmentId,
            this.transactionId,
            finalScore,
            this.riskLevel,
            decision,
            Instant.now()
        ));

        if (finalScore.isHighRisk()) {
            this.domainEvents.add(new HighRiskDetected(
                this.assessmentId,
                this.transactionId,
                this.riskLevel,
                Instant.now()
            ));
        }
    }

    public void addRuleEvaluation(RuleEvaluation evaluation) {
        this.ruleEvaluations.add(evaluation);
    }

    public void setMlPrediction(MLPrediction prediction) {
        this.mlPrediction = prediction;
    }

    private void validateDecisionAlignment(RiskScore score, Decision decision) {
        if (score.toRiskLevel() == RiskLevel.CRITICAL && decision != Decision.BLOCK) {
            throw new InvariantViolationException(
                "Critical risk must result in BLOCK decision"
            );
        }
        if (score.toRiskLevel() == RiskLevel.LOW && decision == Decision.BLOCK) {
            throw new InvariantViolationException(
                "Low risk cannot result in BLOCK decision"
            );
        }
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
