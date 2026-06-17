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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

@Service
public class AnalyticsConsumerService {

    private static final Logger LOGGER = Logger.getLogger(AnalyticsConsumerService.class.getName());

    private final ClickLogRepository clickLogRepository;
    private final DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository;
    private final MeterRegistry meterRegistry;

    public AnalyticsConsumerService(
            ClickLogRepository clickLogRepository,
            DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository,
            MeterRegistry meterRegistry) {
        this.clickLogRepository = clickLogRepository;
        this.dailyUrlAnalyticsRepository = dailyUrlAnalyticsRepository;
        this.meterRegistry = meterRegistry;
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

        Timer.Sample sample = Timer.start(meterRegistry);
        String batchStatus = "success";

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
                        .browser(e.browser())
                        .operatingSystem(e.operatingSystem())
                        .latitude(e.latitude())
                        .longitude(e.longitude())
                        .city(e.city())
                        .region(e.region())
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
                    .browserChromeCount(0)
                    .browserSafariCount(0)
                    .browserFirefoxCount(0)
                    .browserOtherCount(0)
                    .osWindowsCount(0)
                    .osMacosCount(0)
                    .osAndroidCount(0)
                    .osIosCount(0)
                    .osOtherCount(0)
                    .build());

            agg.setClickCount(agg.getClickCount() + 1);
            if (event.deviceType() != null) {
                switch (event.deviceType().toLowerCase()) {
                    case "desktop" -> agg.setDeviceDesktopCount(agg.getDeviceDesktopCount() + 1);
                    case "mobile" -> agg.setDeviceMobileCount(agg.getDeviceMobileCount() + 1);
                    case "tablet" -> agg.setDeviceTabletCount(agg.getDeviceTabletCount() + 1);
                    default -> {}
                }
            }

            if (event.browser() != null) {
                switch (event.browser()) {
                    case "Chrome" -> agg.setBrowserChromeCount(agg.getBrowserChromeCount() + 1);
                    case "Safari" -> agg.setBrowserSafariCount(agg.getBrowserSafariCount() + 1);
                    case "Firefox" -> agg.setBrowserFirefoxCount(agg.getBrowserFirefoxCount() + 1);
                    default -> agg.setBrowserOtherCount(agg.getBrowserOtherCount() + 1);
                }
            } else {
                agg.setBrowserOtherCount(agg.getBrowserOtherCount() + 1);
            }

            if (event.operatingSystem() != null) {
                switch (event.operatingSystem()) {
                    case "Windows" -> agg.setOsWindowsCount(agg.getOsWindowsCount() + 1);
                    case "macOS" -> agg.setOsMacosCount(agg.getOsMacosCount() + 1);
                    case "Android" -> agg.setOsAndroidCount(agg.getOsAndroidCount() + 1);
                    case "iOS" -> agg.setOsIosCount(agg.getOsIosCount() + 1);
                    default -> agg.setOsOtherCount(agg.getOsOtherCount() + 1);
                }
            } else {
                agg.setOsOtherCount(agg.getOsOtherCount() + 1);
            }
        }

        try {
            // 3. Batch insert raw click logs (Deduplicated on event_id + clicked_at)
            clickLogRepository.batchInsert(clickLogs);

            // 4. Batch upsert daily aggregates (Atomic additions direct in PostgreSQL)
            dailyUrlAnalyticsRepository.batchUpsert(new ArrayList<>(aggregateMap.values()));

            // Broadcast events to active Server-Sent Events stream subscribers
            for (ClickEvent event : events) {
                try {
                    String country = event.countryCode();
                    if ("LO".equalsIgnoreCase(country)) {
                        country = "IN";
                    }
                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("shortCode", event.shortCode());
                    payload.put("clickedAt", event.clickedAt().toString());
                    payload.put("referrer", event.referrer() != null ? event.referrer() : "Direct");
                    payload.put("userAgent", event.userAgent());
                    payload.put("ipAddress", event.ipAddress());
                    payload.put("countryCode", country);
                    payload.put("deviceType", event.deviceType());
                    payload.put("browser", event.browser());
                    payload.put("operatingSystem", event.operatingSystem());
                    payload.put("latitude", event.latitude());
                    payload.put("longitude", event.longitude());
                    payload.put("city", event.city() != null ? event.city() : "Unknown");
                    payload.put("region", event.region() != null ? event.region() : "Unknown");

                    com.shortener.controller.AnalyticsController.broadcastClick(event.shortCode(), payload);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to broadcast click stream event: " + ex.getMessage());
                }
            }

            // 5. Commit Kafka offset manually
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
