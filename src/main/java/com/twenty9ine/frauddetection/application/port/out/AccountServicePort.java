package com.twenty9ine.frauddetection.application.port.out;

import com.twenty9ine.frauddetection.domain.valueobject.AccountProfile;

/**
 * Output port for retrieving account holder information from Account Service.
 *
 * This port represents the anti-corruption layer between Fraud Detection
 * and Account Management bounded contexts. Implementations should handle:
 * - Service communication (REST, gRPC, etc.)
 * - Data transformation from Account model to Fraud Detection model
 * - Error handling and fallbacks
 * - Caching for performance
 *
 * @author Ignatius Itumeleng Manota
 */
public interface AccountServicePort {

    /**
     * Retrieves account profile information for fraud detection analysis.
     *
     * @param accountId the account identifier
     * @return optional account profile, empty if account not found or service unavailable
     */
    AccountProfile findAccountProfile(String accountId);
}