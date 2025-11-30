package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("merchants")
public class MerchantEntity {
    @Id
    private UUID id;
    private String merchantId;
    private String name;
    private String category;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}