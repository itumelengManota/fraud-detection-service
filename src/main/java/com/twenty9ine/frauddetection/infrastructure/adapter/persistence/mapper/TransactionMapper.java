package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Currency;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {LocationMapper.class})
public interface TransactionMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "transactionIdToUUID")
    @Mapping(target = "amountValue", source = "amount.value")
    @Mapping(target = "amountCurrency", source = "amount.currency", qualifiedByName = "currencyToCode")
    @Mapping(target = "type", source = "type", qualifiedByName = "enumToString")
    @Mapping(target = "channel", source = "channel", qualifiedByName = "enumToString")
    @Mapping(target = "merchant", source = ".", qualifiedByName = "toMerchantEntity")
    @Mapping(target = "device", source = "deviceId", qualifiedByName = "toDeviceEntity")
    @Mapping(target = "location", source = "location")
    TransactionEntity toEntity(Transaction transaction);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToTransactionId")
    @Mapping(target = "amount", expression = "java(new Money(entity.getAmountValue(), Currency.getInstance(entity.getAmountCurrency())))")
    @Mapping(target = "type", source = "type", qualifiedByName = "stringToTransactionType")
    @Mapping(target = "channel", source = "channel", qualifiedByName = "stringToChannel")
    @Mapping(target = "merchantId", source = "merchant.merchantId")
    @Mapping(target = "merchantName", source = "merchant.name")
    @Mapping(target = "merchantCategory", source = "merchant.category")
    @Mapping(target = "deviceId", source = "device.deviceId")
    @Mapping(target = "location", source = "location")
    Transaction toDomain(TransactionEntity entity);

    @Named("transactionIdToUUID")
    default UUID transactionIdToUUID(TransactionId id) {
        return id.toUUID();
    }

    @Named("uuidToTransactionId")
    default TransactionId uuidToTransactionId(UUID uuid) {
        return TransactionId.of(uuid);
    }

    @Named("currencyToCode")
    default String currencyToCode(Currency currency) {
        return currency.getCurrencyCode();
    }

    @Named("enumToString")
    default String enumToString(Enum<?> enumValue) {
        return enumValue.name();
    }

    @Named("stringToTransactionType")
    default TransactionType stringToTransactionType(String type) {
        return TransactionType.valueOf(type);
    }

    @Named("stringToChannel")
    default Channel stringToChannel(String channel) {
        return Channel.valueOf(channel);
    }

    @Named("toMerchantEntity")
    default MerchantEntity toMerchantEntity(Transaction transaction) {
        if (transaction.merchantId() == null) return null;
        MerchantEntity entity = new MerchantEntity();
        entity.setMerchantId(transaction.merchantId());
        entity.setName(transaction.merchantName());
        entity.setCategory(transaction.merchantCategory());
        return entity;
    }

    @Named("toDeviceEntity")
    default DeviceEntity toDeviceEntity(String deviceId) {
        if (deviceId == null) return null;
        DeviceEntity entity = new DeviceEntity();
        entity.setDeviceId(deviceId);
        return entity;
    }
}