package com.twenty9ine.frauddetection.domain.port;

import com.twenty9ine.frauddetection.domain.model.RiskAssessment;
import com.twenty9ine.frauddetection.domain.model.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskAssessmentRepository {
    RiskAssessment save(RiskAssessment assessment);
    Optional<RiskAssessment> findByTransactionId(UUID transactionId);
    List<RiskAssessment> findByRiskLevelSince(RiskLevel level, Instant since);
}
