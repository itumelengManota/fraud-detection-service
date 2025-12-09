package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;

public interface DecisionStrategy {
    TransactionRiskLevel getRiskLevel();
    Decision decide(RiskAssessment assessment);
}
