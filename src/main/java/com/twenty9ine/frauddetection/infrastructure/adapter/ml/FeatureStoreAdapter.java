package com.twenty9ine.frauddetection.infrastructure.adapter.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sagemakerfeaturestoreruntime.SageMakerFeatureStoreRuntimeClient;
import software.amazon.awssdk.services.sagemakerfeaturestoreruntime.model.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "aws.sagemaker.enabled", havingValue = "true")
@Slf4j
public class FeatureStoreAdapter {

    private final SageMakerFeatureStoreRuntimeClient featureStoreClient;

    public FeatureStoreAdapter(SageMakerFeatureStoreRuntimeClient featureStoreClient) {
        this.featureStoreClient = featureStoreClient;
    }

    public Map<String, String> getFeatures(String featureGroupName, String recordId) {
        try {
            GetRecordRequest request = GetRecordRequest.builder()
                    .featureGroupName(featureGroupName)
                    .recordIdentifierValueAsString(recordId)
                    .build();

            GetRecordResponse response = featureStoreClient.getRecord(request);

            return response.record().stream()
                    .collect(Collectors.toMap(
                            FeatureValue::featureName,
                            FeatureValue::valueAsString
                    ));
        } catch (Exception e) {
            log.error("Failed to get features from Feature Store", e);
            throw new RuntimeException("Feature retrieval failed", e);
        }
    }

    public void putFeatures(String featureGroupName, List<FeatureValue> features) {
        try {
            PutRecordRequest request = PutRecordRequest.builder()
                    .featureGroupName(featureGroupName)
                    .record(features)
                    .build();

            featureStoreClient.putRecord(request);
            log.debug("Successfully wrote {} features to group {}", features.size(), featureGroupName);
        } catch (Exception e) {
            log.error("Failed to put features to Feature Store", e);
            throw new RuntimeException("Feature storage failed", e);
        }
    }
}