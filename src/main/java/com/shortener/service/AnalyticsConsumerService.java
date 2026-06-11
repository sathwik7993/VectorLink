package com.shortener.service;

import com.shortener.config.KafkaConfig;
import com.shortener.dto.ClickEvent;
import com.shortener.entity.ClickLog;
import com.shortener.entity.DailyUrlAnalytics;
import com.shortener.entity.DailyUrlAnalyticsKey;
import com.shortener.repository.ClickLogRepository;
import com.shortener.repository.DailyUrlAnalyticsRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class AnalyticsConsumerService {

    private static final Logger LOGGER = Logger.getLogger(AnalyticsConsumerService.class.getName());

    private final ClickLogRepository clickLogRepository;
    private final DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository;

    public AnalyticsConsumerService(
            ClickLogRepository clickLogRepository,
            DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository) {
        this.clickLogRepository = clickLogRepository;
        this.dailyUrlAnalyticsRepository = dailyUrlAnalyticsRepository;
    }

    /**
     * Consumes batches of ClickEvents from Kafka, aggregates them in memory,
     * writes them to PostgreSQL using batch JDBC inserts, and commits offsets.
     */
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

        // 1. Map DTO ClickEvents to Entity ClickLogs
        List<ClickLog> clickLogs = events.stream()
                .map(e -> ClickLog.builder()
                        .shortCode(e.shortCode())
                        .clickedAt(e.clickedAt())
                        .referrer(e.referrer())
                        .userAgent(e.userAgent())
                        .ipAddress(e.ipAddress())
                        .countryCode(e.countryCode())
                        .deviceType(e.deviceType())
                        .eventId(e.eventId())
                        .build()
                ).toList();

        // 2. Aggregate clicks in memory to reduce database lock and record contention
        Map<DailyUrlAnalyticsKey, DailyUrlAnalytics> aggregateMap = new HashMap<>();
        for (ClickEvent event : events) {
            LocalDate date = event.clickedAt().atZone(ZoneOffset.UTC).toLocalDate();
            DailyUrlAnalyticsKey key = new DailyUrlAnalyticsKey(event.shortCode(), date);

            DailyUrlAnalytics agg = aggregateMap.computeIfAbsent(key, k -> DailyUrlAnalytics.builder()
                    .shortCode(k.getShortCode())
                    .clickDate(k.getClickDate())
                    .clickCount(0L)
                    .deviceDesktopCount(0)
                    .deviceMobileCount(0)
                    .deviceTabletCount(0)
                    .build());

            agg.setClickCount(agg.getClickCount() + 1);
            switch (event.deviceType()) {
                case "desktop" -> agg.setDeviceDesktopCount(agg.getDeviceDesktopCount() + 1);
                case "mobile" -> agg.setDeviceMobileCount(agg.getDeviceMobileCount() + 1);
                case "tablet" -> agg.setDeviceTabletCount(agg.getDeviceTabletCount() + 1);
                default -> {}
            }
        }

        try {
            // 3. Batch insert raw click logs (Deduplicated on event_id + clicked_at)
            clickLogRepository.batchInsert(clickLogs);

            // 4. Batch upsert daily aggregates (Atomic additions direct in PostgreSQL)
            dailyUrlAnalyticsRepository.batchUpsert(new ArrayList<>(aggregateMap.values()));

            // 5. Commit Kafka offset manually
            ack.acknowledge();
            LOGGER.fine("Successfully processed and committed offset for " + events.size() + " click events.");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Database persist failed for analytics batch. Offsets will not be committed.", e);
            // Throwing the exception forces Kafka to trigger standard retry/re-deliver policies
            throw e;
        }
    }
}
