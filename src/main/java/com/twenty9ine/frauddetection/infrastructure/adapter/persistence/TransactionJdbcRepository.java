package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.TransactionEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionJdbcRepository extends CrudRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByAccountId(String accountId);

    List<TransactionEntity> findByAccountIdAndTimestampBetween(String accountId, Instant start, Instant end);

    Optional<TransactionEntity> findFirstByAccountIdOrderByTimestampAsc(String accountId);

    Optional<TransactionEntity> findFirstByAccountIdOrderByTimestampDesc(String accountId);
}