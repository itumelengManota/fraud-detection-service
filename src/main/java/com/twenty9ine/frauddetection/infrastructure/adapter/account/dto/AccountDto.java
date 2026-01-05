package com.twenty9ine.frauddetection.infrastructure.adapter.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AccountDto(@NotBlank
                         String accountId,

                         @Valid
                         @NotNull
                         LocationDto homeLocation,

                         @JsonProperty("accountCreatedAt")
                         Instant createdAt) { }
