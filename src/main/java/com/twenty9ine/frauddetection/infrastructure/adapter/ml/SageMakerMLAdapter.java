package com.twenty9ine.frauddetection.infrastructure.adapter.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.application.port.out.AccountServicePort;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.AccountProfile;
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
    private final AccountServicePort accountService;
    private final String endpointName;
    private final String modelVersion;

    public SageMakerMLAdapter(
            SageMakerRuntimeClient sageMakerClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper,
            AccountServicePort accountService,
            @Value("${aws.sagemaker.endpoint-name}") String endpointName,
            @Value("${aws.sagemaker.model-version:1.0.0}") String modelVersion) {
        this.sageMakerClient = sageMakerClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("sagemakerML");
        this.objectMapper = objectMapper;
        this.accountService = accountService;
        this.endpointName = endpointName;
        this.modelVersion = modelVersion;
    }

    @Override
    @Cacheable(value = "mlPredictions", key = "#transaction.id().toString()", unless = "#result.fraudProbability() > 0.7")
    public MLPrediction predict(Transaction transaction) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                log.debug("Invoking SageMaker endpoint: {} for transaction: {}", endpointName, transaction.id());

                AccountProfile accountProfile = accountService.findAccountProfile(transaction.accountId());

                // Extract and serialize features (now includes home location)
                Map<String, Object> features = extractFeatures(transaction, accountProfile);
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

    private Map<String, Object> extractFeatures(Transaction transaction, AccountProfile accountProfile) {
        Map<String, Object> features = new HashMap<>();

        // Transaction basics
        features.put("transaction_amount", transaction.amount().value().doubleValue());
        features.put("transaction_currency", transaction.amount().currency().getCurrencyCode());
        features.put("transaction_type", transaction.type().name());
        features.put("channel", transaction.channel().name());

        // Merchant information
        if (transaction.merchant() != null) {
            features.put("merchant_category", transaction.merchant().category().name());
            features.put("merchant_id", transaction.merchant().id().merchantId());
        } else {
            features.put("merchant_category", "UNKNOWN");
            features.put("merchant_id", "UNKNOWN");
        }

        // Temporal features
        features.put("hour_of_day", transaction.timestamp().atZone(java.time.ZoneOffset.UTC).getHour());
        features.put("day_of_week", transaction.timestamp().atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue());

        // Transaction location features
        features.put("has_location", transaction.location() != null);
        if (transaction.location() != null) {
            features.put("transaction_latitude", transaction.location().latitude());
            features.put("transaction_longitude", transaction.location().longitude());
            features.put("transaction_country", transaction.location().country() != null ?
                    transaction.location().country() : "UNKNOWN");
        } else {
            features.put("transaction_latitude", 0.0);
            features.put("transaction_longitude", 0.0);
            features.put("transaction_country", "UNKNOWN");
        }

        // HOME LOCATION FEATURES (NEW)
        boolean hasHomeLocation = accountProfile.homeLocation() != null;
        features.put("has_home_location", hasHomeLocation);

        if (hasHomeLocation && transaction.location() != null) {
            // Distance from home location to transaction location
            double distanceFromHome = accountProfile.distanceFromHome(transaction.location());
            features.put("distance_from_home_km", distanceFromHome);

            // Whether transaction is in home country
            boolean isInHomeCountry = accountProfile.isInHomeCountry(transaction.location());
            features.put("is_in_home_country", isInHomeCountry);

            // Home location coordinates (for model to learn patterns)
            features.put("home_latitude", accountProfile.homeLocation().latitude());
            features.put("home_longitude", accountProfile.homeLocation().longitude());
//            features.put("home_country", accountProfile.homeCountry());

            // Account age (older accounts may have different risk profiles)
            if (accountProfile.accountCreatedAt() != null) {
                long accountAgeDays = java.time.Duration.between(
                        accountProfile.accountCreatedAt(),
                        transaction.timestamp()
                ).toDays();
                features.put("account_age_days", accountAgeDays);
            } else {
                features.put("account_age_days", 0);
            }
        } else {
            // Default values when home location not available
            features.put("distance_from_home_km", -1.0);  // -1 indicates unknown
            features.put("is_in_home_country", false);
            features.put("home_latitude", 0.0);
            features.put("home_longitude", 0.0);
            features.put("home_country", "UNKNOWN");
            features.put("account_age_days", 0);
        }

        // Device information
        features.put("has_device", transaction.deviceId() != null);

        log.debug("Extracted {} features for transaction {}, including home location distance: {} km",
                features.size(), transaction.id(), features.get("distance_from_home_km"));

        return features;
    }

    private MLPrediction parsePrediction(String responseBody) throws Exception {
        double[] response = objectMapper.readValue(responseBody, double[].class);

        if (response.length == 0) {
            log.warn("Empty response from SageMaker, using fallback");
            return fallbackPrediction();
        }

        double fraudProbability = response[0];
        double confidence = 0.95;

        return new MLPrediction(
                endpointName,
                modelVersion,
                fraudProbability,
                confidence,
                Map.of() // Empty feature importance for simple predictions
        );
    }

    private MLPrediction fallbackPrediction() {
        return MLPrediction.unavailable();
    }
}