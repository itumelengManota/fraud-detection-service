package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class RuleEvaluationResult {
    private final List<RuleTrigger> triggers = new ArrayList<>();

    public void addTrigger(RuleTrigger trigger) {
        this.triggers.add(trigger);
    }

    public double aggregateScore() {
        return triggers.stream()
                       .mapToDouble(RuleEvaluationResult::toScore)
                       .sum();
    }

    private static double toScore(RuleTrigger t) {
        return switch (t.ruleViolationSeverity()) {
            case LOW -> 10.0;
            case MEDIUM -> 25.0;
            case HIGH -> 40.0;
            case CRITICAL -> 60.0;
        };
    }
}
