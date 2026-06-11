package com.shortener.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class IdGeneratorService {

    private static final Logger LOGGER = Logger.getLogger(IdGeneratorService.class.getName());
    private static final String REDIS_COUNTER_KEY = "global:url:sequence";
    
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public IdGeneratorService(StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Generates a unique 62-bit ID. 
     * Tries Redis counter first. If Redis is unavailable, falls back to a Postgres sequence.
     */
    public long nextId() {
        try {
            Long nextId = redisTemplate.opsForValue().increment(REDIS_COUNTER_KEY);
            if (nextId != null) {
                return nextId;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Redis sequence generation failed, falling back to PostgreSQL sequence: " + e.getMessage());
        }

        return getPostgresSequenceFallback();
    }

    private long getPostgresSequenceFallback() {
        String sql = "SELECT nextval('seq_short_urls')";
        Long id = jdbcTemplate.queryForObject(sql, Long.class);
        if (id == null) {
            throw new RuntimeException("Failed to generate ID from PostgreSQL sequence fallback.");
        }
        return id;
    }
}
