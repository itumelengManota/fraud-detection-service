package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import org.springframework.stereotype.Component;

@Component
public class DomainEventToAvroMapper {

    public Object toAvro(DomainEvent<?> event) {
        return switch(event) {
            case RiskAssessmentCompleted e -> mapRiskAssessmentCompleted(e);
            case HighRiskDetected e -> mapHighRiskDetected(e);
            default -> throw new IllegalArgumentException("Unsupported event type: " + event.getClass());
        };
    }

    private RiskAssessmentCompletedAvro mapRiskAssessmentCompleted(RiskAssessmentCompleted event) {
        return RiskAssessmentCompletedAvro.newBuilder()
                .setId(event.id().toString())
                .setAssessmentId(event.assessmentId().toString())
                .setFinalScore(event.finalScore().value())
                .setRiskLevel(event.riskLevel().name())
                .setDecision(event.decision().name())
                .setOccurredAt(event.getOccurredAt().toEpochMilli())
                .build();
    }

    private HighRiskDetectedAvro mapHighRiskDetected(HighRiskDetected event) {
        return HighRiskDetectedAvro.newBuilder()
                .setId(event.id().toString())
                .setAssessmentId(event.assessmentId().toString())
                .setRiskLevel(event.riskLevel().name())
                .setOccurredAt(event.getOccurredAt().toEpochMilli())
                .build();
    }
}
