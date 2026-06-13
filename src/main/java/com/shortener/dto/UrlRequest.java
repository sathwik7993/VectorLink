package com.shortener.dto;

public record UrlRequest(
    String longUrl,
    Long ttlSeconds, // Optional time-to-live in seconds
    String customAlias, // Optional user-provided custom short code
    String password, // Optional password for protection (plain text)
    String fallbackUrl // Optional fallback URL if expired/inactive
) {}
