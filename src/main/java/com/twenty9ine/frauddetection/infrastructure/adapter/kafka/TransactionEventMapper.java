package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventMapper {

    public Transaction toDomain(byte[] payload) {
        return null;
    }
}
