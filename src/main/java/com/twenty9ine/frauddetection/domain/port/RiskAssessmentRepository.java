package com.twenty9ine.frauddetection.domain.port;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskAssessmentRepository {
    RiskAssessment save(RiskAssessment assessment);
    Optional<RiskAssessment> findByTransactionId(UUID transactionId);
    List<RiskAssessment> findByRiskLevelSince(RiskLevel level, Instant since);
}
