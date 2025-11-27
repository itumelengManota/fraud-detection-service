package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.model.Decision;
import com.twenty9ine.frauddetection.domain.model.RiskAssessment;
import com.twenty9ine.frauddetection.domain.model.RiskLevel;

public class CriticalRiskStrategy implements DecisionStrategy {
    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.CRITICAL;
    }

    @Override
    public Decision decide(RiskAssessment assessment) {
        return Decision.BLOCK;
    }
}
