package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.TransactionEntity;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class TransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJdbcRepository jdbcRepository;
    private final TransactionMapper mapper;

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = mapper.toEntity(transaction);
        TransactionEntity saved = jdbcRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Transaction> findById(TransactionId transactionId) {
        return jdbcRepository.findById(transactionId.toUUID())
                .map(mapper::toDomain);
    }

    @Override
    public List<Transaction> findByAccountId(String accountId) {
        return jdbcRepository.findByAccountId(accountId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findByAccountIdAndTimestampBetween(String accountId, Instant start, Instant end) {
        return jdbcRepository.findByAccountIdAndTimestampBetween(accountId, start, end).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Transaction> findEarliestByAccountId(String accountId) {
        return jdbcRepository.findFirstByAccountIdOrderByTimestampAsc(accountId)
                .map(mapper::toDomain);
    }

    @Override
    public void deleteById(TransactionId transactionId) {
        jdbcRepository.deleteById(transactionId.toUUID());
    }

    @Override
    public boolean existsById(TransactionId transactionId) {
        return jdbcRepository.existsById(transactionId.toUUID());
    }
}