package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.model.*;
import com.twenty9ine.frauddetection.domain.port.MLServicePort;
import com.twenty9ine.frauddetection.domain.port.VelocityServicePort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RiskScoringService {

    private final RuleEngineService ruleEngine;
    private final MLServicePort mlService;
    private final VelocityServicePort velocityService;
    private final GeographicValidator geographicValidator;
    private final double mlWeight;
    private final double ruleWeight;

    public RiskScoringService(RuleEngineService ruleEngine,
                             MLServicePort mlService,
                             VelocityServicePort velocityService,
                             GeographicValidator geographicValidator,
                             double mlWeight,
                             double ruleWeight) {
        this.ruleEngine = ruleEngine;
        this.mlService = mlService;
        this.velocityService = velocityService;
        this.geographicValidator = geographicValidator;
        this.mlWeight = mlWeight;
        this.ruleWeight = ruleWeight;
    }

    public RiskAssessment assessRisk(Transaction transaction) {
        CompletableFuture<MLPrediction> mlFuture =
            CompletableFuture.supplyAsync(() ->
                mlService.predict(transaction)
            );

        CompletableFuture<VelocityMetrics> velocityFuture =
            CompletableFuture.supplyAsync(() ->
                velocityService.getVelocity(transaction.getAccountId())
            );

        CompletableFuture<GeographicContext> geographicFuture =
            CompletableFuture.supplyAsync(() ->
                geographicValidator.validate(transaction)
            );

        CompletableFuture.allOf(mlFuture, velocityFuture, geographicFuture).join();

        MLPrediction mlPrediction = mlFuture.join();
        VelocityMetrics velocity = velocityFuture.join();
        GeographicContext geographic = geographicFuture.join();

        RuleEvaluationResult ruleResults =
            ruleEngine.evaluateRules(transaction, velocity, geographic);

        RiskScore score = calculateCompositeScore(mlPrediction, ruleResults);

        RiskAssessment assessment = new RiskAssessment(
            UUID.randomUUID(),
            transaction.getId()
        );

        assessment.setMlPrediction(mlPrediction);
        ruleResults.getTriggers().forEach(t ->
            assessment.addRuleEvaluation(new RuleEvaluation(
                t.ruleId(),
                t.ruleName(),
                RuleType.VELOCITY,
                true,
                (int) t.triggeredValue(),
                t.description()
            ))
        );

        return assessment;
    }

    private RiskScore calculateCompositeScore(MLPrediction ml,
                                             RuleEvaluationResult rules) {
        BigDecimal mlScore = BigDecimal.valueOf(ml.fraudProbability())
            .multiply(BigDecimal.valueOf(100));

        BigDecimal ruleScore = BigDecimal.valueOf(rules.aggregateScore());

        BigDecimal finalScore = mlScore.multiply(BigDecimal.valueOf(mlWeight))
            .add(ruleScore.multiply(BigDecimal.valueOf(ruleWeight)));

        int roundedScore = finalScore.setScale(0, RoundingMode.HALF_UP).intValue();

        return new RiskScore(Math.min(100, Math.max(0, roundedScore)));
    }
}
