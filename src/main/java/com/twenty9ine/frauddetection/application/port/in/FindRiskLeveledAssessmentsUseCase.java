package com.twenty9ine.frauddetection.application.port.in;

import com.twenty9ine.frauddetection.application.dto.PagedResultDto;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.query.FindRiskLeveledAssessmentsQuery;
import com.twenty9ine.frauddetection.application.port.in.query.PageRequestQuery;

/**
 * Input port (Use Case interface) for finding high-risk assessments.
 *
 * Query use case for retrieving risk assessments filtered by risk level
 * and time period. Useful for monitoring, reporting, and alerting purposes.
 *
 * @author Ignatius Itumeleng Manota
 */
public interface FindRiskLeveledAssessmentsUseCase {

    /**
     * Finds all risk assessments matching the specified risk level from a given time.
     *
     * @param query the query containing risk level and time criteria
     * @return list of matching risk assessments, ordered by assessment time descending
     */
    PagedResultDto<RiskAssessmentDto> find(FindRiskLeveledAssessmentsQuery query, PageRequestQuery pageRequestQuery);
}