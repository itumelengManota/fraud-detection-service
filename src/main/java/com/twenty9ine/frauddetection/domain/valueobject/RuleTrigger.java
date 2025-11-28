package com.twenty9ine.frauddetection.domain.valueobject;

public record RuleTrigger(
    String ruleId,
    String ruleName,
    RiskImpact impact,
    String description,
    double triggeredValue
) {}
