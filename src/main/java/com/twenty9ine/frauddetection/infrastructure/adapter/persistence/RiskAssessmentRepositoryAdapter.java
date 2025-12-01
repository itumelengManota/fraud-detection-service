package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper.RiskAssessmentMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RiskAssessmentRepositoryAdapter implements RiskAssessmentRepository {

    private final RiskAssessmentJdbcRepository jdbcRepository;
    private final RiskAssessmentMapper mapper;

    public RiskAssessmentRepositoryAdapter(RiskAssessmentJdbcRepository jdbcRepository, RiskAssessmentMapper mapper) {
        this.jdbcRepository = jdbcRepository;
        this.mapper = mapper;
    }

    @Override
    public RiskAssessment save(RiskAssessment assessment) {
        RiskAssessmentEntity newRiskAssessment = mapper.toEntity(assessment);

        return jdbcRepository.findById(newRiskAssessment.getId())
                             .map(existingRiskAssessment -> {
                                var entityToUpdate = synchronise(existingRiskAssessment, newRiskAssessment);
                                return mapper.toDomain(jdbcRepository.save(entityToUpdate));
                             })
                            .orElseGet(() -> mapper.toDomain(jdbcRepository.save(newRiskAssessment)));
    }

    private static RiskAssessmentEntity synchronise(RiskAssessmentEntity existingRiskAssessment, RiskAssessmentEntity newRiskAssessment) {
        return RiskAssessmentEntity.builder().id(existingRiskAssessment.getId())
                .transactionId(existingRiskAssessment.getTransactionId())
                .riskScoreValue(newRiskAssessment.getRiskScoreValue())
                .riskLevel(newRiskAssessment.getRiskLevel())
                .decision(newRiskAssessment.getDecision())
                .mlPredictionJson(newRiskAssessment.getMlPredictionJson())
                .assessmentTime(newRiskAssessment.getAssessmentTime())
                .createdAt(existingRiskAssessment.getCreatedAt())
                .updatedAt(existingRiskAssessment.getUpdatedAt())
                .revision(existingRiskAssessment.getRevision())
                .ruleEvaluations(newRiskAssessment.getRuleEvaluations())
                .build();
    }

    @Override
    public Optional<RiskAssessment> findByTransactionId(UUID transactionId) {
        return jdbcRepository.findByTransactionId(transactionId)
                             .map(mapper::toDomain);
    }

    @Override
    public List<RiskAssessment> findByRiskLevelSince(RiskLevel level, Instant since) {
        return jdbcRepository.findByRiskLevelAndAssessmentTimeGreaterThanEqualOrderByAssessmentTimeDesc(level.name(), since)
                             .stream()
                             .map(mapper::toDomain)
                             .toList();
    }
}
