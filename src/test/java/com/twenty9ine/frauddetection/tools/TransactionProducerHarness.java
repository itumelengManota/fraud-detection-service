package com.twenty9ine.frauddetection.tools;

import com.twenty9ine.frauddetection.infrastructure.adapter.kafka.LocationAvro;
import com.twenty9ine.frauddetection.infrastructure.adapter.kafka.MerchantAvro;
import com.twenty9ine.frauddetection.infrastructure.adapter.kafka.TransactionAvro;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Standalone harness for producing properly Apicurio-encoded Avro messages to
 * {@code transactions.normalized}. Exists to work around BUG-T4-001 — the
 * {@code spring-boot} MCP {@code kafka_produce} tool writes raw JSON bytes, not
 * Apicurio-framed Avro, and therefore cannot drive the UC-01 happy path.
 *
 * <p>Invocation (via Gradle):
 * <pre>
 *   ./gradlew sendTransaction --args="--txnId=... --amount=25.50 --scenario=happy"
 *   ./gradlew sendTransaction --args="--scenario=malformed --topic=transactions.normalized"
 * </pre>
 */
public final class TransactionProducerHarness {

    private TransactionProducerHarness() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        String topic = opts.getOrDefault("topic", "transactions.normalized");
        String scenario = opts.getOrDefault("scenario", "happy");
        String bootstrap = opts.getOrDefault("bootstrap", "localhost:9092");
        String registry = opts.getOrDefault("registry", "http://localhost:8081/apis/registry/v2");

        if ("malformed".equals(scenario)) {
            produceMalformed(topic, opts.getOrDefault("txnId", UUID.randomUUID().toString()), bootstrap);
            return;
        }

        produceAvro(topic, opts, bootstrap, registry);
    }

    private static void produceAvro(String topic, Map<String, String> opts, String bootstrap, String registry) throws Exception {
        String txnId = opts.getOrDefault("txnId", UUID.randomUUID().toString());
        String accountId = opts.getOrDefault("accountId", UUID.randomUUID().toString());
        BigDecimal amount = new BigDecimal(opts.getOrDefault("amount", "25.50"));
        String currency = opts.getOrDefault("currency", "USD");
        String type = opts.getOrDefault("type", "PURCHASE");
        String channel = opts.getOrDefault("channel", "ONLINE");

        TransactionAvro avro = TransactionAvro.newBuilder()
                .setTransactionId(txnId)
                .setAccountId(accountId)
                .setAmount(amount.setScale(4, java.math.RoundingMode.HALF_UP))
                .setCurrency(currency)
                .setType(type)
                .setChannel(channel)
                .setMerchant(MerchantAvro.newBuilder()
                        .setId(opts.getOrDefault("merchantId", "m-001"))
                        .setName(opts.getOrDefault("merchantName", "Acme"))
                        .setCategory(opts.getOrDefault("merchantCategory", "RETAIL"))
                        .build())
                .setLocation(LocationAvro.newBuilder()
                        .setLatitude(Double.parseDouble(opts.getOrDefault("lat", "40.7128")))
                        .setLongitude(Double.parseDouble(opts.getOrDefault("lon", "-74.0060")))
                        .setCountry(opts.getOrDefault("country", "US"))
                        .setCity(opts.getOrDefault("city", "New York"))
                        .build())
                .setDeviceId(opts.getOrDefault("deviceId", "dev-001"))
                .setTimestamp(java.time.Instant.ofEpochMilli(Long.parseLong(opts.getOrDefault("timestamp", String.valueOf(System.currentTimeMillis())))))
                .build();

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        props.put(SerdeConfig.REGISTRY_URL, registry);
        props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
        props.put(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER, "true");
        props.put(SerdeConfig.ENABLE_HEADERS, "false");

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props)) {
            RecordMetadata md = producer.send(new ProducerRecord<>(topic, txnId, avro)).get();
            System.out.printf("SENT topic=%s partition=%d offset=%d txnId=%s accountId=%s amount=%s%n",
                    md.topic(), md.partition(), md.offset(), txnId, accountId, amount);
        }
    }

    private static void produceMalformed(String topic, String key, String bootstrap) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            byte[] payload = "{\"this\":\"is not avro\"}".getBytes();
            RecordMetadata md = producer.send(new ProducerRecord<>(topic, key, payload)).get();
            System.out.printf("POISON topic=%s partition=%d offset=%d key=%s%n",
                    md.topic(), md.partition(), md.offset(), key);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (String a : args) {
            if (!a.startsWith("--")) continue;
            String body = a.substring(2);
            int eq = body.indexOf('=');
            if (eq < 0) out.put(body, "true");
            else out.put(body.substring(0, eq), body.substring(eq + 1));
        }
        return out;
    }
}
