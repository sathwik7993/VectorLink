package com.shortener.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import com.shortener.dto.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    public static final String CLICK_TOPIC = "url-clicks";
    public static final String CLICK_DLT = CLICK_TOPIC + ".DLT";
    public static final String DLT_REPLAY_LISTENER_ID = "click-dlt-replay";

    // 1. Auto-create Topics
    @Bean
    public NewTopic clickTopic() {
        return TopicBuilder.name(CLICK_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Dead-letter topic: same partition count, because the recoverer keeps the original partition number
    @Bean
    public NewTopic clickDeadLetterTopic() {
        return TopicBuilder.name(CLICK_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // 2. Producer Configuration
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Optimize for throughput (buffering and batching)
        config.put(ProducerConfig.LINGER_MS_CONFIG, 20); // wait up to 20ms before sending batch
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768); // 32KB batch size
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        config.put(ProducerConfig.ACKS_CONFIG, "1"); // At least one ack
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000); // Timeout if Kafka broker is unavailable
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // 3. Batch Consumer Configuration
    @Bean
    public ConsumerFactory<String, ClickEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Manual commit configuration for at-least-once guarantee
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Tuning consumption speed
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000); // Poll up to 1000 records at a time
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1048576); // 1MB min fetch
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // Wait 500ms max if bytes not met

        JsonDeserializer<ClickEvent> clickEventDeserializer = new JsonDeserializer<>(ClickEvent.class);
        clickEventDeserializer.addTrustedPackages("com.shortener.dto");

        // Poison pills (invalid JSON) become a null value plus an exception header instead of
        // crashing the consumer in a loop; the listener then routes them to the DLT.
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(),
                new ErrorHandlingDeserializer<>(clickEventDeserializer));
    }

    // 4. Dead-letter publishing: raw bytes for undeserializable records, JSON for everything else.
    // Not a bean, so it never competes with kafkaTemplate for injection.
    private KafkaTemplate<String, Object> deadLetterKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);

        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(Object.class, new JsonSerializer<>());
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(
                config, new StringSerializer(), new DelegatingByTypeSerializer(delegates, true));
        return new KafkaTemplate<>(factory);
    }

    /**
     * Retries a failed record 3 times (1s apart), then publishes it to url-clicks.DLT with the
     * exception details in headers so it can be inspected and replayed later.
     */
    @Bean
    public DefaultErrorHandler clickErrorHandler(MeterRegistry meterRegistry) {
        Counter deadLettered = Counter.builder("analytics.events.dead_lettered")
                .description("Click events published to the dead-letter topic after retries were exhausted")
                .register(meterRegistry);
        DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate());
        return new DefaultErrorHandler((record, exception) -> {
            deadLettered.increment();
            publisher.accept(record, exception);
        }, new FixedBackOff(1000L, 3L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClickEvent> kafkaListenerContainerFactory(
            DefaultErrorHandler clickErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ClickEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // CRITICAL: Enable batch listening
        factory.setBatchListener(true);

        // Enable manual immediate offset committing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        factory.setCommonErrorHandler(clickErrorHandler);

        return factory;
    }

    // 5. DLT replay consumer: reads raw bytes so it can handle both JSON and poison-pill records
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> dltListenerContainerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new ByteArrayDeserializer()));
        return factory;
    }
}
