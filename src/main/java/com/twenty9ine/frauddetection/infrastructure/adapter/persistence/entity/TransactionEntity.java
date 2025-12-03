package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
@Table("transaction")
public record TransactionEntity(@Id
                                UUID id,
                                String accountId,
                                BigDecimal amountValue,
                                String amountCurrency,
                                String type,
                                String channel,

                                @MappedCollection(idColumn = "transaction_id")
                                MerchantEntity merchant,
                                String deviceId,

                                @MappedCollection(idColumn = "transaction_id")
                                LocationEntity location,
                                Instant timestamp,

                                @NotNull
                                @CreatedDate
                                Instant createdAt,

                                @NotNull
                                @LastModifiedDate
                                Instant updatedAt,

                                @Version
                                int revision) {

}