package com.shortener.service;

import com.shortener.dto.ClickEvent;
import com.shortener.entity.ClickLog;
import com.shortener.entity.DailyUrlAnalytics;
import com.shortener.entity.DailyUrlAnalyticsKey;
import com.shortener.repository.ClickLogRepository;
import com.shortener.repository.DailyUrlAnalyticsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists ClickEvents to the click log and daily analytics aggregates.
 * Shared by the Kafka consumer (AnalyticsConsumerService) and by the
 * synchronous fallback path used when Kafka is disabled (app.kafka.enabled=false).
 */
@Service
public class ClickEventProcessor {

    private static final Logger LOGGER = Logger.getLogger(ClickEventProcessor.class.getName());

    private final ClickLogRepository clickLogRepository;
    private final DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository;

    public ClickEventProcessor(
            ClickLogRepository clickLogRepository,
            DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository) {
        this.clickLogRepository = clickLogRepository;
        this.dailyUrlAnalyticsRepository = dailyUrlAnalyticsRepository;
    }

    public void processEvents(List<ClickEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

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

        clickLogRepository.batchInsert(clickLogs);
        dailyUrlAnalyticsRepository.batchUpsert(new ArrayList<>(aggregateMap.values()));

        for (ClickEvent event : events) {
            try {
                String country = event.countryCode();
                if ("LO".equalsIgnoreCase(country)) {
                    country = "IN";
                }
                Map<String, Object> payload = new HashMap<>();
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
    }
}
