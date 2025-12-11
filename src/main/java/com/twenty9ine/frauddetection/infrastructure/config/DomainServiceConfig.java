package com.twenty9ine.frauddetection.infrastructure.config;

import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.service.*;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

@Configuration
public class DomainServiceConfig {

    @Value("${fraud-detection.scoring.ml-weight:0.6}")
    private double mlWeight;

    @Value("${fraud-detection.scoring.rule-weight:0.4}")
    private double ruleWeight;

    @Bean
    public RuleEngineService ruleEngineService(KieContainer kieContainer) {
        return new RuleEngineService(kieContainer);
    }

    @Bean
    public GeographicValidator geographicValidator(TransactionRepository transactionRepository) {
        return new GeographicValidator(transactionRepository);
    }

    @Bean
    public RiskScoringService riskScoringService(RuleEngineService ruleEngine, Optional<MLServicePort> mlService, VelocityServicePort velocityService,
                                                 GeographicValidator geographicValidator) {
        return new RiskScoringService(ruleEngine, mlService.orElse(null), velocityService, geographicValidator, mlWeight, ruleWeight);
    }

    @Bean
    public DecisionService decisionService() {
        return new DecisionService(buildStrategies());
    }

    private static List<DecisionStrategy> buildStrategies() {
        return List.of(new CriticalRiskStrategy(), new HighRiskStrategy(), new MediumRiskStrategy(), new LowRiskStrategy());
    }
}
