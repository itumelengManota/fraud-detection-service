package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import lombok.Builder;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Builder
@Table("location")
public record LocationEntity(double latitude, double longitude, String country, String city, Instant timestamp) {
}