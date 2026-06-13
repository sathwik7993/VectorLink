package com.shortener.dto;

import java.time.Instant;

public record ClickEvent(
    String shortCode,
    Instant clickedAt,
    String referrer,
    String userAgent,
    String ipAddress,
    String countryCode,
    String deviceType,
    String eventId, // Unique key to guarantee idempotency in consumer
    String browser,
    String operatingSystem
) {}
