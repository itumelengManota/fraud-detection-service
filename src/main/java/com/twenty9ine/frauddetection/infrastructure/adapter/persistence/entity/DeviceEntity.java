package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("devices")
public class DeviceEntity {
    @Id
    private UUID id;
    private String deviceId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
}