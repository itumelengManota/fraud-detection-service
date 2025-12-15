package com.twenty9ine.frauddetection.infrastructure;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public final class KafkaTestConsumerFactory {

    private static KafkaConsumer<String, Object> sharedConsumer;

    private KafkaTestConsumerFactory() { }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(KafkaTestConsumerFactory::closeConsumer));
    }

    public static synchronized KafkaConsumer<String, Object> getConsumer(
            String bootstrapServers,
            String registryUrl) {
        if (sharedConsumer == null) {
            sharedConsumer = createConsumer(bootstrapServers, registryUrl);
        }
        return sharedConsumer;
    }

    public static synchronized void resetConsumer(KafkaConsumer<String, Object> consumer) {
        consumer.unsubscribe();
        consumer.poll(Duration.ZERO); // Clear state
    }

    public static synchronized void closeConsumer() {
        if (sharedConsumer != null) {
            try {
                sharedConsumer.close(Duration.ofSeconds(2));
            } finally {
                sharedConsumer = null;
            }
        }
    }

    private static KafkaConsumer<String, Object> createConsumer(String bootstrapServers, String registryUrl) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class,
                SerdeConfig.REGISTRY_URL, registryUrl,
                "apicurio.registry.use-specific-avro-reader", true,
                "specific.avro.reader", "true",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                SerdeConfig.AUTO_REGISTER_ARTIFACT, true);

        return new KafkaConsumer<>(config);
    }
}