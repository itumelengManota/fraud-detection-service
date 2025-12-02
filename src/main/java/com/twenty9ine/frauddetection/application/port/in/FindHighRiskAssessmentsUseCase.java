package com.twenty9ine.frauddetection.application.port.in;

import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;

import java.util.List;

/**
 * Input port (Use Case interface) for finding high-risk assessments.
 *
 * Query use case for retrieving risk assessments filtered by risk level
 * and time period. Useful for monitoring, reporting, and alerting purposes.
 *
 * @author Fraud Detection Team
 */
public interface FindHighRiskAssessmentsUseCase {

    /**
     * Finds all risk assessments matching the specified risk level since a given time.
     *
     * @param query the query containing risk level and time criteria
     * @return list of matching risk assessments, ordered by assessment time descending
     */
    List<RiskAssessmentDto> find(FindHighRiskAssessmentsQuery query);
}