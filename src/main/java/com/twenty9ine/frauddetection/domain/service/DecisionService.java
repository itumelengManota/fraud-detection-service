package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.model.Decision;
import com.twenty9ine.frauddetection.domain.model.RiskAssessment;
import com.twenty9ine.frauddetection.domain.model.RiskLevel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DecisionService {

    private final Map<RiskLevel, DecisionStrategy> strategies;

    public DecisionService(List<DecisionStrategy> strategies) {
        this.strategies = strategies.stream()
            .collect(Collectors.toMap(
                DecisionStrategy::getRiskLevel,
                Function.identity()
            ));
    }

    public Decision makeDecision(RiskAssessment assessment) {
        RiskLevel level = assessment.getRiskLevel();
        DecisionStrategy strategy = strategies.get(level);

        if (strategy == null) {
            throw new IllegalStateException("No strategy for risk level: " + level);
        }

        return strategy.decide(assessment);
    }
}
