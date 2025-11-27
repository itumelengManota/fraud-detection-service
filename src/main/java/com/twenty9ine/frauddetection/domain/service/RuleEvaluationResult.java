package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.model.RuleTrigger;
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
            .mapToDouble(t -> switch(t.impact()) {
                case LOW -> 10.0;
                case MEDIUM -> 25.0;
                case HIGH -> 40.0;
                case CRITICAL -> 60.0;
            })
            .sum();
    }
}
