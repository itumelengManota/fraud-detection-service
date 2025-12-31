package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisabledInAotMode
@Import({TransactionRepositoryAdapter.class, TransactionMapperImpl.class, LocationMapperImpl.class, MerchantMapperImpl.class})
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("postgres")
class TransactionRepositoryAdapterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgres.start();

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionRepositoryAdapter adapter;

    @Autowired
    private TransactionJdbcRepository jdbcRepository;

    @BeforeEach
    void setUp() {
        jdbcRepository.deleteAll();
    }

    @Test
    void shouldSaveAndFindById() {
        // Given
        Transaction transaction = createTransaction();

        // When
        Transaction saved = adapter.save(transaction);
        Optional<Transaction> found = adapter.findById(saved.id());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.id());
        assertThat(found.get().accountId()).isEqualTo(transaction.accountId());
        assertThat(found.get().type()).isEqualTo(transaction.type());
    }

    @Test
    void shouldReturnEmptyWhenTransactionIdNotFound() {
        // When
        Optional<Transaction> found = adapter.findById(TransactionId.generate());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByAccountId() {
        // Given
        String accountId = "ACC123";
        Transaction transaction1 = createTransactionWithAccountId(accountId);
        Transaction transaction2 = createTransactionWithAccountId(accountId);
        Transaction transaction3 = createTransactionWithAccountId("ACC456");

        adapter.save(transaction1);
        adapter.save(transaction2);
        adapter.save(transaction3);

        // When
        List<Transaction> found = adapter.findByAccountId(accountId);

        // Then
        assertThat(found).hasSize(2)
                .allMatch(t -> t.accountId().equals(accountId));
    }

    @Test
    void shouldReturnEmptyListWhenAccountIdNotFound() {
        // When
        List<Transaction> found = adapter.findByAccountId("NONEXISTENT");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByAccountIdAndTimestampBetween() {
        // Given
        String accountId = "ACC123";
        Instant now = now();
        Instant start = twoHoursEarlier(now);
        Instant end = twoHoursLater(now);

        Transaction inRange1 = createTransactionWithAccountIdAndTime(accountId, now);
        Transaction inRange2 = createTransactionWithAccountIdAndTime(accountId, oneHourLater(now));
        Transaction outOfRange = createTransactionWithAccountIdAndTime(accountId, oneHourEarlier(start));
        Transaction differentAccount = createTransactionWithAccountIdAndTime("ACC456", now);

        adapter.save(inRange1);
        adapter.save(inRange2);
        adapter.save(outOfRange);
        adapter.save(differentAccount);

        // When
        List<Transaction> found = adapter.findByAccountIdAndTimestampBetween(accountId, start, end);

        // Then
        assertThat(found)
                .hasSize(2)
                .allMatch(t -> t.accountId().equals(accountId))
                .allMatch(t -> !t.timestamp().isBefore(start) && !t.timestamp().isAfter(end));
    }

    @Test
    void shouldReturnEmptyListWhenNoTransactionsInTimeRange() {
        // Given
        Instant now = Instant.now();
        Instant futureStart = now.plus(10, ChronoUnit.DAYS);
        Instant futureEnd = now.plus(20, ChronoUnit.DAYS);

        String accountId = "ACC123";
        Transaction transaction = createTransactionWithAccountIdAndTime(accountId, now);
        adapter.save(transaction);

        // When
        List<Transaction> found = adapter.findByAccountIdAndTimestampBetween(accountId, futureStart, futureEnd);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindEarliestByAccountId() {
        // Given
        String accountId = "ACC123";
        Instant now = now();

        Transaction earliest = createTransactionWithAccountIdAndTime(accountId, twoHoursEarlier(now));
        Transaction middle = createTransactionWithAccountIdAndTime(accountId, oneHourEarlier(now));
        Transaction latest = createTransactionWithAccountIdAndTime(accountId, now);

        adapter.save(latest);
        adapter.save(middle);
        adapter.save(earliest);

        // When
        Optional<Transaction> found = adapter.findEarliestByAccountId(accountId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(earliest.id());
        assertThat(found.get().timestamp()).isEqualTo(earliest.timestamp());
    }


    @Test
    void shouldFindLatestByAccountId() {
        // Given
        String accountId = "ACC123";
        Instant now = now();

        Transaction earliest = createTransactionWithAccountIdAndTime(accountId, twoHoursEarlier(now));
        Transaction middle = createTransactionWithAccountIdAndTime(accountId, oneHourEarlier(now));
        Transaction latest = createTransactionWithAccountIdAndTime(accountId, now);

        adapter.save(latest);
        adapter.save(middle);
        adapter.save(earliest);

        // When
        Optional<Transaction> found = adapter.findLatestByAccountId(accountId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(latest.id());
        assertThat(found.get().timestamp()).isEqualTo(latest.timestamp());
    }

    @Test
    void shouldReturnEmptyWhenNoTransactionsForAccount() {
        // Given
        String accountId = "ACC123";
        Transaction transaction = createTransactionWithAccountId(accountId);
        adapter.save(transaction);

        // When
        Optional<Transaction> found = adapter.findEarliestByAccountId("NONEXISTENT");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldDeleteById() {
        // Given
        Transaction transaction = createTransaction();
        Transaction saved = adapter.save(transaction);

        Optional<Transaction> found = adapter.findById(saved.id());
        assertThat(found).isPresent();

        adapter.deleteById(saved.id());
        found = adapter.findById(saved.id());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCheckExistenceById() {
        // Given
        Transaction transaction = createTransaction();
        Transaction saved = adapter.save(transaction);

        // When
        boolean exists = adapter.existsById(saved.id());
        boolean notExists = adapter.existsById(TransactionId.generate());

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void shouldUpdateExistingTransaction() {
        // Given
        Transaction transaction = createTransaction();
        Transaction saved = adapter.save(transaction);

        // When
        Transaction updated = Transaction.builder()
                .id(saved.id())
                .accountId(saved.accountId())
                .amount(new Money(new BigDecimal("999.99"), Currency.getInstance("USD")))
                .type(saved.type())
                .channel(saved.channel())
                .merchant(saved.merchant())
                .location(saved.location())
                .deviceId(saved.deviceId())
                .timestamp(saved.timestamp())
                .build();

        Transaction savedUpdated = adapter.save(updated);
        Optional<Transaction> found = adapter.findById(savedUpdated.id());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().amount().value()).isEqualByComparingTo(new BigDecimal("999.99"));
    }

    private Transaction createTransaction() {
        return createTransactionWithAccountId("ACC123");
    }

    private static Instant oneHourEarlier(Instant now) {
        return now.minus(1, ChronoUnit.HOURS);
    }

    private static Instant oneHourLater(Instant baseTime) {
        return baseTime.plus(1, ChronoUnit.HOURS);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static Instant twoHoursLater(Instant baseTime) {
        return baseTime.plus(2, ChronoUnit.HOURS);
    }

    private static Instant twoHoursEarlier(Instant baseTime) {
        return baseTime.minus(2, ChronoUnit.HOURS);
    }

    private Transaction createTransactionWithAccountId(String accountId) {
        return createTransactionWithAccountIdAndTime(accountId, now());
    }

    private Transaction createTransactionWithAccountIdAndTime(String accountId, Instant timestamp) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(new BigDecimal("100.50"), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MER123"), "Test Merchant", MerchantCategory.RETAIL))
                .location(new Location(12.34, 56.78, "Test Location", "City", Instant.now()))
                .deviceId("DEV123")
                .timestamp(timestamp)
                .build();
    }
}