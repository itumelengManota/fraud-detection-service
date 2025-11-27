package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.model.GeographicContext;
import com.twenty9ine.frauddetection.domain.model.Transaction;
import com.twenty9ine.frauddetection.domain.model.VelocityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

@Slf4j
public class RuleEngineService {

    private final KieContainer kieContainer;

    public RuleEngineService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public RuleEvaluationResult evaluateRules(Transaction transaction,
                                             VelocityMetrics velocity,
                                             GeographicContext geographic) {
        KieSession kieSession = kieContainer.newKieSession();

        try {
            kieSession.insert(transaction);
            kieSession.insert(velocity);
            kieSession.insert(geographic);

            RuleEvaluationResult result = new RuleEvaluationResult();
            kieSession.setGlobal("result", result);

            int rulesFired = kieSession.fireAllRules();
            log.debug("Fired {} rules for transaction {}",
                     rulesFired, transaction.getId());

            return result;

        } finally {
            kieSession.dispose();
        }
    }
}
