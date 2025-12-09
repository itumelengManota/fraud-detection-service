package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DecisionService {

    private final Map<TransactionRiskLevel, DecisionStrategy> strategies;

    public DecisionService(List<DecisionStrategy> strategies) {
        this.strategies = initialise(strategies);
    }

    public Decision makeDecision(RiskAssessment assessment) {
        TransactionRiskLevel level = assessment.getTransactionRiskLevel();
        DecisionStrategy strategy = findStrategy(level);

        if (strategy == null) {
            throw new IllegalStateException("No strategy for risk level: %s".formatted(level));
        }

        return strategy.decide(assessment);
    }

    private DecisionStrategy findStrategy(TransactionRiskLevel level) {
        return strategies.get(level);
    }

    private static Map<TransactionRiskLevel, DecisionStrategy> initialise(List<DecisionStrategy> strategies) {
        return strategies.stream()
                .collect(Collectors.toMap(DecisionStrategy::getRiskLevel, Function.identity()));
    }
}
