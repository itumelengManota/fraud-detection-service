package com.twenty9ine.frauddetection.application.port.out;

import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(TransactionId transactionId);

    List<Transaction> findByAccountId(String accountId);

    List<Transaction> findByAccountIdAndTimestampBetween(String accountId, Instant start, Instant end);

    Optional<Transaction> findEarliestByAccountId(String accountId);

    void deleteById(TransactionId transactionId);

    boolean existsById(TransactionId transactionId);
}