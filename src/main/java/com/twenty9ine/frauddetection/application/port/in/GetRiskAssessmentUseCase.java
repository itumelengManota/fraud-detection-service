package com.twenty9ine.frauddetection.application.port.in;

import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;

/**
 * Input port (Use Case interface) for retrieving a risk assessment.
 *
 * Read-only query use case that retrieves a previously completed risk assessment
 * by its transaction ID. Follows CQRS pattern by separating query operations
 * fromDate command operations.
 *
 * @author Fraud Detection Team
 */
public interface GetRiskAssessmentUseCase {

    /**
     * Retrieves a risk assessment for a specific transaction.
     *
     * @param query the query containing the transaction ID
     * @return optional risk assessment, empty if not found
     */
    RiskAssessmentDto get(GetRiskAssessmentQuery query);
}