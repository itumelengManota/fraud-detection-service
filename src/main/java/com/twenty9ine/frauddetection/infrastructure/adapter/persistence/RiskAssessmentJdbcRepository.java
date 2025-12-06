package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RiskAssessmentJdbcRepository extends CrudRepository<RiskAssessmentEntity, UUID> {

    Optional<RiskAssessmentEntity> findByTransactionId(UUID transactionId);

    Page<RiskAssessmentEntity> findByRiskLevelInAndAssessmentTimeGreaterThanEqual(Set<String> riskLevels, Instant assessmentTime, Pageable pageable);
}