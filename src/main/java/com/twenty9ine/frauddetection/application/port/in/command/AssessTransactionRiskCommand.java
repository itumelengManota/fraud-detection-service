package com.twenty9ine.frauddetection.application.port.in.command;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Command for assessing transaction risk.
 * <p>
 * Encapsulates all necessary information required to perform a risk assessment
 * on a transaction. Follows Command pattern and uses validation annotations
 * to ensure data integrity at the application boundary.
 *
 * @author Fraud Detection Team
 */
@Builder
public record AssessTransactionRiskCommand(
        @NotNull(message = "Transaction ID cannot be null")
        UUID transactionId,

        @NotNull(message = "Account ID cannot be null")
        String accountId,

        @NotNull(message = "Amount cannot be null")
        BigDecimal amount,

        @NotNull(message = "Currency cannot be null")
        String currency,

        @NotBlank(message = "Transaction type cannot be empty")
        String type,

        @NotBlank(message = "Channel cannot be empty")
        String channel,

        String merchantId,
        String merchantName,
        String merchantCategory,

        @Valid
        LocationDto location,

        String deviceId,

        @NotNull(message = "Timestamp cannot be null")
        Instant transactionTimestamp
) { }