package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.valueobject.Money;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.SerdeConfig;

import com.twenty9ine.frauddetection.domain.valueobject.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class TransactionEventMapper {

    private final AvroKafkaDeserializer<TransactionAvro> deserializer;

    public TransactionEventMapper() {
        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, "http://apicurio-registry:8080/apis/registry/v2");
        config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, true);
        config.put(SerdeConfig.FIND_LATEST_ARTIFACT, true);

        this.deserializer = new AvroKafkaDeserializer<>();
        this.deserializer.configure(config, false);
    }

    public Transaction toDomain(byte[] payload) {
        try {
            TransactionAvro avroTransaction = deserializer.deserialize(null, payload);
            return toTransaction(avroTransaction);
        } catch (Exception e) {
            log.error("Failed to deserialize transaction event", e);
            throw new IllegalArgumentException("Invalid transaction event payload", e);
        }
    }

    private Transaction toTransaction(TransactionAvro avroTransaction) {
        return Transaction.builder()
                .id(TransactionId.of(UUID.fromString(avroTransaction.getTransactionId())))
                .accountId(avroTransaction.getAccountId())
                .amount(new Money(BigDecimal.valueOf(avroTransaction.getAmount()),
                        Currency.getInstance(avroTransaction.getCurrency())))
                .type(TransactionType.valueOf(avroTransaction.getType()))
                .channel(Channel.valueOf(avroTransaction.getChannel()))
                .merchant(mapMerchant(avroTransaction.getMerchant()))
                .location(mapLocation(avroTransaction.getLocation()))
                .deviceId(avroTransaction.getDeviceId())
                .timestamp(Instant.ofEpochMilli(avroTransaction.getTimestamp()))
                .build();
    }

    private Merchant mapMerchant(MerchantAvro merchantAvro) {
        if (merchantAvro == null) return null;

        return new Merchant(
                MerchantId.of(merchantAvro.getId()),
                merchantAvro.getName(),
                merchantAvro.getCategory()
        );
    }

    private Location mapLocation(LocationAvro locationAvro) {
        if (locationAvro == null) return null;

        return new Location(
                locationAvro.getLatitude(),
                locationAvro.getLongitude(),
                locationAvro.getCountry(),
                locationAvro.getCity(),
                Instant.ofEpochMilli(locationAvro.getTimestamp())
        );
    }
}
