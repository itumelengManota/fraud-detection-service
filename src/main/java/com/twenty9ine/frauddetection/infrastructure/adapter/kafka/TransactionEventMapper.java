package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventMapper {

    public Transaction toDomain(byte[] payload) {
        //TODO: Implement the mapping logic from payload to Transaction domain object
        return null;
    }
}
