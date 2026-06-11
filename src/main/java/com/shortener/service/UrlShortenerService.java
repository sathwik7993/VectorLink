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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class UrlShortenerService {

    private static final Logger LOGGER = Logger.getLogger(UrlShortenerService.class.getName());
    private static final String REDIS_PREFIX = "url:";
    private static final long DEFAULT_TTL_SECONDS = 604800; // 7 days

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

        ShortUrl shortUrl = ShortUrl.builder()
                .id(sequenceId)
                .shortCode(shortCode)
                .longUrl(request.longUrl())
                .expiresAt(expiresAt)
                .build();

        // 3. Save to database
        shortUrlRepository.save(shortUrl);

        // 4. Pre-seed Redis cache (Write-Through) to guarantee a 100% cache hit rate initially
        try {
            redisTemplate.opsForValue().set(
                    REDIS_PREFIX + shortCode,
                    request.longUrl(),
                    Duration.ofSeconds(ttlSeconds)
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to seed Redis cache on creation: " + e.getMessage());
        }

        // Return representation
        return new UrlResponse(shortCode, request.longUrl(), expiresAt);
    }

    /**
     * Resolves a short code to its destination long URL.
     * Uses L1 (Caffeine) and L2 (Redis) cache before querying PostgreSQL with a stampede lock.
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

            // Fetch from database
            ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Short URL not found or expired"));

            if (shortUrl.isExpired()) {
                throw new ResourceNotFoundException("Short URL has expired");
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

        return longUrl;
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
