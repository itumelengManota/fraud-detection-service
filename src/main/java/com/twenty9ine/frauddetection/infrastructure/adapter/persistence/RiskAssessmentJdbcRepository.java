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

    /**
     * Find risk assessments by risk levels and assessment time greater than or equal.
     *
     * @param riskLevels Set of risk levels to filter by
     * @param assessmentTime Minimum assessment timestamp
     * @param pageable Pagination information
     * @return Page of matching risk assessments
     */
    Page<RiskAssessmentEntity> findByRiskLevelInAndAssessmentTimeGreaterThanEqual(
            Set<String> riskLevels,
            Instant assessmentTime,
            Pageable pageable
    );

    Page<RiskAssessmentEntity> findByAssessmentTimeGreaterThanEqual(
            Instant assessmentTime,
            Pageable pageable
    );

    Page<RiskAssessmentEntity> findAll(Pageable pageable);

    Page<RiskAssessmentEntity> findByRiskLevelIn(Set<String> riskLevels, Pageable pageable);


}