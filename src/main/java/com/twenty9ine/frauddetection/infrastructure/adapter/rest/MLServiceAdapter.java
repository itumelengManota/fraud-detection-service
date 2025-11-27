package com.twenty9ine.frauddetection.infrastructure.adapter.rest;

import com.twenty9ine.frauddetection.domain.model.MLPrediction;
import com.twenty9ine.frauddetection.domain.model.Transaction;
import com.twenty9ine.frauddetection.domain.port.MLServicePort;
import com.twenty9ine.frauddetection.infrastructure.adapter.rest.dto.FeatureVector;
import com.twenty9ine.frauddetection.infrastructure.adapter.rest.dto.MLPredictionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MLServiceAdapter implements MLServicePort {

    private final MLInferenceClient mlClient;
    private final CircuitBreaker circuitBreaker;

    public MLServiceAdapter(
            MLInferenceClient mlClient,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.mlClient = mlClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("mlService");
    }

    @Override
    public MLPrediction predict(Transaction transaction) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                FeatureVector features = extractFeatures(transaction);
                MLPredictionResponse response = mlClient.predict(features);
                return new MLPrediction(
                    response.modelId(),
                    response.modelVersion(),
                    response.fraudProbability(),
                    response.confidence(),
                    response.featureImportance()
                );
            } catch (Exception e) {
                log.warn("ML service call failed, using fallback", e);
                return fallbackPrediction(transaction);
            }
        });
    }

    private FeatureVector extractFeatures(Transaction transaction) {
        return FeatureVector.builder()
            .amount(transaction.amount().amount())
            .merchantCategory(transaction.merchantCategory())
            .accountAge(0)
            .build();
    }

    private MLPrediction fallbackPrediction(Transaction transaction) {
        return MLPrediction.unavailable();
    }
}
