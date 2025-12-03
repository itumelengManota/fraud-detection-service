package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.Merchant;
import com.twenty9ine.frauddetection.domain.valueobject.MerchantId;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.MerchantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface MerchantMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "merchantIdToUUID")
    MerchantEntity toEntity(Merchant merchant);

    @Mapping(target = "id", source = "id", qualifiedByName = "stringToMerchantId")
    Merchant toDomain(MerchantEntity entity);

    @Named("merchantIdToUUID")
    default String merchantIdToString(MerchantId id) {
        return id.merchantId();
    }

    @Named("stringToMerchantId")
    default MerchantId stringToMerchantId(String id) {
        return MerchantId.of(id);
    }
}