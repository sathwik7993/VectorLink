package com.shortener.service;

import com.shortener.config.KafkaConfig;
import com.shortener.dto.ClickEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

/**
 * Consumes batches of ClickEvents from Kafka and delegates persistence to ClickEventProcessor.
 * Disabled entirely when app.kafka.enabled=false, in which case UrlShortenerService writes click
 * events directly instead.
 *
 * Failure handling: if a batch fails, the events are reprocessed one by one to find the failing
 * record, and a BatchListenerFailedException tells the DefaultErrorHandler (KafkaConfig) to commit
 * everything before it, retry it, and finally publish it to url-clicks.DLT.
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
    public void consumeBatch(List<ConsumerRecord<String, ClickEvent>> records, Acknowledgment ack) {
        if (records == null || records.isEmpty()) {
            ack.acknowledge();
            return;
        }

        LOGGER.info("Consuming Kafka click analytics batch. Size: " + records.size());

        Timer.Sample sample = Timer.start(meterRegistry);
        String batchStatus = "success";

        try {
            // A null value means ErrorHandlingDeserializer could not parse the record (poison pill).
            // Process the good records in front of it, then hand the poison pill to the error handler.
            int poisonIndex = -1;
            List<ClickEvent> events = new ArrayList<>(records.size());
            for (int i = 0; i < records.size(); i++) {
                ClickEvent event = records.get(i).value();
                if (event == null) {
                    poisonIndex = i;
                    break;
                }
                events.add(event);
            }

            processWithFailureIsolation(events);

            if (poisonIndex >= 0) {
                throw new BatchListenerFailedException("Undeserializable click event", poisonIndex);
            }

            ack.acknowledge();
            LOGGER.fine("Successfully processed and committed offset for " + records.size() + " click events.");
        } catch (Exception e) {
            batchStatus = "failure";
            LOGGER.log(Level.WARNING, "Analytics batch failed; delegating to error handler for retry/DLT.", e);
            throw e;
        } finally {
            sample.stop(Timer.builder("analytics.batch.latency")
                    .description("Latency of processing analytics batch")
                    .tag("status", batchStatus)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }

    /**
     * Persists the events as one batch. If that fails, persists them one at a time and throws a
     * BatchListenerFailedException at the first event that still fails, so only that record is
     * retried and dead-lettered. Persistence is idempotent, so reprocessing is safe.
     */
    private void processWithFailureIsolation(List<ClickEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        try {
            clickEventProcessor.processEvents(events);
            countConsumed(events.size());
            return;
        } catch (Exception batchError) {
            LOGGER.log(Level.WARNING, "Batch persist failed; isolating failing record.", batchError);
        }

        for (int i = 0; i < events.size(); i++) {
            try {
                clickEventProcessor.processEvents(List.of(events.get(i)));
                countConsumed(1);
            } catch (Exception recordError) {
                throw new BatchListenerFailedException("Failed to persist click event", recordError, i);
            }
        }
    }

    private void countConsumed(int count) {
        Counter.builder("analytics.events.consumed")
                .description("Total number of analytics click events consumed from Kafka")
                .register(meterRegistry)
                .increment(count);
    }
}
