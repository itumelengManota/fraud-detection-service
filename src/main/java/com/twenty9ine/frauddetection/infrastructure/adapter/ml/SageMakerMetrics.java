package com.twenty9ine.frauddetection.infrastructure.adapter.ml;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "aws.sagemaker.enabled", havingValue = "true")
@Component
public class SageMakerMetrics {

    private final Timer predictionTimer;
    private final MeterRegistry registry;

    public SageMakerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.predictionTimer = Timer.builder("sagemaker.prediction.duration")
                .description("Time taken for SageMaker predictions")
                .tag("endpoint", "fraud-detection")
                .register(registry);
    }

    public <T> T recordPrediction(java.util.function.Supplier<T> supplier) {
        return predictionTimer.record(supplier);
    }

    public void recordPredictionError(String errorType) {
        registry.counter("sagemaker.prediction.errors",
                "error_type", errorType).increment();
    }
}