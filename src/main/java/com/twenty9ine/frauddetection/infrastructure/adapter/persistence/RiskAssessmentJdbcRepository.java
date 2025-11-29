package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskAssessmentJdbcRepository extends CrudRepository<RiskAssessmentEntity, UUID> {

    Optional<RiskAssessmentEntity> findByTransactionId(UUID transactionId);

    List<RiskAssessmentEntity> findByRiskLevelAndAssessmentTimeGreaterThanEqualOrderByAssessmentTimeDesc(String riskLevel, Instant assessmentTime);
}