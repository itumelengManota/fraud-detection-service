package com.twenty9ine.frauddetection.domain.model;

public record RuleTrigger(
    String ruleId,
    String ruleName,
    RiskImpact impact,
    String description,
    double triggeredValue
) {}
