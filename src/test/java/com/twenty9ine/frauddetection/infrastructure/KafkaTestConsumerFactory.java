package com.twenty9ine.frauddetection.infrastructure;

    import io.apicurio.registry.serde.SerdeConfig;
    import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
    import org.apache.kafka.clients.consumer.ConsumerConfig;
    import org.apache.kafka.clients.consumer.KafkaConsumer;
    import org.apache.kafka.common.serialization.StringDeserializer;

    import java.time.Duration;
    import java.util.Map;
    import java.util.UUID;
    import java.util.concurrent.ConcurrentHashMap;

    /**
     * Factory for creating and pooling KafkaConsumer instances in tests.
     * Uses ThreadLocal to provide thread-safe consumer isolation for parallel tests.
     */
    public final class KafkaTestConsumerFactory {

        private static final Map<String, ThreadLocal<KafkaConsumer<String, Object>>> CONSUMER_POOL = new ConcurrentHashMap<>();

        private KafkaTestConsumerFactory() {}

        /**
         * Get or create a KafkaConsumer for the current thread.
         * Each thread gets its own consumer instance, enabling safe parallel execution.
         */
        public static KafkaConsumer<String, Object> getConsumer(String bootstrapServers, String registryUrl) {
            String key = createKey(bootstrapServers, registryUrl);

            ThreadLocal<KafkaConsumer<String, Object>> threadLocal = CONSUMER_POOL.computeIfAbsent(key,
                    k -> ThreadLocal.withInitial(() -> createConsumer(bootstrapServers, registryUrl)));

            return threadLocal.get();
        }

        /**
         * Reset consumer state for the current thread.
         */
        public static void resetConsumer(KafkaConsumer<String, Object> consumer) {
            try {
                consumer.unsubscribe();
                consumer.poll(Duration.ofMillis(100)); // Drain any pending messages
            } catch (Exception e) {
                // Log but don't fail - consumer might be in invalid state
                System.err.println("Warning: Failed to reset consumer: " + e.getMessage());
            }
        }

        /**
         * Close and remove consumer for current thread.
         * Call this in @AfterAll if you want to free resources.
         */
        public static void closeCurrentThreadConsumer() {
            CONSUMER_POOL.values().forEach(threadLocal -> {
                KafkaConsumer<String, Object> consumer = threadLocal.get();
                if (consumer != null) {
                    try {
                        consumer.close(Duration.ofSeconds(2));
                    } catch (Exception e) {
                        // Best effort close
                    }
                    threadLocal.remove();
                }
            });
        }

        /**
         * Close all consumers across all threads.
         * Call this when test suite completes.
         */
        public static void closeAll() {
            CONSUMER_POOL.values().forEach(threadLocal -> {
                try {
                    KafkaConsumer<String, Object> consumer = threadLocal.get();
                    if (consumer != null) {
                        consumer.close(Duration.ofSeconds(2));
                        threadLocal.remove();
                    }
                } catch (Exception e) {
                    // Best effort close
                }
            });
            CONSUMER_POOL.clear();
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
                    SerdeConfig.AUTO_REGISTER_ARTIFACT, true
//                    SerdeConfig.USE_SPECIFIC_AVRO_READER, true
            );

            return new KafkaConsumer<>(config);
        }

        private static String createKey(String bootstrapServers, String registryUrl) {
            return bootstrapServers + ":" + registryUrl;
        }
    }