package com.twenty9ine.frauddetection.application.port.in;

import com.twenty9ine.frauddetection.application.port.in.command.ProcessTransactionCommand;

/**
 * Input port (Use Case interface) for processing incoming transactions.
 *
 * This use case handles the complete flow of processing a transaction event:
 * - Risk assessment
 * - Counter updates
 * - Event publishing
 *
 * @author Fraud Detection Team
 */
public interface ProcessTransactionUseCase {

    /**
     * Processes an incoming transaction through the fraud detection pipeline.
     *
     * @param command the transaction to process
     */
    void process(ProcessTransactionCommand command);
}