package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import org.springframework.data.relational.core.mapping.Table;

@Table("merchant")
public record MerchantEntity(String id, String name, String category) {
}