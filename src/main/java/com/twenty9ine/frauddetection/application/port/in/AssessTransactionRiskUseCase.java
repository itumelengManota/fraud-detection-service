package com.twenty9ine.frauddetection.application.port.in;

import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;

/**
 * Input port (Use Case interface) for assessing transaction risk.
 *
 * This represents the primary use case of the Fraud Detection bounded context.
 * Follows the Hexagonal Architecture pattern where application services implement
 * input ports to provide use cases to the outside world.
 *
 * @author Fraud Detection Team
 */
public interface AssessTransactionRiskUseCase {

    /**
     * Assesses the risk of a transaction using ML predictions, rule engine evaluation,
     * velocity checks, and geographic validation.
     *
     * @param command the command containing transaction details to assess
     * @return risk assessment with score, level, and decision
     * @throws IllegalArgumentException if command validation fails
     */
    RiskAssessmentDto assess(AssessTransactionRiskCommand command);
}