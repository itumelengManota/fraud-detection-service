package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;

public class HighRiskStrategy implements DecisionStrategy {
    @Override
    public TransactionRiskLevel getRiskLevel() {
        return TransactionRiskLevel.HIGH;
    }

    @Override
    public Decision decide(RiskAssessment assessment) {
        return Decision.REVIEW;
    }
}
