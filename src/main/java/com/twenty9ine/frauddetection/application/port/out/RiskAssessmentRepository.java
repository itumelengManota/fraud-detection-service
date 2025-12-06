package com.twenty9ine.frauddetection.application.port.out;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.PageRequest;
import com.twenty9ine.frauddetection.domain.valueobject.PagedResult;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RiskAssessmentRepository {
    RiskAssessment save(RiskAssessment assessment);
    Optional<RiskAssessment> findByTransactionId(UUID transactionId);
    PagedResult<RiskAssessment> findByRiskLevelSince(Set<RiskLevel> levels, Instant since, PageRequest pageRequest);
}
