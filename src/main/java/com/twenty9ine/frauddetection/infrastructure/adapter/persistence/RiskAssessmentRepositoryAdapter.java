package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.domain.port.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper.RiskAssessmentMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RiskAssessmentRepositoryAdapter implements RiskAssessmentRepository {

    private final RiskAssessmentJdbcRepository jdbcRepository;
    private final RiskAssessmentMapper mapper;

    public RiskAssessmentRepositoryAdapter(
            RiskAssessmentJdbcRepository jdbcRepository,
            RiskAssessmentMapper mapper) {
        this.jdbcRepository = jdbcRepository;
        this.mapper = mapper;
    }

    @Override
    public RiskAssessment save(RiskAssessment assessment) {
        RiskAssessmentEntity entity = mapper.toEntity(assessment);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        RiskAssessmentEntity saved = jdbcRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RiskAssessment> findByTransactionId(UUID transactionId) {
        return jdbcRepository.findByTransactionId(transactionId)
            .map(mapper::toDomain);
    }

    @Override
    public List<RiskAssessment> findByRiskLevelSince(RiskLevel level, Instant since) {
        return jdbcRepository.findByRiskLevelSince(level.name(), since)
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
}
