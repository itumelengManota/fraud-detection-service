package com.twenty9ine.frauddetection.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class RuleEvaluation {
    private final String ruleId;
    private final String ruleName;
    private final RuleType ruleType;
    private final boolean triggered;
    private final int scoreImpact;
    private final String description;

    public RuleEvaluation(String ruleId, String ruleName, RuleType ruleType,
                         boolean triggered, int scoreImpact, String description) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.triggered = triggered;
        this.scoreImpact = scoreImpact;
        this.description = description;
    }
}
