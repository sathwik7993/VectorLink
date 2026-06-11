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

    private final DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository;

    public AnalyticsController(DailyUrlAnalyticsRepository dailyUrlAnalyticsRepository) {
        this.dailyUrlAnalyticsRepository = dailyUrlAnalyticsRepository;
    }

    /**
     * Endpoint to fetch daily aggregated metrics for a specific short code.
     */
    @GetMapping("/api/v1/analytics/{shortCode}")
    public ResponseEntity<List<DailyUrlAnalytics>> getUrlAnalytics(@PathVariable String shortCode) {
        List<DailyUrlAnalytics> analytics = dailyUrlAnalyticsRepository.findByShortCodeOrderByClickDateAsc(shortCode);
        return ResponseEntity.ok(analytics);
    }
}
