package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.model.Decision;
import com.twenty9ine.frauddetection.domain.model.RiskAssessment;
import com.twenty9ine.frauddetection.domain.model.RiskLevel;

public class MediumRiskStrategy implements DecisionStrategy {
    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.MEDIUM;
    }

    @Override
    public Decision decide(RiskAssessment assessment) {
        return Decision.CHALLENGE;
    }
}
