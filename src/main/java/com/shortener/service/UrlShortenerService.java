package com.shortener.service;

import com.shortener.config.KafkaConfig;
import com.shortener.dto.ClickEvent;
import com.shortener.dto.UrlRequest;
import com.shortener.dto.UrlResponse;
import com.shortener.entity.ShortUrl;
import com.shortener.exception.ResourceNotFoundException;
import com.shortener.repository.ShortUrlRepository;
import com.shortener.repository.UserRepository;
import com.shortener.entity.User;
import com.shortener.util.Base62;
import com.shortener.util.FeistelCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;

@Service
public class UrlShortenerService {

    private static final Logger LOGGER = Logger.getLogger(UrlShortenerService.class.getName());
    private static final String REDIS_PREFIX = "url:";
    private static final long DEFAULT_TTL_SECONDS = 604800; // 7 days
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{3,30}$");

    // L1 and L2 cache / database sequence circuit-breaker state
    private static final AtomicBoolean REDIS_DOWN = new AtomicBoolean(false);

    // Kafka failover queue and buffers
    private static final ConcurrentLinkedQueue<ClickEvent> KAFKA_BUFFER = new ConcurrentLinkedQueue<>();
    private static final int BUFFER_CAPACITY = 50000;
    private static final AtomicInteger BUFFER_SIZE = new AtomicInteger(0);
    private static final AtomicBoolean KAFKA_DOWN = new AtomicBoolean(false);
    private static final Object logFileLock = new Object();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final IdGeneratorService idGenerator;
    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CacheManager caffeineCacheManager;
    private final RestTemplate restTemplate;
    private final com.github.benmanes.caffeine.cache.Cache<String, GeoDetails> ipCountryCache;
    private final MeterRegistry meterRegistry;

    public UrlShortenerService(
            IdGeneratorService idGenerator,
            ShortUrlRepository shortUrlRepository,
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            MeterRegistry meterRegistry) {
        this.idGenerator = idGenerator;
        this.shortUrlRepository = shortUrlRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.caffeineCacheManager = caffeineCacheManager;
        this.meterRegistry = meterRegistry;
        
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(500);
        requestFactory.setReadTimeout(500);
        this.restTemplate = new RestTemplate(requestFactory);
        this.ipCountryCache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .expireAfterWrite(1, java.util.concurrent.TimeUnit.HOURS)
                .maximumSize(5000)
                .build();

        // Register Kafka fallback queue size Gauge
        Gauge.builder("kafka.fallback.queue.size", KAFKA_BUFFER, ConcurrentLinkedQueue::size)
                .description("Size of the Kafka fallback buffer queue")
                .register(meterRegistry);
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

        String passwordHash = null;
        if (request.password() != null && !request.password().isEmpty()) {
            passwordHash = hashPassword(request.password(), shortCode);
        }

        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String email = authentication.getName();
            currentUser = userRepository.findByEmail(email).orElse(null);
        }

        ShortUrl shortUrl = ShortUrl.builder()
                .id(sequenceId)
                .shortCode(shortCode)
                .longUrl(request.longUrl())
                .customAlias(customAlias)
                .active(true)
                .clickCount(0)
                .expiresAt(expiresAt)
                .passwordHash(passwordHash)
                .fallbackUrl(request.fallbackUrl())
                .user(currentUser)
                .build();

        // 4. Save to database
        shortUrlRepository.save(shortUrl);

        // 5. Pre-seed cache (Write-Through) to guarantee a 100% cache hit rate initially
        CachedUrlInfo cachedUrlInfo = new CachedUrlInfo(
                request.longUrl(),
                passwordHash,
                request.fallbackUrl(),
                true,
                expiresAt
        );

