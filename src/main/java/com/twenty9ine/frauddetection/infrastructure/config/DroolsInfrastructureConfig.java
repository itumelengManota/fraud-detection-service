package com.twenty9ine.frauddetection.infrastructure.config;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DroolsInfrastructureConfig {

    @Bean
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        kieFileSystem.write(ResourceFactory.newClassPathResource(
            "rules/velocity-rules.drl"
        ));
        kieFileSystem.write(ResourceFactory.newClassPathResource(
            "rules/geographic-rules.drl"
        ));
        kieFileSystem.write(ResourceFactory.newClassPathResource(
            "rules/amount-rules.drl"
        ));

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Rule compilation errors: " +
                kieBuilder.getResults().toString());
        }

        return kieServices.newKieContainer(
            kieBuilder.getKieModule().getReleaseId()
        );
    }
}
