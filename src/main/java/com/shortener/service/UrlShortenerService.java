package com.shortener.service;

import com.shortener.config.KafkaConfig;
import com.shortener.dto.ClickEvent;
import com.shortener.dto.UrlRequest;
import com.shortener.dto.UrlResponse;
import com.shortener.entity.ShortUrl;
import com.shortener.exception.ResourceNotFoundException;
import com.shortener.repository.ShortUrlRepository;
import com.shortener.util.Base62;
import com.shortener.util.FeistelCipher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UrlShortenerService {

    private static final Logger LOGGER = Logger.getLogger(UrlShortenerService.class.getName());
    private static final String REDIS_PREFIX = "url:";
    private static final long DEFAULT_TTL_SECONDS = 604800; // 7 days
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{3,30}$");

    private final IdGeneratorService idGenerator;
    private final ShortUrlRepository shortUrlRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CacheManager caffeineCacheManager;

    public UrlShortenerService(
            IdGeneratorService idGenerator,
            ShortUrlRepository shortUrlRepository,
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager) {
        this.idGenerator = idGenerator;
        this.shortUrlRepository = shortUrlRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.caffeineCacheManager = caffeineCacheManager;
    }

    /**
     * Creates a new short URL, saves to database, and pre-seeds Redis cache.
     * Supports optional custom aliases.
     */
    @Transactional
    public UrlResponse createShortUrl(UrlRequest request) {
        long sequenceId = idGenerator.nextId();

        // 1. Obfuscate ID using Feistel Cipher to prevent enumeration attacks
        long obfuscatedId = FeistelCipher.encrypt(sequenceId);

        // 2. Base62 encode the obfuscated ID
        String shortCode = Base62.encode(obfuscatedId);

        long ttlSeconds = request.ttlSeconds() != null ? request.ttlSeconds() : DEFAULT_TTL_SECONDS;
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        // 3. Validate and process custom alias if provided
        String customAlias = null;
        if (request.customAlias() != null && !request.customAlias().trim().isEmpty()) {
            customAlias = request.customAlias().trim();
            if (!ALIAS_PATTERN.matcher(customAlias).matches()) {
                throw new IllegalArgumentException(
                        "Custom alias must be 3-30 characters long and contain only letters, numbers, and hyphens.");
            }
            // Check uniqueness
            Optional<ShortUrl> existing = shortUrlRepository.findByCustomAlias(customAlias);
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Custom alias '" + customAlias + "' is already in use.");
            }
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .id(sequenceId)
                .shortCode(shortCode)
                .longUrl(request.longUrl())
                .customAlias(customAlias)
                .active(true)
                .clickCount(0)
                .expiresAt(expiresAt)
                .build();

        // 4. Save to database
        shortUrlRepository.save(shortUrl);

        // 5. Pre-seed Redis cache (Write-Through) to guarantee a 100% cache hit rate initially
        try {
            redisTemplate.opsForValue().set(
                    REDIS_PREFIX + shortCode,
                    request.longUrl(),
                    Duration.ofSeconds(ttlSeconds)
            );
            // Also cache the custom alias mapping if provided
            if (customAlias != null) {
                redisTemplate.opsForValue().set(
                        REDIS_PREFIX + customAlias,
                        request.longUrl(),
                        Duration.ofSeconds(ttlSeconds)
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to seed Redis cache on creation: " + e.getMessage());
        }

        // Return representation
        String qrCodeUrl = "/api/v1/qr/" + shortCode;
        return new UrlResponse(shortCode, request.longUrl(), expiresAt, customAlias, true, 0, qrCodeUrl);
    }

    /**
     * Resolves a short code (or custom alias) to its destination long URL.
     * Uses L1 (Caffeine) and L2 (Redis) cache before querying PostgreSQL with a stampede lock.
     * Checks if the URL is active before redirecting.
     */
    public String resolveUrl(String shortCode) {
        // 1. Read L1 Cache (Caffeine local, sub-microsecond)
        Cache l1Cache = caffeineCacheManager.getCache("l1-urls");
        if (l1Cache != null) {
            Cache.ValueWrapper wrapper = l1Cache.get(shortCode);
            if (wrapper != null) {
                return (String) wrapper.get();
            }
        }

        // 2. Read L2 Cache (Redis distributed, <1ms)
        String redisKey = REDIS_PREFIX + shortCode;
        String longUrl = null;
        try {
            longUrl = redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to query Redis L2 cache: " + e.getMessage());
        }

        if (longUrl != null) {
            if (l1Cache != null) {
                l1Cache.put(shortCode, longUrl);
            }
            return longUrl;
        }

        // 3. Cache Miss: Query PostgreSQL
        // Synchronize on the shortCode string to block Thundering Herd (Cache Stampede)
        synchronized (shortCode.intern()) {
            // Double-check L2 cache in case another concurrent request just populated it
            try {
                longUrl = redisTemplate.opsForValue().get(redisKey);
            } catch (Exception e) {}

            if (longUrl != null) {
                return longUrl;
            }

            // Fetch from database - try shortCode first, then customAlias
            ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                    .or(() -> shortUrlRepository.findByCustomAlias(shortCode))
                    .orElseThrow(() -> new ResourceNotFoundException("Short URL not found or expired"));

            if (shortUrl.isExpired()) {
                throw new ResourceNotFoundException("Short URL has expired");
            }

            if (!shortUrl.isActive()) {
                throw new ResourceNotFoundException("Short URL is currently inactive");
            }

            longUrl = shortUrl.getLongUrl();

            // Warm both L1 and L2 caches
            long remainingSeconds = Duration.between(Instant.now(), shortUrl.getExpiresAt()).getSeconds();
            if (remainingSeconds > 0) {
                try {
                    redisTemplate.opsForValue().set(redisKey, longUrl, Duration.ofSeconds(remainingSeconds));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to update Redis L2 cache: " + e.getMessage());
                }
            }

            if (l1Cache != null) {
                l1Cache.put(shortCode, longUrl);
            }
        }

        // 4. Increment click count asynchronously
        incrementClickCountAsync(shortCode);

        return longUrl;
    }

    /**
     * Returns all URLs ordered by creation date (newest first).
     */
    public List<UrlResponse> listAllUrls() {
        List<ShortUrl> urls = shortUrlRepository.findAllByOrderByCreatedAtDesc();
        return urls.stream()
                .map(url -> new UrlResponse(
                        url.getShortCode(),
                        url.getLongUrl(),
                        url.getExpiresAt(),
                        url.getCustomAlias(),
                        url.isActive(),
                        url.getClickCount(),
                        "/api/v1/qr/" + url.getShortCode()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Deletes a URL by its short code and evicts it from all caches.
     */
    @Transactional
    public void deleteUrl(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found: " + shortCode));

        shortUrlRepository.delete(shortUrl);

        // Evict from caches
        evictFromCaches(shortCode, shortUrl.getCustomAlias());
    }

    /**
     * Toggles the active/inactive state of a URL.
     */
    @Transactional
    public UrlResponse toggleUrlActive(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found: " + shortCode));

        shortUrl.setActive(!shortUrl.isActive());
        shortUrlRepository.save(shortUrl);

        // Evict from caches so the active state is re-checked on next access
        evictFromCaches(shortCode, shortUrl.getCustomAlias());

        return new UrlResponse(
                shortUrl.getShortCode(),
                shortUrl.getLongUrl(),
                shortUrl.getExpiresAt(),
                shortUrl.getCustomAlias(),
                shortUrl.isActive(),
                shortUrl.getClickCount(),
                "/api/v1/qr/" + shortUrl.getShortCode()
        );
    }

    /**
     * Increments click count asynchronously in the database.
     */
    @Transactional
    @Async
    public void incrementClickCountAsync(String shortCode) {
        try {
            shortUrlRepository.incrementClickCount(shortCode);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to increment click count for: " + shortCode, e);
        }
    }

    /**
     * Evicts a URL from L1 (Caffeine) and L2 (Redis) caches.
     */
    private void evictFromCaches(String shortCode, String customAlias) {
        // Evict from L1 Caffeine cache
        Cache l1Cache = caffeineCacheManager.getCache("l1-urls");
        if (l1Cache != null) {
            l1Cache.evict(shortCode);
            if (customAlias != null) {
                l1Cache.evict(customAlias);
            }
        }

        // Evict from L2 Redis cache
        try {
            redisTemplate.delete(REDIS_PREFIX + shortCode);
            if (customAlias != null) {
                redisTemplate.delete(REDIS_PREFIX + customAlias);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to evict from Redis cache: " + e.getMessage());
        }
    }

    /**
     * Publishes a redirect click event asynchronously to Kafka for background processing.
     */
    public void trackClick(String shortCode, String referrer, String userAgent, String ipAddress) {
        Instant clickedAt = Instant.now();
        String deviceType = parseDeviceType(userAgent);
        String countryCode = "US"; // Mock or geo-ip lookup fallback

        // Generate an event ID for database idempotency (deduplication)
        String eventId = generateEventId(shortCode, clickedAt, ipAddress, userAgent);

        ClickEvent clickEvent = new ClickEvent(
                shortCode,
                clickedAt,
                referrer,
                userAgent,
                ipAddress,
                countryCode,
                deviceType,
                eventId
        );

        // Publish asynchronously to Kafka, partitioning by shortCode to keep events for the same URL in-order
        try {
            kafkaTemplate.send(KafkaConfig.CLICK_TOPIC, shortCode, clickEvent);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to publish click event to Kafka for shortCode: " + shortCode, e);
        }
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent == null) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        } else if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }
        return "desktop";
    }

    private String generateEventId(String shortCode, Instant clickedAt, String ip, String userAgent) {
        try {
            String input = shortCode + ":" + clickedAt.toEpochMilli() + ":" + ip + ":" + userAgent;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString(); // Fallback
        }
    }
}
