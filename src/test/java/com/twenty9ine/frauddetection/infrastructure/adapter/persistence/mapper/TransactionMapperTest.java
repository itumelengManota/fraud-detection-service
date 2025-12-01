package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;
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
class TransactionMapperTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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
                .merchantId("MERCH001")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .deviceId("DEV456")
                .location(Location.of(40.7128, -74.0060, "USA", "New York", timestamp))
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNotNull(entity);
        assertEquals(transaction.id().toUUID(), entity.getId());
        assertEquals(transaction.accountId(), entity.getAccountId());
        assertEquals(transaction.amount().value(), entity.getAmountValue());
        assertEquals(transaction.amount().currency().getCurrencyCode(), entity.getAmountCurrency());
        assertEquals(transaction.type().name(), entity.getType());
        assertEquals(transaction.channel().name(), entity.getChannel());
        assertEquals(transaction.timestamp(), entity.getTimestamp());
    }

    @Test
    void testToEntity_MerchantDetails_MapsCorrectly() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC456")
                .amount(new Money(BigDecimal.valueOf(250.50), Currency.getInstance("EUR")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.POS)
                .merchantId("MERCH002")
                .merchantName("Coffee Shop")
                .merchantCategory("FOOD")
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNotNull(entity.getMerchant());
        assertEquals(transaction.merchantId(), entity.getMerchant().getMerchantId());
        assertEquals(transaction.merchantName(), entity.getMerchant().getName());
        assertEquals(transaction.merchantCategory(), entity.getMerchant().getCategory());
    }

    @Test
    void testToEntity_NoMerchant_ReturnsNullMerchant() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC789")
                .amount(new Money(BigDecimal.valueOf(75.00), Currency.getInstance("GBP")))
                .type(TransactionType.ATM_WITHDRAWAL)
                .channel(Channel.ATM)
                .merchantId(null)
                .merchantName(null)
                .merchantCategory(null)
                .timestamp(timestamp)
                .build();

        TransactionEntity entity = mapper.toEntity(transaction);

        assertNull(entity.getMerchant());
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

        assertNotNull(entity.getDevice());
        assertEquals(transaction.deviceId(), entity.getDevice().getDeviceId());
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

        assertNull(entity.getDevice());
    }

    @Test
    void testToEntity_Location_MapsAllLocationFields() {
        Location location = Location.of(51.5074, -0.1278, "UK", "London", timestamp);

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

        assertNotNull(entity.getLocation());
        assertEquals(location.latitude(), entity.getLocation().getLatitude());
        assertEquals(location.longitude(), entity.getLocation().getLongitude());
        assertEquals(location.country(), entity.getLocation().getCountry());
        assertEquals(location.city(), entity.getLocation().getCity());
        assertEquals(location.timestamp(), entity.getLocation().getTimestamp());
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

        assertNull(entity.getLocation());
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

            assertEquals(type.name(), entity.getType());
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

            assertEquals(channel.name(), entity.getChannel());
        }
    }

    @Test
    void testToDomain_CompleteEntity_MapsAllFields() {
        UUID id = UUID.randomUUID();

        TransactionEntity entity = new TransactionEntity();
        entity.setId(id);
        entity.setAccountId("ACC456");
        entity.setAmountValue(BigDecimal.valueOf(125.75));
        entity.setAmountCurrency("EUR");
        entity.setType("PURCHASE");
        entity.setChannel("POS");
        entity.setTimestamp(timestamp);

        MerchantEntity merchant = new MerchantEntity();
        merchant.setMerchantId("MERCH003");
        merchant.setName("Bookstore");
        merchant.setCategory("BOOKS");
        entity.setMerchant(merchant);

        DeviceEntity device = new DeviceEntity();
        device.setDeviceId("DEV789");
        entity.setDevice(device);

        LocationEntity location = new LocationEntity();
        location.setLatitude(48.8566);
        location.setLongitude(2.3522);
        location.setCountry("France");
        location.setCity("Paris");
        location.setTimestamp(timestamp);
        entity.setLocation(location);

        Transaction transaction = mapper.toDomain(entity);

        assertNotNull(transaction);
        assertEquals(id, transaction.id().toUUID());
        assertEquals(entity.getAccountId(), transaction.accountId());
        assertEquals(entity.getAmountValue(), transaction.amount().value());
        assertEquals(Currency.getInstance(entity.getAmountCurrency()), transaction.amount().currency());
        assertEquals(TransactionType.PURCHASE, transaction.type());
        assertEquals(Channel.POS, transaction.channel());
        assertEquals(entity.getTimestamp(), transaction.timestamp());
    }

    @Test
    void testToDomain_MerchantDetails_MapsCorrectly() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId("ACC789");
        entity.setAmountValue(BigDecimal.valueOf(300.00));
        entity.setAmountCurrency("USD");
        entity.setType("PURCHASE");
        entity.setChannel("ONLINE");
        entity.setTimestamp(timestamp);

        MerchantEntity merchant = new MerchantEntity();
        merchant.setMerchantId("MERCH004");
        merchant.setName("Electronics Store");
        merchant.setCategory("ELECTRONICS");
        entity.setMerchant(merchant);

        Transaction transaction = mapper.toDomain(entity);

        assertEquals(merchant.getMerchantId(), transaction.merchantId());
        assertEquals(merchant.getName(), transaction.merchantName());
        assertEquals(merchant.getCategory(), transaction.merchantCategory());
    }

    @Test
    void testToDomain_NoMerchant_ReturnsNullMerchantFields() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId("ACC999");
        entity.setAmountValue(BigDecimal.valueOf(50.00));
        entity.setAmountCurrency("USD");
        entity.setType("ATM_WITHDRAWAL");
        entity.setChannel("ATM");
        entity.setTimestamp(timestamp);
        entity.setMerchant(null);

        Transaction transaction = mapper.toDomain(entity);

        assertNull(transaction.merchantId());
        assertNull(transaction.merchantName());
        assertNull(transaction.merchantCategory());
    }

    @Test
    void testToDomain_DeviceId_MapsCorrectly() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId("ACC111");
        entity.setAmountValue(BigDecimal.valueOf(85.50));
        entity.setAmountCurrency("GBP");
        entity.setType("PURCHASE");
        entity.setChannel("MOBILE");
        entity.setTimestamp(timestamp);

        DeviceEntity device = new DeviceEntity();
        device.setDeviceId("MOBILE123");
        entity.setDevice(device);

        Transaction transaction = mapper.toDomain(entity);

        assertEquals(device.getDeviceId(), transaction.deviceId());
    }

    @Test
    void testToDomain_NoDevice_ReturnsNullDeviceId() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId("ACC222");
        entity.setAmountValue(BigDecimal.valueOf(45.00));
        entity.setAmountCurrency("CAD");
        entity.setType("REFUND");
        entity.setChannel("ONLINE");
        entity.setTimestamp(timestamp);
        entity.setDevice(null);

        Transaction transaction = mapper.toDomain(entity);

        assertNull(transaction.deviceId());
    }

    @Test
    void testToDomain_Location_MapsAllLocationFields() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId("ACC333");
        entity.setAmountValue(BigDecimal.valueOf(175.00));
        entity.setAmountCurrency("AUD");
        entity.setType("PURCHASE");
        entity.setChannel("POS");
        entity.setTimestamp(timestamp);

        LocationEntity location = new LocationEntity();
        location.setLatitude(-33.8688);
        location.setLongitude(151.2093);
        location.setCountry("Australia");
        location.setCity("Sydney");
        location.setTimestamp(timestamp);
        entity.setLocation(location);

        Transaction transaction = mapper.toDomain(entity);

        assertNotNull(transaction.location());
        assertEquals(location.getLatitude(), transaction.location().latitude());
        assertEquals(location.getLongitude(), transaction.location().longitude());
        assertEquals(location.getCountry(), transaction.location().country());
        assertEquals(location.getCity(), transaction.location().city());
        assertEquals(location.getTimestamp(), transaction.location().timestamp());
    }

    @Test
    void testToDomain_NoLocation_ReturnsNullLocation() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId("ACC444");
        entity.setAmountValue(BigDecimal.valueOf(60.00));
        entity.setAmountCurrency("JPY");
        entity.setType("TRANSFER");
        entity.setChannel("ONLINE");
        entity.setTimestamp(timestamp);
        entity.setLocation(null);

        Transaction transaction = mapper.toDomain(entity);

        assertNull(transaction.location());
    }

    @Test
    void testToDomain_AllTransactionTypes_MapCorrectly() {
        for (TransactionType type : TransactionType.values()) {
            TransactionEntity entity = new TransactionEntity();
            entity.setId(UUID.randomUUID());
            entity.setAccountId("ACC777");
            entity.setAmountValue(BigDecimal.valueOf(100.00));
            entity.setAmountCurrency("USD");
            entity.setType(type.name());
            entity.setChannel("ONLINE");
            entity.setTimestamp(timestamp);

            Transaction transaction = mapper.toDomain(entity);

            assertEquals(type, transaction.type());
        }
    }

    @Test
    void testToDomain_AllChannels_MapCorrectly() {
        for (Channel channel : Channel.values()) {
            TransactionEntity entity = new TransactionEntity();
            entity.setId(UUID.randomUUID());
            entity.setAccountId("ACC666");
            entity.setAmountValue(BigDecimal.valueOf(100.00));
            entity.setAmountCurrency("USD");
            entity.setType("PURCHASE");
            entity.setChannel(channel.name());
            entity.setTimestamp(timestamp);

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
                .merchantId("MERCH005")
                .merchantName("Fashion Store")
                .merchantCategory("CLOTHING")
                .deviceId("WEB123")
                .location(Location.of(40.7128, -74.0060, "USA", "New York", timestamp))
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
        assertEquals(originalTransaction.merchantId(), roundTripTransaction.merchantId());
        assertEquals(originalTransaction.merchantName(), roundTripTransaction.merchantName());
        assertEquals(originalTransaction.merchantCategory(), roundTripTransaction.merchantCategory());
        assertEquals(originalTransaction.deviceId(), roundTripTransaction.deviceId());
        assertEquals(originalTransaction.timestamp(), roundTripTransaction.timestamp());
    }

    @Test
    void testRoundTrip_EntityToDomainToEntity_PreservesData() {
        UUID id = UUID.randomUUID();

        TransactionEntity originalEntity = new TransactionEntity();
        originalEntity.setId(id);
        originalEntity.setAccountId("ACC666");
        originalEntity.setAmountValue(BigDecimal.valueOf(350.00));
        originalEntity.setAmountCurrency("EUR");
        originalEntity.setType("PURCHASE");
        originalEntity.setChannel("MOBILE");
        originalEntity.setTimestamp(timestamp);

        MerchantEntity merchant = new MerchantEntity();
        merchant.setMerchantId("MERCH006");
        merchant.setName("Grocery Store");
        merchant.setCategory("GROCERIES");
        originalEntity.setMerchant(merchant);

        Transaction transaction = mapper.toDomain(originalEntity);
        TransactionEntity roundTripEntity = mapper.toEntity(transaction);

        assertEquals(originalEntity.getId(), roundTripEntity.getId());
        assertEquals(originalEntity.getAccountId(), roundTripEntity.getAccountId());
        assertEquals(originalEntity.getAmountValue(), roundTripEntity.getAmountValue());
        assertEquals(originalEntity.getAmountCurrency(), roundTripEntity.getAmountCurrency());
        assertEquals(originalEntity.getType(), roundTripEntity.getType());
        assertEquals(originalEntity.getChannel(), roundTripEntity.getChannel());
        assertEquals(originalEntity.getTimestamp(), roundTripEntity.getTimestamp());
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
                .merchantId("MERCH007")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .timestamp(timestamp)
                .build();

        MerchantEntity merchantEntity = mapper.toMerchantEntity(transaction);

        assertNotNull(merchantEntity);
        assertEquals("MERCH007", merchantEntity.getMerchantId());
        assertEquals("Test Merchant", merchantEntity.getName());
        assertEquals("RETAIL", merchantEntity.getCategory());
    }

    @Test
    void testToMerchantEntity_WithNullMerchantId_ReturnsNull() {
        Transaction transaction = Transaction.builder()
                .id(TransactionId.of(UUID.randomUUID()))
                .accountId("ACC123")
                .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchantId(null)
                .timestamp(timestamp)
                .build();

        MerchantEntity merchantEntity = mapper.toMerchantEntity(transaction);

        assertNull(merchantEntity);
    }

    @Test
    void testToDeviceEntity_WithDeviceId_CreatesEntity() {
        DeviceEntity deviceEntity = mapper.toDeviceEntity("DEV123");

        assertNotNull(deviceEntity);
        assertEquals("DEV123", deviceEntity.getDeviceId());
    }

    @Test
    void testToDeviceEntity_WithNullDeviceId_ReturnsNull() {
        DeviceEntity deviceEntity = mapper.toDeviceEntity(null);

        assertNull(deviceEntity);
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
        assertEquals(transaction.id().toUUID(), entity.getId());
        assertEquals(transaction.accountId(), entity.getAccountId());
        assertEquals(transaction.amount().value(), entity.getAmountValue());
        assertNull(entity.getMerchant());
        assertNull(entity.getDevice());
        assertNull(entity.getLocation());
    }

    @Test
    void testToDomain_MinimalEntity_MapsRequiredFields() {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(UUID.randomUUID());
        entity.setAccountId("ACC_MIN");
        entity.setAmountValue(BigDecimal.valueOf(10.00));
        entity.setAmountCurrency("USD");
        entity.setType("PURCHASE");
        entity.setChannel("ONLINE");
        entity.setTimestamp(timestamp);

        Transaction transaction = mapper.toDomain(entity);

        assertNotNull(transaction);
        assertEquals(entity.getId(), transaction.id().toUUID());
        assertEquals(entity.getAccountId(), transaction.accountId());
        assertNull(transaction.merchantId());
        assertNull(transaction.deviceId());
        assertNull(transaction.location());
    }
}