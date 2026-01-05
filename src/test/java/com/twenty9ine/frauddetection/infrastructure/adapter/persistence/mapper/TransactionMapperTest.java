package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJdbcTest
@Testcontainers
@DisabledInAotMode
@ComponentScan(basePackages = "com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper")
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("postgres")
class TransactionMapperTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgres.start();

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionMapper mapper;
    private Instant timestamp;

    @BeforeEach
    void setUp() {
        timestamp = Instant.now();
    }

    @Test
    void testToEntity_CompleteTransaction_MapsAllFields() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC123")
                .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH001"), "Test Merchant", MerchantCategory.RETAIL))
                .deviceId("DEV456")
                .location(Location.of(40.7128, -74.0060, "USA", "New York"))
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNotNull(entity);
        assertEquals(transaction.id().toUUID(), entity.id());
        assertEquals(transaction.accountId(), entity.accountId());
        assertEquals(transaction.amount().value(), entity.amountValue());
        assertEquals(transaction.amount().currency().getCurrencyCode(), entity.amountCurrency());
        assertEquals(transaction.type().name(), entity.type());
        assertEquals(transaction.channel().name(), entity.channel());
        assertEquals(transaction.timestamp(), entity.timestamp());
    }

    @Test
    void testToEntity_MerchantDetails_MapsCorrectly() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC456")
                .amount(new Money(BigDecimal.valueOf(250.50), Currency.getInstance("EUR")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.POS)
                .merchant(new Merchant(MerchantId.of("MERCH002"), "Coffee Shop", MerchantCategory.RESTAURANT))
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNotNull(entity.merchant());
        assertEquals(transaction.merchant().id().merchantId(), entity.merchant().id());
        assertEquals(transaction.merchant().name(), entity.merchant().name());
        assertEquals(transaction.merchant().category(), MerchantCategory.fromString(entity.merchant().category()));
    }

    @Test
    void testToEntity_NoMerchant_ReturnsNullMerchant() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC789")
                .amount(new Money(BigDecimal.valueOf(75.00), Currency.getInstance("GBP")))
                .type(TransactionType.ATM_WITHDRAWAL)
                .channel(Channel.ATM)
                .merchant(null)
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNull(entity.merchant());
    }

    @Test
    void testToEntity_DeviceId_MapsCorrectly() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC321")
                .amount(new Money(BigDecimal.valueOf(150.00), Currency.getInstance("CAD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.MOBILE)
                .deviceId("MOB789")
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNotNull(entity.deviceId());
        assertEquals(transaction.deviceId(), entity.deviceId());
    }

    @Test
    void testToEntity_NoDevice_ReturnsNullDevice() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC654")
                .amount(new Money(BigDecimal.valueOf(50.00), Currency.getInstance("USD")))
                .type(TransactionType.REFUND)
                .channel(Channel.ONLINE)
                .deviceId(null)
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNull(entity.deviceId());
    }

    @Test
    void testToEntity_Location_MapsAllLocationFields() {
        Location location = Location.of(51.5074, -0.1278, "UK", "London");

        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC111")
                .amount(new Money(BigDecimal.valueOf(200.00), Currency.getInstance("GBP")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.POS)
                .location(location)
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNotNull(entity.location());
        assertEquals(location.latitude(), entity.location().latitude());
        assertEquals(location.longitude(), entity.location().longitude());
        assertEquals(location.country(), entity.location().country());
        assertEquals(location.city(), entity.location().city());
    }

    @Test
    void testToEntity_NoLocation_ReturnsNullLocation() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC222")
                .amount(new Money(BigDecimal.valueOf(60.00), Currency.getInstance("AUD")))
                .type(TransactionType.TRANSFER)
                .channel(Channel.ONLINE)
                .location(null)
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNull(entity.location());
    }

    @Test
    void testToEntity_AllTransactionTypes_MapCorrectly() {
        for (TransactionType type : TransactionType.values()) {
            Transaction transaction = Transaction.builder()
                    .id(TransactionId.of(UUID.randomUUID()))
                    .accountId("ACC999")
                    .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                    .type(type)
                    .channel(Channel.ONLINE)
                    .timestamp(timestamp)
                    .build();

            TransactionEntity entity = mapper.toEntity(transaction);

            assertEquals(type.name(), entity.type());
        }
    }

    @Test
    void testToEntity_AllChannels_MapCorrectly() {
        for (Channel channel : Channel.values()) {
            Transaction transaction = Transaction.builder()
                    .id(TransactionId.of(UUID.randomUUID()))
                    .accountId("ACC888")
                    .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                    .type(TransactionType.PURCHASE)
                    .channel(channel)
                    .timestamp(timestamp)
                    .build();

            TransactionEntity entity = mapper.toEntity(transaction);

            assertEquals(channel.name(), entity.channel());
        }
    }

    @Test
    void testToDomain_CompleteEntity_MapsAllFields() {
        UUID id = UUID.randomUUID();

        TransactionEntity entity = TransactionEntity.builder()
                .id(id)
                .accountId("ACC456")
                .amountValue(BigDecimal.valueOf(125.75))
                .amountCurrency("EUR")
                .type("PURCHASE")
                .channel("POS")
                .merchant(new MerchantEntity("MERCH003", "Bookstore", MerchantCategory.RETAIL.name()))
                .deviceId("DEV789")
                .location(buildLocationEntity())
                .timestamp(timestamp)
                .build();

        Transaction transaction = mapper.toDomain(entity);

        assertNotNull(transaction);
        assertEquals(id, transaction.id().toUUID());
        assertEquals(entity.accountId(), transaction.accountId());
        assertEquals(entity.amountValue(), transaction.amount().value());
        assertEquals(Currency.getInstance(entity.amountCurrency()), transaction.amount().currency());
        assertEquals(TransactionType.PURCHASE, transaction.type());
        assertEquals(Channel.POS, transaction.channel());
        assertEquals(entity.timestamp(), transaction.timestamp());
    }

    private LocationEntity buildLocationEntity() {
        return LocationEntity.builder()
                .latitude(48.8566)
                .longitude(2.3522)
                .country("France")
                .city("Paris")
                .build();
    }

    @Test
    void testToDomain_MerchantDetails_MapsCorrectly() {
        MerchantEntity merchant = new MerchantEntity("MERCH004", "Electronics Store", "ELECTRONICS");

        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .accountId("ACC789")
                .amountValue(BigDecimal.valueOf(300.00))
                .amountCurrency("USD")
                .type("PURCHASE")
                .channel("ONLINE")
                .timestamp(timestamp)
                .merchant(merchant)
                .build();

        Transaction transaction = mapper.toDomain(entity);

        assertEquals(merchant.id(), transaction.merchant().id().merchantId());
        assertEquals(merchant.name(), transaction.merchant().name());
        assertEquals(merchant.category(), transaction.merchant().category().name());
    }

    @Test
    void testToDomain_NoMerchant_ReturnsNullMerchantFields() {
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .accountId("ACC999")
                .amountValue(BigDecimal.valueOf(50.00))
                .amountCurrency("USD")
                .type("ATM_WITHDRAWAL")
                .channel("ATM")
                .timestamp(timestamp)
                .merchant(null)
                .build();

        Transaction transaction = mapper.toDomain(entity);

        assertNull(transaction.merchant());
    }

    @Test
    void testToDomain_DeviceId_MapsCorrectly() {
        String deviceId = "MOBILE123";
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .accountId("ACC111")
                .amountValue(BigDecimal.valueOf(85.50))
                .amountCurrency("GBP")
                .type("PURCHASE")
                .channel("MOBILE")
                .timestamp(timestamp)
                .deviceId(deviceId)
                .build();


        Transaction transaction = mapper.toDomain(entity);

        assertEquals(deviceId, transaction.deviceId());
    }

    @Test
    void testToDomain_NoDevice_ReturnsNullDeviceId() {
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .accountId("ACC222")
                .amountValue(BigDecimal.valueOf(45.00))
                .amountCurrency("CAD")
                .type("REFUND")
                .channel("ONLINE")
                .timestamp(timestamp)
                .deviceId(null)
                .build();

        Transaction transaction = mapper.toDomain(entity);

        assertNull(transaction.deviceId());
    }

    @Test
    void testToDomain_Location_MapsAllLocationFields() {
        LocationEntity location = LocationEntity.builder()
                .latitude(-33.8688)
                .longitude(151.2093)
                .country("Australia")
                .city("Sydney")
                .build();

        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .accountId("ACC333")
                .amountValue(BigDecimal.valueOf(175.00))
                .amountCurrency("AUD")
                .type("PURCHASE")
                .channel("POS")
                .timestamp(timestamp)
                .location(location)
                .build();

        Transaction transaction = mapper.toDomain(entity);

        assertNotNull(transaction.location());
        assertEquals(location.latitude(), transaction.location().latitude());
        assertEquals(location.longitude(), transaction.location().longitude());
        assertEquals(location.country(), transaction.location().country());
        assertEquals(location.city(), transaction.location().city());
    }

    @Test
    void testToDomain_NoLocation_ReturnsNullLocation() {
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .accountId("ACC444")
                .amountValue(BigDecimal.valueOf(60.00))
                .amountCurrency("JPY")
                .type("TRANSFER")
                .channel("ONLINE")
                .timestamp(timestamp)
                .location(null)
                .build();

        Transaction transaction = mapper.toDomain(entity);

        assertNull(transaction.location());
    }

    @Test
    void testToDomain_AllTransactionTypes_MapCorrectly() {
        for (TransactionType type : TransactionType.values()) {
            TransactionEntity entity = TransactionEntity.builder()
                    .id(UUID.randomUUID())
                    .accountId("ACC777")
                    .amountValue(BigDecimal.valueOf(100.00))
                    .amountCurrency("USD")
                    .type(type.name())
                    .channel("ONLINE")
                    .timestamp(timestamp)
                    .build();

            Transaction transaction = mapper.toDomain(entity);

            assertEquals(type, transaction.type());
        }
    }

    @Test
    void testToDomain_AllChannels_MapCorrectly() {
        for (Channel channel : Channel.values()) {
            TransactionEntity entity = TransactionEntity.builder()
                    .id(UUID.randomUUID())
                    .accountId("ACC666")
                    .amountValue(BigDecimal.valueOf(100.00))
                    .amountCurrency("USD")
                    .type("PURCHASE")
                    .channel(channel.name())
                    .timestamp(timestamp)
                    .build();

            Transaction transaction = mapper.toDomain(entity);

            assertEquals(channel, transaction.channel());
        }
    }

    @Test
    void testRoundTrip_DomainToEntityToDomain_PreservesData() {
        Transaction originalTransaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC555")
                .amount(new Money(BigDecimal.valueOf(250.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH005"), "Fashion Store", MerchantCategory.RETAIL))
                .deviceId("WEB123")
                .location(Location.of(40.7128, -74.0060, "USA", "New York"))
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(originalTransaction);
        Transaction roundTripTransaction = mapper.toDomain(entity);

        assertEquals(originalTransaction.id(), roundTripTransaction.id());
        assertEquals(originalTransaction.accountId(), roundTripTransaction.accountId());
        assertEquals(originalTransaction.amount().value(), roundTripTransaction.amount().value());
        assertEquals(originalTransaction.amount().currency(), roundTripTransaction.amount().currency());
        assertEquals(originalTransaction.type(), roundTripTransaction.type());
        assertEquals(originalTransaction.channel(), roundTripTransaction.channel());
        assertEquals(originalTransaction.merchant().id().merchantId(), roundTripTransaction.merchant().id().merchantId());
        assertEquals(originalTransaction.merchant().name(), roundTripTransaction.merchant().name());
        assertEquals(originalTransaction.merchant().category(), roundTripTransaction.merchant().category());
        assertEquals(originalTransaction.deviceId(), roundTripTransaction.deviceId());
        assertEquals(originalTransaction.timestamp(), roundTripTransaction.timestamp());
    }

    @Test
    void testRoundTrip_EntityToDomainToEntity_PreservesData() {
        UUID id = UUID.randomUUID();

        MerchantEntity merchant = new MerchantEntity("MERCH006", "Grocery Store", MerchantCategory.GROCERY.name());

        TransactionEntity originalEntity = TransactionEntity.builder()
                .id(id)
                .accountId("ACC666")
                .amountValue(BigDecimal.valueOf(350.00))
                .amountCurrency("EUR")
                .type("PURCHASE")
                .channel("MOBILE")
                .timestamp(timestamp)
                .merchant(merchant)
                .build();

        Transaction transaction = mapper.toDomain(originalEntity);
        TransactionEntity roundTripEntity = mapper.toEntity(transaction);

        assertEquals(originalEntity.id(), roundTripEntity.id());
        assertEquals(originalEntity.accountId(), roundTripEntity.accountId());
        assertEquals(originalEntity.amountValue(), roundTripEntity.amountValue());
        assertEquals(originalEntity.amountCurrency(), roundTripEntity.amountCurrency());
        assertEquals(originalEntity.type(), roundTripEntity.type());
        assertEquals(originalEntity.channel(), roundTripEntity.channel());
        assertEquals(originalEntity.timestamp(), roundTripEntity.timestamp());
    }

    @Test
    void testTransactionIdMapping_BothDirections_WorksCorrectly() {
        UUID uuid = UUID.randomUUID();
        TransactionId transactionId = TransactionId.of(uuid);

        UUID mappedUuid = mapper.transactionIdToUUID(transactionId);
        TransactionId mappedId = mapper.uuidToTransactionId(uuid);

        assertEquals(uuid, mappedUuid);
        assertEquals(transactionId, mappedId);
    }

    @Test
    void testCurrencyMapping_ToCode_ReturnsCorrectCode() {
        Currency usd = Currency.getInstance("USD");
        Currency eur = Currency.getInstance("EUR");
        Currency gbp = Currency.getInstance("GBP");
        Currency jpy = Currency.getInstance("JPY");

        assertEquals("USD", mapper.currencyToCode(usd));
        assertEquals("EUR", mapper.currencyToCode(eur));
        assertEquals("GBP", mapper.currencyToCode(gbp));
        assertEquals("JPY", mapper.currencyToCode(jpy));
    }

    @Test
    void testEnumMapping_TransactionType_WorksCorrectly() {
        assertEquals("PURCHASE", mapper.enumToString(TransactionType.PURCHASE));
        assertEquals("ATM_WITHDRAWAL", mapper.enumToString(TransactionType.ATM_WITHDRAWAL));
        assertEquals("TRANSFER", mapper.enumToString(TransactionType.TRANSFER));
        assertEquals("REFUND", mapper.enumToString(TransactionType.REFUND));

        assertEquals(TransactionType.PURCHASE, mapper.stringToTransactionType("PURCHASE"));
        assertEquals(TransactionType.ATM_WITHDRAWAL, mapper.stringToTransactionType("ATM_WITHDRAWAL"));
        assertEquals(TransactionType.TRANSFER, mapper.stringToTransactionType("TRANSFER"));
        assertEquals(TransactionType.REFUND, mapper.stringToTransactionType("REFUND"));
    }

    @Test
    void testEnumMapping_Channel_WorksCorrectly() {
        assertEquals("ONLINE", mapper.enumToString(Channel.ONLINE));
        assertEquals("POS", mapper.enumToString(Channel.POS));
        assertEquals("ATM", mapper.enumToString(Channel.ATM));
        assertEquals("MOBILE", mapper.enumToString(Channel.MOBILE));

        assertEquals(Channel.ONLINE, mapper.stringToChannel("ONLINE"));
        assertEquals(Channel.POS, mapper.stringToChannel("POS"));
        assertEquals(Channel.ATM, mapper.stringToChannel("ATM"));
        assertEquals(Channel.MOBILE, mapper.stringToChannel("MOBILE"));
    }

    @Test
    void testToMerchantEntity_WithAllFields_CreatesEntity() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC123")
                .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH007"), "Test Merchant", MerchantCategory.RETAIL))
                .timestamp(timestamp)
                .build();

        MerchantEntity merchantEntity = mapper.toEntity(transaction).merchant();

        assertNotNull(merchantEntity);
        assertEquals("MERCH007", merchantEntity.id());
        assertEquals("Test Merchant", merchantEntity.name());
        assertEquals("RETAIL", merchantEntity.category());
    }

    @Test
    void testToMerchantEntity_WithNullMerchantId_ReturnsNull() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC123")
                .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(null)
                .timestamp(timestamp)
                .build();

        MerchantEntity merchantEntity = mapper.toEntity(transaction).merchant();

        assertNull(merchantEntity);
    }

    @Test
    void testToEntity_MinimalTransaction_MapsRequiredFields() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC_MIN")
                .amount(new Money(BigDecimal.valueOf(10.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNotNull(entity);
        assertEquals(transaction.id().toUUID(), entity.id());
        assertEquals(transaction.accountId(), entity.accountId());
        assertEquals(transaction.amount().value(), entity.amountValue());
        assertNull(entity.merchant());
        assertNull(entity.deviceId());
        assertNull(entity.location());
    }

    @Test
    void testToDomain_MinimalEntity_MapsRequiredFields() {
        TransactionEntity entity = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .accountId("ACC_MIN")
                .amountValue(BigDecimal.valueOf(10.00))
                .amountCurrency("USD")
                .type("PURCHASE")
                .channel("ONLINE")
                .timestamp(timestamp)
                .build();

        Transaction transaction = mapper.toDomain(entity);

        assertNotNull(transaction);
        assertEquals(entity.id(), transaction.id().toUUID());
        assertEquals(entity.accountId(), transaction.accountId());
        assertNull(transaction.merchant());
        assertNull(transaction.deviceId());
        assertNull(transaction.location());
    }
}