package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Table("transactions")
public class TransactionEntity {
    @Id
    private UUID id;
    private String accountId;
    private BigDecimal amountValue;
    private String amountCurrency;
    private String type;
    private String channel;
    private MerchantEntity merchant;
    private DeviceEntity device;
    private LocationEntity location;
    private Instant timestamp;
}