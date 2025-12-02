package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.*;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Currency;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {LocationMapper.class, MerchantMapper.class}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TransactionMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "transactionIdToUUID")
    @Mapping(target = "amountValue", source = "amount.value")
    @Mapping(target = "amountCurrency", source = "amount.currency", qualifiedByName = "currencyToCode")
    @Mapping(target = "type", source = "type", qualifiedByName = "enumToString")
    @Mapping(target = "channel", source = "channel", qualifiedByName = "enumToString")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "revision", ignore = true)
    TransactionEntity toEntity(Transaction transaction);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToTransactionId")
    @Mapping(target = "amount", expression = "java(new Money(entity.amountValue(), Currency.getInstance(entity.amountCurrency())))")
    @Mapping(target = "type", source = "type", qualifiedByName = "stringToTransactionType")
    @Mapping(target = "channel", source = "channel", qualifiedByName = "stringToChannel")
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
}