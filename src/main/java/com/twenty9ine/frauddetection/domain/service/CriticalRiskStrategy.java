package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;

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
