package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Builder;

@Builder
public record RuleEvaluation(String ruleId, String ruleName, RuleType ruleType, boolean triggered, int scoreImpact,
                             String description) {
}
