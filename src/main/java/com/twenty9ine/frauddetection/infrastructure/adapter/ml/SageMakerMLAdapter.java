package com.twenty9ine.frauddetection.infrastructure.adapter.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.MLPrediction;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

import java.util.HashMap;
import java.util.Map;

@ConditionalOnProperty(name = "aws.sagemaker.enabled", havingValue = "true")
@Component
@Slf4j
public class SageMakerMLAdapter implements MLServicePort {

    private final SageMakerRuntimeClient sageMakerClient;
    private final CircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;
    private final String endpointName;
    private final String modelVersion;

    public SageMakerMLAdapter(
            SageMakerRuntimeClient sageMakerClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper,
            @Value("${aws.sagemaker.endpoint-name}") String endpointName,
            @Value("${aws.sagemaker.model-version:1.0.0}") String modelVersion) {
        this.sageMakerClient = sageMakerClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("sagemakerML");
        this.objectMapper = objectMapper;
        this.endpointName = endpointName;
        this.modelVersion = modelVersion;
    }

    @Override
    @Cacheable(value = "mlPredictions", key = "#transaction.id().toString()", unless = "#result.fraudProbability() > 0.7")
    public MLPrediction predict(Transaction transaction) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                log.debug("Invoking SageMaker endpoint: {} for transaction: {}",
                        endpointName, transaction.id());

                // Extract and serialize features
                Map<String, Object> features = extractFeatures(transaction);
                String requestBody = objectMapper.writeValueAsString(features);

                // Invoke SageMaker endpoint
                InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                        .endpointName(endpointName)
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(requestBody))
                        .build();

                InvokeEndpointResponse response = sageMakerClient.invokeEndpoint(request);

                // Parse response
                String responseBody = response.body().asUtf8String();
                return parsePrediction(responseBody);

            } catch (Exception e) {
                log.warn("SageMaker prediction failed for transaction: {}, using fallback",
                        transaction.id(), e);
                return fallbackPrediction();
            }
        });
    }

    private Map<String, Object> extractFeatures(Transaction transaction) {
        Map<String, Object> features = new HashMap<>();

        // Transaction basics
        features.put("transaction_amount", transaction.amount().value().doubleValue());
        features.put("transaction_currency", transaction.amount().currency().getCurrencyCode());
        features.put("transaction_type", transaction.type().name());
        features.put("channel", transaction.channel().name());

        // Merchant information
        if (transaction.merchant() != null) {
            features.put("merchant_category", transaction.merchant().category());
            features.put("merchant_id", transaction.merchant().id().merchantId());
        } else {
            features.put("merchant_category", "UNKNOWN");
            features.put("merchant_id", "UNKNOWN");
        }

        // Temporal features
        features.put("hour_of_day", transaction.timestamp().atZone(java.time.ZoneOffset.UTC).getHour());
        features.put("day_of_week", transaction.timestamp().atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue());

        // Location features
        features.put("has_location", transaction.location() != null);
        if (transaction.location() != null) {
            features.put("latitude", transaction.location().latitude());
            features.put("longitude", transaction.location().longitude());
            features.put("country", transaction.location().country() != null ?
                    transaction.location().country() : "UNKNOWN");
        } else {
            features.put("latitude", 0.0);
            features.put("longitude", 0.0);
            features.put("country", "UNKNOWN");
        }

        // Device information
        features.put("has_device", transaction.deviceId() != null);

        return features;
    }

    private MLPrediction parsePrediction(String responseBody) throws Exception {
        // Parse SageMaker response format
        // Adjust based on your model's output format
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        double fraudProbability = ((Number) response.get("fraud_probability")).doubleValue();
        double confidence = response.containsKey("confidence") ?
                ((Number) response.get("confidence")).doubleValue() : 0.95;

        @SuppressWarnings("unchecked")
        Map<String, Double> featureImportance = response.containsKey("feature_importance") ?
                (Map<String, Double>) response.get("feature_importance") : Map.of();

        return new MLPrediction(
                endpointName,
                modelVersion,
                fraudProbability,
                confidence,
                featureImportance
        );
    }

    private MLPrediction fallbackPrediction() {
        return MLPrediction.unavailable();
    }
}