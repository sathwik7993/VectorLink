package com.shortener.controller;

import com.shortener.entity.DailyUrlAnalytics;
import com.shortener.repository.DailyUrlAnalyticsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AnalyticsController {

    private static final java.util.Map<String, java.util.List<org.springframework.web.servlet.mvc.method.annotation.SseEmitter>> emitters = 
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void broadcastClick(String shortCode, Object event) {
        java.util.List<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> list = emitters.get(shortCode);
        if (list != null) {
            java.util.List<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> deadEmitters = new java.util.ArrayList<>();
            for (org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter : list) {
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("click")
                            .data(event));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            list.removeAll(deadEmitters);
        }
    }

    @GetMapping(value = "/api/v1/analytics/{shortCode}/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamClicks(@PathVariable String shortCode) {
        // Only the link owner (or an admin) may watch its live clicks; check before registering an emitter
        if (urlShortenerService.findOwnedLink(shortCode).isEmpty()) {
            throw new com.shortener.exception.ResourceNotFoundException("Short URL not found: " + shortCode);
        }
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(30 * 60 * 1000L); // 30 minutes timeout
        java.util.List<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> list = emitters.computeIfAbsent(shortCode, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError((e) -> list.remove(emitter));

        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("connected")
                    .data("Streaming connected"));
        } catch (Exception e) {
            list.remove(emitter);
        }

        return emitter;
    }

    private final DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository;
    private final com.shortener.repository.ClickLogRepository clickLogRepository;
    private final com.shortener.service.UrlShortenerService urlShortenerService;

    public AnalyticsController(
            DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository,
            com.shortener.repository.ClickLogRepository clickLogRepository,
            com.shortener.service.UrlShortenerService urlShortenerService) {
        this.dailyUrlAnalyticsRepository = dailyUrlAnalyticsRepository;
        this.clickLogRepository = clickLogRepository;
        this.urlShortenerService = urlShortenerService;
    }

    /**
     * Endpoint to fetch daily aggregated metrics for a specific short code.
     */
    @GetMapping("/api/v1/analytics/{shortCode}")
    public ResponseEntity<List<DailyUrlAnalytics>> getUrlAnalytics(@PathVariable String shortCode) {
        if (urlShortenerService.findOwnedLink(shortCode).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<DailyUrlAnalytics> analytics = dailyUrlAnalyticsRepository.findByShortCodeOrderByClickDateAsc(shortCode);
        return ResponseEntity.ok(analytics);
    }

    /**
     * Endpoint to fetch country distribution metrics for a specific short code.
     */
    @GetMapping("/api/v1/analytics/{shortCode}/countries")
    public ResponseEntity<java.util.Map<String, Long>> getCountryAnalytics(@PathVariable String shortCode) {
        com.shortener.entity.ShortUrl shortUrl = urlShortenerService.findOwnedLink(shortCode).orElse(null);

        if (shortUrl == null) {
            return ResponseEntity.notFound().build();
        }

        List<Object[]> results = clickLogRepository.getCountryCounts(shortUrl.getShortCode(), shortUrl.getCustomAlias());
        java.util.Map<String, Long> countryCounts = new java.util.HashMap<>();
        for (Object[] result : results) {
            String country = (String) result[0];
            Long count = (Long) result[1];
            if (country != null) {
                String cc = country.toUpperCase();
                if (cc.equals("LO")) {
                    cc = "IN";
                }
                countryCounts.put(cc, countryCounts.getOrDefault(cc, 0L) + count);
            }
        }
        return ResponseEntity.ok(countryCounts);
    }

    /**
     * Endpoint to fetch referrer distribution metrics for a specific short code.
     */
    @GetMapping("/api/v1/analytics/{shortCode}/referrers")
    public ResponseEntity<java.util.Map<String, Long>> getReferrerAnalytics(@PathVariable String shortCode) {
        com.shortener.entity.ShortUrl shortUrl = urlShortenerService.findOwnedLink(shortCode).orElse(null);

        if (shortUrl == null) {
            return ResponseEntity.notFound().build();
        }

        List<Object[]> results = clickLogRepository.getReferrerCounts(shortUrl.getShortCode(), shortUrl.getCustomAlias());
        java.util.Map<String, Long> referrerCounts = new java.util.LinkedHashMap<>();
        for (Object[] result : results) {
            String ref = (String) result[0];
            Long count = (Long) result[1];
            referrerCounts.put(ref, count);
        }
        return ResponseEntity.ok(referrerCounts);
    }

    /**
     * Endpoint to fetch hourly traffic distribution metrics for a specific short code.
     */
    @GetMapping("/api/v1/analytics/{shortCode}/hourly")
    public ResponseEntity<java.util.Map<Integer, Long>> getHourlyAnalytics(@PathVariable String shortCode) {
        com.shortener.entity.ShortUrl shortUrl = urlShortenerService.findOwnedLink(shortCode).orElse(null);

        if (shortUrl == null) {
            return ResponseEntity.notFound().build();
        }

        List<Object[]> results = clickLogRepository.getHourlyTraffic(shortUrl.getShortCode(), shortUrl.getCustomAlias());
        java.util.Map<Integer, Long> hourlyCounts = new java.util.TreeMap<>();
        // Pre-fill all hours 0-23 with 0
        for (int i = 0; i < 24; i++) {
            hourlyCounts.put(i, 0L);
        }
        for (Object[] result : results) {
            Integer hour = (Integer) result[0];
            Long count = (Long) result[1];
            if (hour != null) {
                hourlyCounts.put(hour, count);
            }
        }
        return ResponseEntity.ok(hourlyCounts);
    }

    public static record GeoLocationDto(
            Double latitude,
            Double longitude,
            String city,
            String region,
            String countryCode,
            Long clickCount,
            java.time.Instant lastClickTimestamp
    ) {}

    /**
     * Endpoint to fetch precise geographic click coordinates and counts.
     */
    @GetMapping("/api/v1/analytics/{shortCode}/locations")
    public ResponseEntity<List<GeoLocationDto>> getGeographicAnalytics(@PathVariable String shortCode) {
        com.shortener.entity.ShortUrl shortUrl = urlShortenerService.findOwnedLink(shortCode).orElse(null);

        if (shortUrl == null) {
            return ResponseEntity.notFound().build();
        }

        List<Object[]> results = clickLogRepository.getGeographicLocations(shortUrl.getShortCode(), shortUrl.getCustomAlias());
        List<GeoLocationDto> locations = new java.util.ArrayList<>();
        for (Object[] result : results) {
            Double lat = (Double) result[0];
            Double lon = (Double) result[1];
            String city = (String) result[2];
            String region = (String) result[3];
            String country = (String) result[4];
            Long count = (Long) result[5];
            java.time.Instant lastClick = (java.time.Instant) result[6];

            if ("LO".equalsIgnoreCase(country)) {
                country = "IN";
            }

            locations.add(new GeoLocationDto(lat, lon, city, region, country, count, lastClick));
        }
        return ResponseEntity.ok(locations);
    }
}
