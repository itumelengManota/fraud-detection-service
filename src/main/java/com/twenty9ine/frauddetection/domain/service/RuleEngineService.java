package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.GeographicContext;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.VelocityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

@Slf4j
public class RuleEngineService {

    private final KieContainer kieContainer;

    public RuleEngineService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public RuleEvaluationResult evaluateRules(Transaction transaction, VelocityMetrics velocity, GeographicContext geographic) {
        try (KieSession kieSession = kieContainer.newKieSession()) {

            kieSession.insert(transaction);
            kieSession.insert(velocity);
            kieSession.insert(geographic);

            RuleEvaluationResult ruleEvaluationResult = new RuleEvaluationResult();
            kieSession.setGlobal("ruleEvaluationResult", ruleEvaluationResult);

            int rulesFired = kieSession.fireAllRules();
            log.debug("Fired {} rules for transaction {}", rulesFired, transaction.id());

            return ruleEvaluationResult;
        }
    }
}
