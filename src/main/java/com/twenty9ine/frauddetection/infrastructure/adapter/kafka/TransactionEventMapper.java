package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.port.in.command.ProcessTransactionCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class TransactionEventMapper {

    public ProcessTransactionCommand toCommand(TransactionAvro avroTransaction) {
        return ProcessTransactionCommand.builder()
                .transactionId(UUID.fromString(avroTransaction.getTransactionId()))
                .accountId(avroTransaction.getAccountId())
                .amount(avroTransaction.getAmount())
                .currency(avroTransaction.getCurrency())
                .type(avroTransaction.getType())
                .channel(avroTransaction.getChannel())
                .merchantId(avroTransaction.getMerchant().getId())
                .merchantName(avroTransaction.getMerchant().getName())
                .merchantCategory(avroTransaction.getMerchant().getCategory())
                .location(toDto(avroTransaction.getLocation()))
                .deviceId(avroTransaction.getDeviceId())
                .transactionTimestamp(avroTransaction.getTimestamp())
                .build();
    }

    private LocationDto toDto(LocationAvro locationAvro) {
        if (locationAvro == null) return null;

        return new LocationDto(locationAvro.getLatitude(), locationAvro.getLongitude(), locationAvro.getCountry(),
                locationAvro.getCity());
    }
}
