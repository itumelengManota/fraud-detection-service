package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskAssessmentJdbcRepository
        extends CrudRepository<RiskAssessmentEntity, UUID> {

    @Query("SELECT * FROM risk_assessments WHERE transaction_id = :transactionId")
    Optional<RiskAssessmentEntity> findByTransactionId(@Param("transactionId") UUID transactionId);

    @Query("""
        SELECT * FROM risk_assessments
        WHERE risk_level = :level
        AND assessment_time >= :since
        ORDER BY assessment_time DESC
        """)
    List<RiskAssessmentEntity> findByRiskLevelSince(
        @Param("level") String level,
        @Param("since") Instant since
    );
}
