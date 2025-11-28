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
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
public class RiskAssessment {

    private final UUID assessmentId;
    private final TransactionId transactionId;
    private RiskScore riskScore;
    private RiskLevel riskLevel;
    private Decision decision;
    private final List<RuleEvaluation> ruleEvaluations;

    @Setter
    private MLPrediction mlPrediction;
    private final Instant assessmentTime;
    private final List<DomainEvent> domainEvents;

    public RiskAssessment(UUID assessmentId, TransactionId transactionId) {
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

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    private void validateDecisionAlignment(RiskScore score, Decision decision) {
        if (hasCriticalRisk(score) && isProceed(decision))
            throw new InvariantViolationException("Critical risk must result in BLOCK decision");

        if (hasLowRisk(score) && isBlocked(decision))
            throw new InvariantViolationException("Low risk cannot result in BLOCK decision");
    }

    private static boolean isBlocked(Decision decision) {
        return decision == Decision.BLOCK;
    }

    private static boolean hasLowRisk(RiskScore score) {
        return score.toRiskLevel() == RiskLevel.LOW;
    }

    private static boolean isProceed(Decision decision) {
        return decision != Decision.BLOCK;
    }

    private static boolean hasCriticalRisk(RiskScore score) {
        return score.toRiskLevel() == RiskLevel.CRITICAL;
    }
}
