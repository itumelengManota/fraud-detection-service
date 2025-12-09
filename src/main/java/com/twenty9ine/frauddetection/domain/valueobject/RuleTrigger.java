package com.twenty9ine.frauddetection.domain.valueobject;

public record RuleTrigger(
    String ruleId,
    String ruleName,
    RuleViolationSeverity ruleViolationSeverity,
    String description,
    double triggeredValue
) {}
