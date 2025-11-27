package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.model.Decision;
import com.twenty9ine.frauddetection.domain.model.RiskAssessment;
import com.twenty9ine.frauddetection.domain.model.RiskLevel;

public interface DecisionStrategy {
    RiskLevel getRiskLevel();
    Decision decide(RiskAssessment assessment);
}
