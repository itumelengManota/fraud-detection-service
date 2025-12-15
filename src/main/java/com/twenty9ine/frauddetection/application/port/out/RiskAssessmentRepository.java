package com.twenty9ine.frauddetection.application.port.out;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.PageRequest;
import com.twenty9ine.frauddetection.domain.valueobject.PagedResult;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface RiskAssessmentRepository {
    RiskAssessment save(RiskAssessment assessment);
    Optional<RiskAssessment> findByTransactionId(TransactionId transactionId);
    PagedResult<RiskAssessment> findByRiskLevelSince(Set<TransactionRiskLevel> levels, Instant since, PageRequest pageRequest);
}