        try {
            String json = OBJECT_MAPPER.writeValueAsString(cachedUrlInfo);
            if (!isRedisDown()) {
                redisTemplate.opsForValue().set(
                        REDIS_PREFIX + shortCode,
                        json,
                        Duration.ofSeconds(ttlSeconds)
                );
                // Also cache the custom alias mapping if provided
                if (customAlias != null) {
                    redisTemplate.opsForValue().set(
                            REDIS_PREFIX + customAlias,
                            json,
                            Duration.ofSeconds(ttlSeconds)
                    );
                }
            }
            Cache l1Cache = caffeineCacheManager.getCache("l1-urls");
            if (l1Cache != null) {
                l1Cache.put(shortCode, cachedUrlInfo);
                if (customAlias != null) {
                    l1Cache.put(customAlias, cachedUrlInfo);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to seed cache on creation: " + e.getMessage());
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
    public String resolveUrl(String shortCode, String clientToken, String cookieValue, String userAgent) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = "success";
        String device = parseDeviceType(userAgent);
        String browser = parseBrowser(userAgent);

        try {
            Cache l1Cache = caffeineCacheManager.getCache("l1-urls");
            CachedUrlInfo cachedInfo = null;

            // 1. Read L1 Cache
            if (l1Cache != null) {
                Cache.ValueWrapper wrapper = l1Cache.get(shortCode);
                if (wrapper != null) {
                    Object cachedObj = wrapper.get();
                    if (cachedObj instanceof CachedUrlInfo) {
                        cachedInfo = (CachedUrlInfo) cachedObj;
                    } else if (cachedObj instanceof String) {
                        try {
                            cachedInfo = OBJECT_MAPPER.readValue((String) cachedObj, CachedUrlInfo.class);
                        } catch (Exception e) {}
                    }
                }
            }

            if (cachedInfo != null) {
                Counter.builder("cache.requests")
                        .tags("cache", "l1", "result", "hit")
                        .register(meterRegistry).increment();
            } else {
                Counter.builder("cache.requests")
                        .tags("cache", "l1", "result", "miss")
                        .register(meterRegistry).increment();
            }

            // 2. Read L2 Cache
            if (cachedInfo == null) {
                String redisKey = REDIS_PREFIX + shortCode;
                if (!isRedisDown()) {
                    try {
                        String json = redisTemplate.opsForValue().get(redisKey);
                        if (json != null) {
                            cachedInfo = OBJECT_MAPPER.readValue(json, CachedUrlInfo.class);
                            Counter.builder("cache.requests")
                                    .tags("cache", "l2", "result", "hit")
                                    .register(meterRegistry).increment();
                            if (l1Cache != null) {
                                l1Cache.put(shortCode, cachedInfo);
                            }
                        } else {
                            Counter.builder("cache.requests")
                                    .tags("cache", "l2", "result", "miss")
                                    .register(meterRegistry).increment();
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to query Redis L2 cache: " + e.getMessage());
                        setRedisDown(true);
                        Counter.builder("cache.requests")
                                .tags("cache", "l2", "result", "miss")
                                .register(meterRegistry).increment();
                    }
                } else {
                    Counter.builder("cache.requests")
                            .tags("cache", "l2", "result", "miss")
                            .register(meterRegistry).increment();
                }
            }

            // 3. Cache Miss: Query PostgreSQL
            if (cachedInfo == null) {
                synchronized (shortCode.intern()) {
                    // Double check L2
                    String redisKey = REDIS_PREFIX + shortCode;
                    if (!isRedisDown()) {
                        try {
                            String json = redisTemplate.opsForValue().get(redisKey);
                            if (json != null) {
                                cachedInfo = OBJECT_MAPPER.readValue(json, CachedUrlInfo.class);
                            }
                        } catch (Exception e) {
                            setRedisDown(true);
                        }
                    }

                    if (cachedInfo == null) {
                        Counter.builder("cache.requests")
                                .tags("cache", "db", "result", "hit")
                                .register(meterRegistry).increment();

                        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                                .or(() -> shortUrlRepository.findByCustomAlias(shortCode))
                                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found or expired"));

                        cachedInfo = new CachedUrlInfo(
                                shortUrl.getLongUrl(),
                                shortUrl.getPasswordHash(),
                                shortUrl.getFallbackUrl(),
                                shortUrl.isActive(),
                                shortUrl.getExpiresAt()
                        );

                        // Warm L1 & L2 caches if not expired
                        long remainingSeconds = Duration.between(Instant.now(), shortUrl.getExpiresAt()).getSeconds();
                        if (remainingSeconds > 0) {
                            if (!isRedisDown()) {
                                try {
                                    String json = OBJECT_MAPPER.writeValueAsString(cachedInfo);
                                    redisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(remainingSeconds));
                                } catch (Exception e) {
                                    LOGGER.log(Level.WARNING, "Failed to update Redis L2 cache: " + e.getMessage());
                                    setRedisDown(true);
                                }
                            }
                        }
                    } else {
                        Counter.builder("cache.requests")
                                .tags("cache", "l2", "result", "hit")
                                .register(meterRegistry).increment();
                    }

                    if (l1Cache != null) {
                        l1Cache.put(shortCode, cachedInfo);
                    }
                }
            }

            // 4. Validate expiration / activity state
            boolean isExpired = Instant.now().isAfter(cachedInfo.expiresAt());
            if (isExpired || !cachedInfo.active()) {
                if (cachedInfo.fallbackUrl() != null && !cachedInfo.fallbackUrl().trim().isEmpty()) {
                    return cachedInfo.fallbackUrl();
                }
                if (isExpired) {
                    throw new ResourceNotFoundException("Short URL has expired");
                } else {
                    throw new ResourceNotFoundException("Short URL is currently inactive");
                }
            }

            // 5. Password protection check
            if (cachedInfo.passwordHash() != null && !cachedInfo.passwordHash().trim().isEmpty()) {
                boolean authorized = false;
                if (clientToken != null && clientToken.equals(cachedInfo.passwordHash())) {
                    authorized = true;
                }
                if (!authorized && cookieValue != null && cookieValue.equals(cachedInfo.passwordHash())) {
                    authorized = true;
                }
                if (!authorized) {
                    throw new com.shortener.exception.PasswordRequiredException(shortCode);
                }
            }

            // 6. Increment click count asynchronously
            incrementClickCountAsync(shortCode);

            return cachedInfo.longUrl();
        } catch (com.shortener.exception.PasswordRequiredException e) {
            status = "password_required";
            throw e;
        } catch (ResourceNotFoundException e) {
            if (e.getMessage().toLowerCase().contains("not found")) {
                status = "not_found";
            } else if (e.getMessage().toLowerCase().contains("expired")) {
                status = "expired";
            } else if (e.getMessage().toLowerCase().contains("inactive")) {
                status = "inactive";
            } else {
                status = "not_found";
            }
            throw e;
        } catch (Exception e) {
            status = "error";
            throw e;
        } finally {
            sample.stop(Timer.builder("redirect.latency")
                    .description("Latency of short URL redirection")
                    .publishPercentileHistogram()
                    .register(meterRegistry));
            Counter.builder("redirect.count")
                    .description("Number of redirection requests")
                    .tags("device", device, "browser", browser, "status", status)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Returns all URLs ordered by creation date (newest first).
     */
    public List<UrlResponse> listAllUrls() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        List<ShortUrl> urls;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            
            if (isAdmin) {
                urls = shortUrlRepository.findAllByOrderByCreatedAtDesc();
            } else {
                String email = authentication.getName();
                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    urls = shortUrlRepository.findByUserOrderByCreatedAtDesc(userOpt.get());
                } else {
                    urls = java.util.Collections.emptyList();
                }
            }
        } else {
            urls = java.util.Collections.emptyList();
        }

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
    public void incrementClickCountAsync(String shortCode) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                shortUrlRepository.incrementClickCount(shortCode);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to increment click count for: " + shortCode, e);
            }
        });
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
        if (!isRedisDown()) {
            try {
                redisTemplate.delete(REDIS_PREFIX + shortCode);
                if (customAlias != null) {
                    redisTemplate.delete(REDIS_PREFIX + customAlias);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to evict from Redis cache: " + e.getMessage());
                setRedisDown(true);
            }
        }
    }

    /**
     * Publishes a redirect click event asynchronously to Kafka for background processing.
     */
    public void trackClick(String shortCode, String referrer, String userAgent, String ipAddress) {
        Instant clickedAt = Instant.now();
        String deviceType = parseDeviceType(userAgent);
        GeoDetails geo = getGeoDetailsFromIp(ipAddress);
        String browser = parseBrowser(userAgent);
        String operatingSystem = parseOS(userAgent);

        // Generate an event ID for database idempotency (deduplication)
        String eventId = generateEventId(shortCode, clickedAt, ipAddress, userAgent);

        ClickEvent clickEvent = new ClickEvent(
                shortCode,
                clickedAt,
                referrer,
                userAgent,
                ipAddress,
                geo.countryCode(),
                deviceType,
                eventId,
                browser,
                operatingSystem,
                geo.latitude(),
                geo.longitude(),
                geo.city(),
                geo.region()
        );

        if (isKafkaDown()) {
            bufferClickEvent(clickEvent);
            return;
        }

        // Publish asynchronously to Kafka, partitioning by shortCode to keep events for the same URL in-order
        try {
            java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>> future = 
                    kafkaTemplate.send(KafkaConfig.CLICK_TOPIC, shortCode, clickEvent);
            
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    LOGGER.log(Level.WARNING, "Failed to publish click event to Kafka for shortCode: " + shortCode, ex);
                    setKafkaDown(true);
                    bufferClickEvent(clickEvent);
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to publish click event to Kafka for shortCode: " + shortCode, e);
            setKafkaDown(true);
            bufferClickEvent(clickEvent);
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

    // State checkers and recovery methods
    public static boolean isRedisDown() {
        return REDIS_DOWN.get();
    }

    public static void setRedisDown(boolean down) {
        REDIS_DOWN.set(down);
    }

    public static boolean isKafkaDown() {
        return KAFKA_DOWN.get();
    }

    public static void setKafkaDown(boolean down) {
        KAFKA_DOWN.set(down);
    }

    public static ConcurrentLinkedQueue<ClickEvent> getKafkaBuffer() {
        return KAFKA_BUFFER;
    }

    public static void decrementBufferSize() {
        BUFFER_SIZE.decrementAndGet();
    }

    public static void bufferClickEventDirectly(ClickEvent clickEvent) {
        KAFKA_BUFFER.offer(clickEvent);
        BUFFER_SIZE.incrementAndGet();
    }

    public static void bufferClickEvent(ClickEvent clickEvent) {
        while (true) {
            int currentSize = BUFFER_SIZE.get();
            if (currentSize >= BUFFER_CAPACITY) {
                writeToDeadLetterLog(clickEvent);
                return;
            }
            if (BUFFER_SIZE.compareAndSet(currentSize, currentSize + 1)) {
                KAFKA_BUFFER.offer(clickEvent);
                return;
            }
        }
    }

    private static void writeToDeadLetterLog(ClickEvent event) {
        try {
            java.io.File file = new java.io.File("C:\\Users\\SATHWIK.CH\\Downloads\\projects\\URL shortner\\logs\\dead_letter_clicks.log");
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String json = OBJECT_MAPPER.writeValueAsString(event);
            synchronized (logFileLock) {
                java.nio.file.Files.writeString(
                        file.toPath(),
                        json + System.lineSeparator(),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND
                );
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to write click event to dead letter log: " + event, e);
        }
    }

    @Scheduled(fixedRate = 30000)
    public void pingRedisScheduler() {
        boolean pingSuccess = false;
        try {
            Boolean result = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                connection.ping();
                return true;
            });
            pingSuccess = Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // Ignore connection errors
        }

        if (pingSuccess) {
            if (REDIS_DOWN.getAndSet(false)) {
                LOGGER.info("Redis connection recovered. Circuit breaker reset. Restoring standard L1 cache TTL (15s).");
            }
        } else {
            if (!REDIS_DOWN.getAndSet(true)) {
                LOGGER.warning("Redis is unreachable. Circuit breaker active. Stretching L1 cache TTL (10m).");
            }
        }
    }

    public String parseBrowser(String userAgent) {
        if (userAgent == null) {
            return "Other";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg") || ua.contains("edge")) {
            return "Edge";
        } else if (ua.contains("firefox")) {
            return "Firefox";
        } else if (ua.contains("chrome")) {
            return "Chrome";
        } else if (ua.contains("safari")) {
            return "Safari";
        }
        return "Other";
    }

    public String parseOS(String userAgent) {
        if (userAgent == null) {
            return "Other";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "iOS";
        } else if (ua.contains("android")) {
            return "Android";
        } else if (ua.contains("windows")) {
            return "Windows";
        } else if (ua.contains("macintosh") || ua.contains("mac os x") || ua.contains("mac os")) {
            return "macOS";
        } else if (ua.contains("linux")) {
            return "Linux";
        }
        return "Other";
    }

    public static record GeoDetails(
            String countryCode,
            Double latitude,
            Double longitude,
            String city,
            String region
    ) {}

    public GeoDetails getGeoDetailsFromIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return new GeoDetails("US", 37.0902, -95.7129, "Washington", "District of Columbia");
        }
        ip = ip.trim();
        // Mumbai, London, New York, Tokyo, Sydney coordinates for local testing so they spread out beautifully on the 3D map
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("localhost")) {
            return new GeoDetails("IN", 19.0760, 72.8777, "Mumbai", "Maharashtra");
        } else if (ip.startsWith("192.168.1.")) {
            return new GeoDetails("GB", 51.5074, -0.1278, "London", "Greater London");
        } else if (ip.startsWith("192.168.2.")) {
            return new GeoDetails("US", 40.7128, -74.0060, "New York", "New York");
        } else if (ip.startsWith("192.168.")) {
            return new GeoDetails("JP", 35.6762, 139.6503, "Tokyo", "Tokyo");
        } else if (ip.startsWith("10.")) {
            return new GeoDetails("AU", -33.8688, 151.2093, "Sydney", "New South Wales");
        }

        if (ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.") || ip.startsWith("172.19.") || 
            ip.startsWith("172.20.") || ip.startsWith("172.21.") || ip.startsWith("172.22.") || ip.startsWith("172.23.") || 
            ip.startsWith("172.24.") || ip.startsWith("172.25.") || ip.startsWith("172.26.") || ip.startsWith("172.27.") || 
            ip.startsWith("172.28.") || ip.startsWith("172.29.") || ip.startsWith("172.30.") || ip.startsWith("172.31.")) {
            return new GeoDetails("IN", 19.0760, 72.8777, "Mumbai", "Maharashtra");
        }

        GeoDetails cached = ipCountryCache.getIfPresent(ip);
        if (cached != null) {
            return cached;
        }

        try {
            String url = "http://ip-api.com/json/" + ip + "?fields=countryCode,regionName,city,lat,lon";
            IpApiResponse response = restTemplate.getForObject(url, IpApiResponse.class);
            if (response != null && response.countryCode() != null) {
                Double latitude = response.lat() != null ? response.lat() : 0.0;
                Double longitude = response.lon() != null ? response.lon() : 0.0;
                String city = response.city() != null ? response.city() : "Unknown";
                String region = response.regionName() != null ? response.regionName() : "Unknown";
                GeoDetails details = new GeoDetails(response.countryCode(), latitude, longitude, city, region);
                ipCountryCache.put(ip, details);
                return details;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to resolve IP geo details for " + ip + ": " + e.getMessage());
        }
        return new GeoDetails("US", 37.0902, -95.7129, "Washington", "District of Columbia");
    }

    public boolean verifyPassword(String shortCode, String password) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .or(() -> shortUrlRepository.findByCustomAlias(shortCode))
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found"));

        if (shortUrl.getPasswordHash() == null || shortUrl.getPasswordHash().isEmpty()) {
            return true;
        }
        if (password == null) {
            return false;
        }
        String inputHash = hashPassword(password, shortUrl.getShortCode());
        return shortUrl.getPasswordHash().equals(inputHash);
    }

    public String getPasswordHash(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .or(() -> shortUrlRepository.findByCustomAlias(shortCode))
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found"));
        return shortUrl.getPasswordHash();
    }

    public String hashPassword(String password, String salt) {
        if (password == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = password + ":" + salt;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    public static record CachedUrlInfo(
            String longUrl,
            String passwordHash,
            String fallbackUrl,
            boolean active,
            Instant expiresAt
    ) {}

    private static record IpApiResponse(
            String countryCode,
            String regionName,
            String city,
            Double lat,
            Double lon
    ) {}
}
