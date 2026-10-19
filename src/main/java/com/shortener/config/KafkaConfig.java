package com.shortener.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.shortener.dto.ClickEvent;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    public static final String CLICK_TOPIC = "url-clicks";

    // 1. Auto-create Topic
    @Bean
    public NewTopic clickTopic() {
        return TopicBuilder.name(CLICK_TOPIC)
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
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Manual commit configuration for at-least-once guarantee
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Tuning consumption speed
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000); // Poll up to 1000 records at a time
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1048576); // 1MB min fetch
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // Wait 500ms max if bytes not met
        
        JsonDeserializer<ClickEvent> clickEventDeserializer = new JsonDeserializer<>(ClickEvent.class);
        clickEventDeserializer.addTrustedPackages("com.shortener.dto");
        
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), clickEventDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClickEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ClickEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // CRITICAL: Enable batch listening
        factory.setBatchListener(true);
        
        // Enable manual immediate offset committing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }
}
