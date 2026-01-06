package com.twenty9ine.frauddetection.infrastructure.adapter.ml;

import com.twenty9ine.frauddetection.application.port.out.AccountServicePort;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SageMakerMLAdapter implements MLServicePort {

    private final SageMakerRuntimeClient sageMakerClient;
    private final CircuitBreaker circuitBreaker;
    private final JsonMapper jsonMapper;
    private final AccountServicePort accountService;
    private final TransactionRepository transactionRepository;
    private final String endpointName;
    private final String modelVersion;
    private final boolean localMode;
    private final String localEndpointUrl;
    private final RestClient restClient;

    private final double minRawProbability;
    private final double maxRawProbability;

    private List<Transaction> last24HoursTransactions;

    public SageMakerMLAdapter(
            SageMakerRuntimeClient sageMakerClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            JsonMapper jsonMapper,
            AccountServicePort accountService,
            TransactionRepository transactionRepository,
            @Value("${aws.sagemaker.endpoint-name}") String endpointName,
            @Value("${aws.sagemaker.model-version:1.0.0}") String modelVersion,
            @Value("${aws.sagemaker.local-mode:true}") boolean localMode,
            @Value("${aws.sagemaker.endpoint-url:http://localhost:8080/invocations}") String localEndpointUrl,
            @Value("${aws.sagemaker.scaling.min-raw-probability:0.000001}") double minRawProbability,
            @Value("${aws.sagemaker.scaling.max-raw-probability:0.1}") double maxRawProbability) {

        this.sageMakerClient = sageMakerClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("sagemakerML");
        this.jsonMapper = jsonMapper;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.endpointName = endpointName;
        this.modelVersion = modelVersion;
        this.localMode = localMode;
        this.localEndpointUrl = localEndpointUrl;

        this.minRawProbability = minRawProbability;
        this.maxRawProbability = maxRawProbability;

        // Initialize RestClient for local mode
        this.restClient = RestClient.builder()
                .baseUrl(localEndpointUrl)
                .build();

        log.info("SageMakerMLAdapter initialized in {} mode", localMode ? "LOCAL" : "CLOUD");
        if (localMode) {
            log.info("Local endpoint URL: {}", localEndpointUrl);
        } else {
            log.info("Cloud endpoint name: {}", endpointName);
        }
    }

    @Override
    @Cacheable(value = "mlPredictions", key = "#transaction.id().toString()", unless = "#result.fraudProbability() > 0.7")
    public MLPrediction predict(Transaction transaction) {
        this.last24HoursTransactions = findLast24HoursTransactionsByAccountId(transaction.accountId());

        return circuitBreaker.executeSupplier(() -> {
            try {
                log.debug("Invoking SageMaker endpoint: {} for transaction: {}", endpointName, transaction.id());

                AccountProfile accountProfile = findAccountProfileByAccountId(transaction.accountId());
                Map<String, Object> features = extractFeatures(transaction, accountProfile);

                String responseBody;
                if (localMode) {
                    responseBody = invokeLocalEndpoint(features);
                } else {
                    InvokeEndpointResponse response = invokeCloudEndpoint(features);
                    responseBody = toString(response);
                }

                return parsePrediction(responseBody);

            } catch (Exception e) {
                log.warn("SageMaker prediction failed for transaction: {}, using fallback", transaction.id(), e);
                return fallbackPrediction();
            }
        });
    }

    /**
     * Invoke local SageMaker endpoint using direct HTTP call
     */
    private String invokeLocalEndpoint(Map<String, Object> features) {
        try {
            String requestBody = jsonMapper.writeValueAsString(features);
            log.debug("Invoking local endpoint: {} with payload: {}", localEndpointUrl, requestBody);

            return restClient.post()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to invoke local endpoint", e);
            throw new RuntimeException("Local endpoint invocation failed", e);
        }
    }

    /**
     * Invoke cloud SageMaker endpoint using AWS SDK
     */
    private InvokeEndpointResponse invokeCloudEndpoint(Map<String, Object> features) {
        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                .endpointName(endpointName)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(jsonMapper.writeValueAsString(features)))
                .build();

        return sageMakerClient.invokeEndpoint(request);
    }

    private static String toString(InvokeEndpointResponse response) {
        return response.body().asUtf8String();
    }

    private AccountProfile findAccountProfileByAccountId(String accountId) {
        return accountService.findAccountProfile(accountId);
    }

    private Map<String, Object> extractFeatures(Transaction transaction, AccountProfile accountProfile) {
        Map<String, Object> features = new HashMap<>();

        features.put("amount", transaction.amount().value().doubleValue());
        features.put("transaction_type", transaction.type().ordinal());
        features.put("channel", transaction.channel().ordinal());
        features.put("merchant_category", transaction.merchant().category().ordinal());
        features.put("hour", extractHour(transaction));
        features.put("day_of_week", transaction.timestamp().atZone(java.time.ZoneOffset.UTC).getDayOfWeek().ordinal());
        features.put("is_domestic", transaction.location().isDomestic() ? 1 : 0);
        features.put("is_weekend", isWeekend(transaction.timestamp()) ? 1 : 0);
        features.put("has_device", hasDevice(transaction) ? 1 : 0);
        features.put("distance_from_home", calculateDistanceFromHome(accountProfile, transaction));
        features.put("transactions_last_24h", last24HoursTransactions.size() + 1);
        features.put("amount_last_24h", sumTotalAmount(transaction.amount()));
        features.put("new_merchant", isNewMerchant(findLast30DaysMerchantsByAccountId(transaction.accountId()), transaction.merchant()) ? 1 : 0);

        log.debug("Extracted features for transaction {}: {}", transaction.id(), features);

        return features;
    }

    private boolean isNewMerchant(List<Merchant> previousMerchants, Merchant merchant) {
        return !previousMerchants.contains(merchant);
    }

    private List<Merchant> findLast30DaysMerchantsByAccountId(String accountId) {
        return findLast30DaysTransactionsByAccountId(accountId).stream()
                .map(Transaction::merchant)
                .distinct()
                .toList();
    }

    private double sumTotalAmount(Money amount) {
        return getLast24HoursTransactionSumAmount() + amount.value().doubleValue();
    }

    private double getLast24HoursTransactionSumAmount() {
        return last24HoursTransactions.stream()
                .mapToDouble(transaction -> transaction.amount().value().doubleValue())
                .sum();
    }

    private List<Transaction> findLast24HoursTransactionsByAccountId(String accountId) {
        return transactionRepository.findByAccountIdAndTimestampBetween(accountId, Instant.now().minus(24, ChronoUnit.HOURS), Instant.now());
    }

    private List<Transaction> findLast30DaysTransactionsByAccountId(String accountId) {
        return transactionRepository.findByAccountIdAndTimestampBetween(accountId, Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now());
    }

    private static double calculateDistanceFromHome(AccountProfile accountProfile, Transaction transaction) {
        return accountProfile.homeLocation().distanceFrom(transaction.location());
    }

    private static boolean hasDevice(Transaction transaction) {
        return transaction.deviceId() != null && !transaction.deviceId().isBlank();
    }

    private static boolean isWeekend(Instant timestamp) {
        return timestamp.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue() >= 6;
    }

    private static int extractHour(Transaction transaction) {
        return transaction.timestamp().atZone(java.time.ZoneOffset.UTC).getHour();
    }

    private MLPrediction parsePrediction(String responseBody) {
        double[] response = getResponse(responseBody);

        if (response.length == 0) {
            log.warn("Empty response from SageMaker, using fallback");
            return fallbackPrediction();
        }

        double rawProbability = response[0];
        double fraudProbability = scaleRawProbability(rawProbability);

        log.debug("Raw probability: {}, Scaled fraud probability: {}",
                String.format("%.8f", rawProbability),
                String.format("%.4f", fraudProbability));

        return new MLPrediction(endpointName, modelVersion, fraudProbability, 0.95, Map.of());
    }

    private double scaleRawProbability(double rawProbability) {
        double logValue = Math.log(rawProbability + 1e-10);
        double minLog = Math.log(minRawProbability);
        double maxLog = Math.log(maxRawProbability);
        double scaled = (logValue - minLog) / (maxLog - minLog);

        return Math.clamp(scaled, 0.0, 1.0);
    }

    private double[] getResponse(String responseBody) {
        return jsonMapper.readValue(responseBody, double[].class);
    }

    private MLPrediction fallbackPrediction() {
        return MLPrediction.unavailable();
    }
}