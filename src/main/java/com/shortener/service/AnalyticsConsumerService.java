package com.shortener.service;

import com.shortener.config.KafkaConfig;
import com.shortener.dto.ClickEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

/**
 * Consumes batches of ClickEvents from Kafka and delegates persistence to ClickEventProcessor.
 * Disabled entirely when app.kafka.enabled=false (e.g. the free-tier prod profile, which has
 * no Kafka broker available and writes click events synchronously instead — see UrlShortenerService).
 */
@Service
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsConsumerService {

    private static final Logger LOGGER = Logger.getLogger(AnalyticsConsumerService.class.getName());

    private final ClickEventProcessor clickEventProcessor;
    private final MeterRegistry meterRegistry;

    public AnalyticsConsumerService(
            ClickEventProcessor clickEventProcessor,
            MeterRegistry meterRegistry) {
        this.clickEventProcessor = clickEventProcessor;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = KafkaConfig.CLICK_TOPIC,
            groupId = "url-analytics-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBatch(List<ClickEvent> events, Acknowledgment ack) {
        if (events == null || events.isEmpty()) {
            ack.acknowledge();
            return;
        }

        LOGGER.info("Consuming Kafka click analytics batch. Size: " + events.size());

        Timer.Sample sample = Timer.start(meterRegistry);
        String batchStatus = "success";

        try {
            clickEventProcessor.processEvents(events);

            ack.acknowledge();
            LOGGER.fine("Successfully processed and committed offset for " + events.size() + " click events.");

            Counter.builder("analytics.events.consumed")
                    .description("Total number of analytics click events consumed from Kafka")
                    .register(meterRegistry)
                    .increment(events.size());
        } catch (Exception e) {
            batchStatus = "failure";
            LOGGER.log(Level.SEVERE, "Database persist failed for analytics batch. Offsets will not be committed.", e);
            // Throwing the exception forces Kafka to trigger standard retry/re-deliver policies
            throw e;
        } finally {
            sample.stop(Timer.builder("analytics.batch.latency")
                    .description("Latency of processing analytics batch")
                    .tag("status", batchStatus)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }
}
