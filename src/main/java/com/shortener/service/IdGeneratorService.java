package com.shortener.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class IdGeneratorService {

    private static final Logger LOGGER = Logger.getLogger(IdGeneratorService.class.getName());
    private static final String REDIS_COUNTER_KEY = "global:url:sequence";
    private static final int ALLOCATION_SIZE = 1000;
    
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    private long currentLo = 0;
    private long maxHi = 0;

    public IdGeneratorService(StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("ALTER SEQUENCE seq_short_urls INCREMENT BY 1000");
            LOGGER.info("Altered sequence 'seq_short_urls' increment to 1000 for HiLo allocation.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to alter sequence increment to 1000: " + e.getMessage());
        }
    }

    /**
     * Generates a unique 62-bit ID. 
     * Tries Redis counter first. If Redis is unavailable, falls back to a PostgreSQL HiLo sequence.
     */
    public long nextId() {
        if (UrlShortenerService.isRedisDown()) {
            return getNextHiLoId();
        }

        try {
            Long nextId = redisTemplate.opsForValue().increment(REDIS_COUNTER_KEY);
            if (nextId != null) {
                return nextId;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis sequence generation failed, opening circuit breaker and falling back to HiLo: " + e.getMessage());
            UrlShortenerService.setRedisDown(true);
        }

        return getNextHiLoId();
    }

    private synchronized long getNextHiLoId() {
        if (currentLo >= maxHi) {
            long nextVal = fetchNextSequenceValue();
            maxHi = nextVal;
            currentLo = nextVal - ALLOCATION_SIZE + 1;
        } else {
            currentLo++;
        }
        return currentLo;
    }

    private long fetchNextSequenceValue() {
        String sql = "SELECT nextval('seq_short_urls')";
        Long id = jdbcTemplate.queryForObject(sql, Long.class);
        if (id == null) {
            throw new RuntimeException("Failed to generate ID from PostgreSQL sequence fallback.");
        }
        return id;
    }
}
