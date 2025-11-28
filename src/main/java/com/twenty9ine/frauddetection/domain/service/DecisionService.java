package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DecisionService {

    private final Map<RiskLevel, DecisionStrategy> strategies;

    public DecisionService(List<DecisionStrategy> strategies) {
        this.strategies = initialise(strategies);
    }

    public Decision makeDecision(RiskAssessment assessment) {
        RiskLevel level = assessment.getRiskLevel();
        DecisionStrategy strategy = strategies.get(level);

        if (strategy == null) {
            throw new IllegalStateException("No strategy for risk level: %s".formatted(level));
        }

        return strategy.decide(assessment);
    }

    private static Map<RiskLevel, DecisionStrategy> initialise(List<DecisionStrategy> strategies) {
        return strategies.stream()
                .collect(Collectors.toMap(DecisionStrategy::getRiskLevel, Function.identity()));
    }
}
