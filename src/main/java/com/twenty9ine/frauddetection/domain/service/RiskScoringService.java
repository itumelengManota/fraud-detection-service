package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RiskScoringService {

    private final RuleEngineService ruleEngine;
    private final MLServicePort mlService;
    private final VelocityServicePort velocityService;
    private final GeographicValidator geographicValidator;
    private final double mlWeight;
    private final double ruleWeight;

    public RiskScoringService(RuleEngineService ruleEngine, MLServicePort mlService, VelocityServicePort velocityService,
                              GeographicValidator geographicValidator, double mlWeight, double ruleWeight) {
        this.ruleEngine = ruleEngine;
        this.mlService = mlService;
        this.velocityService = velocityService;
        this.geographicValidator = geographicValidator;
        this.mlWeight = mlWeight;
        this.ruleWeight = ruleWeight;
    }

    public RiskAssessment assessRisk(Transaction transaction) {
        CompletableFuture<MLPrediction> mlFuture = predict(transaction);
        CompletableFuture<VelocityMetrics> velocityFuture = findVelocityMetricsByTransaction(transaction);
        CompletableFuture<GeographicContext> geographicFuture = validateGeographical(transaction);

        CompletableFuture.allOf(mlFuture, velocityFuture, geographicFuture).join();

        MLPrediction mlPrediction = mlFuture.join();
        VelocityMetrics velocity = velocityFuture.join();
        GeographicContext geographic = geographicFuture.join();

        RuleEvaluationResult ruleResults = ruleEngine.evaluateRules(transaction, velocity, geographic);

        return new RiskAssessment(transaction.id(), calculateCompositeScore(mlPrediction, ruleResults),
                toRuleEvaluations(ruleResults), mlPrediction);
    }

    private static List<RuleEvaluation> toRuleEvaluations(RuleEvaluationResult ruleResults) {
        return ruleResults.getTriggers()
                .stream()
                .map(RiskScoringService::buildRuleEvaluation)
                .toList();
    }

    private static RuleEvaluation buildRuleEvaluation(RuleTrigger ruleTrigger) {
        return new RuleEvaluation(ruleTrigger.ruleId(), ruleTrigger.ruleName(), determineRuleType(ruleTrigger),
                            true, ruleTrigger.triggeredValue(), ruleTrigger.description());
    }

    private static RuleType determineRuleType(RuleTrigger ruleTrigger) {
        return switch (ruleTrigger.ruleId()) {
            case "VELOCITY_5MIN", "VELOCITY_1HOUR" -> RuleType.VELOCITY;
            case "IMPOSSIBLE_TRAVEL" -> RuleType.GEOGRAPHIC;
            case "LARGE_AMOUNT", "VERY_LARGE_AMOUNT" -> RuleType.AMOUNT;
            default -> throw new IllegalStateException("Unexpected value: " + ruleTrigger.ruleId());
        };
    }

    private CompletableFuture<MLPrediction> predict(Transaction transaction) {
        return CompletableFuture.supplyAsync(() -> mlService.predict(transaction));
    }

    private CompletableFuture<GeographicContext> validateGeographical(Transaction transaction) {
        return CompletableFuture.supplyAsync(() ->
                geographicValidator.validate(transaction));
    }

    private CompletableFuture<VelocityMetrics> findVelocityMetricsByTransaction(Transaction transaction) {
        return CompletableFuture.supplyAsync(() ->
                velocityService.findVelocityMetricsByTransaction(transaction));
    }

    private RiskScore calculateCompositeScore(MLPrediction ml, RuleEvaluationResult rules) {
        BigDecimal mlScore = percentage(ml.fraudProbability());
        BigDecimal ruleScore = findAggregateScore(rules);
        BigDecimal finalScore = calculateFinalScore(mlScore, ruleScore);

        return new RiskScore(Math.clamp(roundUpScore(finalScore), 0, 100));
    }

    private static int roundUpScore(BigDecimal finalScore) {
        return finalScore.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal calculateFinalScore(BigDecimal mlScore, BigDecimal ruleScore) {
        return mlScore.multiply(BigDecimal.valueOf(mlWeight))
                .add(ruleScore.multiply(BigDecimal.valueOf(ruleWeight)));
    }

    private static BigDecimal findAggregateScore(RuleEvaluationResult rules) {
        return BigDecimal.valueOf(rules.aggregateScore());
    }

    private static BigDecimal percentage(double val) {
        return BigDecimal.valueOf(val).multiply(BigDecimal.valueOf(100));
    }
}
