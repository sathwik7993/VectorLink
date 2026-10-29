package com.shortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shortener.config.KafkaConfig;
import com.shortener.dto.ClickEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Replays click events from url-clicks.DLT back onto url-clicks once the underlying problem
 * (e.g. a database outage) is fixed. The listener is stopped by default and is started/stopped
 * on demand through the admin API (AdminController).
 *
 * Records that are not valid ClickEvent JSON (poison pills) cannot succeed on replay, so they are
 * logged and skipped instead of being sent back to the main topic.
 */
@Service
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterReplayService {

    private static final Logger LOGGER = Logger.getLogger(DeadLetterReplayService.class.getName());

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaListenerEndpointRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Counter replayed;
    private final Counter discarded;

    public DeadLetterReplayService(KafkaTemplate<String, Object> kafkaTemplate,
                                   KafkaListenerEndpointRegistry registry,
                                   MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.registry = registry;
        this.replayed = Counter.builder("analytics.dlt.replayed")
                .description("Dead-lettered click events re-published to the main topic")
                .register(meterRegistry);
        this.discarded = Counter.builder("analytics.dlt.discarded")
                .description("Dead-lettered records skipped during replay because they are not valid click events")
                .register(meterRegistry);
    }

    @KafkaListener(
            id = KafkaConfig.DLT_REPLAY_LISTENER_ID,
            topics = KafkaConfig.CLICK_DLT,
            groupId = "url-analytics-dlt-replay",
            containerFactory = "dltListenerContainerFactory",
            autoStartup = "false"
    )
    public void replay(ConsumerRecord<String, byte[]> record) throws Exception {
        ClickEvent event;
        try {
            event = objectMapper.readValue(record.value(), ClickEvent.class);
        } catch (Exception e) {
            discarded.increment();
            LOGGER.log(Level.WARNING, "Skipping non-replayable DLT record at offset " + record.offset()
                    + " (partition " + record.partition() + "): " + e.getMessage());
            return;
        }
        // Blocking send: if Kafka rejects it, the exception stops the offset from being committed
        kafkaTemplate.send(KafkaConfig.CLICK_TOPIC, event.shortCode(), event).get();
        replayed.increment();
    }

    public boolean start() {
        MessageListenerContainer container = container();
        if (!container.isRunning()) {
            container.start();
            LOGGER.info("DLT replay started.");
        }
        return container.isRunning();
    }

    public boolean stop() {
        MessageListenerContainer container = container();
        if (container.isRunning()) {
            container.stop();
            LOGGER.info("DLT replay stopped.");
        }
        return container.isRunning();
    }

    public boolean isRunning() {
        return container().isRunning();
    }

    private MessageListenerContainer container() {
        MessageListenerContainer container = registry.getListenerContainer(KafkaConfig.DLT_REPLAY_LISTENER_ID);
        if (container == null) {
            throw new IllegalStateException("DLT replay listener is not registered");
        }
        return container;
    }
}
