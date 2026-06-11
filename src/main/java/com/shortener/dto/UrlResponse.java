package com.shortener.dto;

import java.time.Instant;

public record UrlResponse(
    String shortUrl,
    String longUrl,
    Instant expiresAt
) {}
