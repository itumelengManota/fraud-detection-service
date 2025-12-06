package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.PageRequest;
import com.twenty9ine.frauddetection.domain.valueobject.PagedResult;
import com.twenty9ine.frauddetection.domain.valueobject.RiskLevel;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.domain.valueobject.SortDirection;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper.RiskAssessmentMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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
                             .map(existingRiskAssessment -> mapper.toDomain(jdbcRepository.save(synchronise(existingRiskAssessment, newRiskAssessment))))
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
    public PagedResult<RiskAssessment> findByRiskLevelSince(Set<RiskLevel> levels, Instant since, PageRequest pageRequest) {

        Page<RiskAssessmentEntity> page = jdbcRepository
                .findByRiskLevelInAndAssessmentTimeGreaterThanEqual(toRiskLevelStrings(levels), since, toSpringPageable(pageRequest));

        return toPagedResult(toRiskAssessments(page), page);
    }

    private static PagedResult<RiskAssessment> toPagedResult(List<RiskAssessment> assessments, Page<RiskAssessmentEntity> page) {
        return PagedResult.of(
                assessments,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    private List<RiskAssessment> toRiskAssessments(Page<RiskAssessmentEntity> page) {
        return page.getContent()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    private static Set<String> toRiskLevelStrings(Set<RiskLevel> levels) {
        return resolveRiskLevels(levels).stream()
                .map(RiskLevel::name)
                .collect(Collectors.toSet());
    }

    private Pageable toSpringPageable(PageRequest pageRequest) {
        if (pageRequest == null) {
            return Pageable.unpaged();
        }

        return org.springframework.data.domain.PageRequest.of(pageRequest.pageNumber(), pageRequest.pageSize(), toSpringSort(pageRequest));
    }

    private static Sort toSpringSort(PageRequest pageRequest) {
        return pageRequest.sortDirection() == SortDirection.ASC
                ? Sort.by(pageRequest.sortBy()).ascending()
                : Sort.by(pageRequest.sortBy()).descending();
    }

    private static Set<RiskLevel> resolveRiskLevels(Set<RiskLevel> levels) {
        return (levels == null || levels.isEmpty()) ? getDefaultRiskLevels(): levels;
    }

    private static Set<RiskLevel> getDefaultRiskLevels() {
        return Arrays.stream(RiskLevel.values()).collect(Collectors.toSet());
    }
}
