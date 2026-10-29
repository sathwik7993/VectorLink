package com.shortener.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HiLo ID generator backed by the PostgreSQL sequence 'seq_short_urls'.
 *
 * The sequence increments by ALLOCATION_SIZE, so each nextval() call reserves a whole block of
 * IDs (the "hi" part) which this instance then hands out from memory (the "lo" part). One DB
 * round-trip yields 1000 IDs, and because the sequence is the single source of truth, IDs are
 * unique across restarts, multiple app instances, and Redis outages.
 */
@Service
public class IdGeneratorService {

    private static final Logger LOGGER = Logger.getLogger(IdGeneratorService.class.getName());
    static final int ALLOCATION_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    private long currentLo = 0;
    private long maxHi = 0;

    public IdGeneratorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("ALTER SEQUENCE seq_short_urls INCREMENT BY " + ALLOCATION_SIZE);
            LOGGER.info("Sequence 'seq_short_urls' increment set to " + ALLOCATION_SIZE + " for HiLo allocation.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to alter sequence increment: " + e.getMessage());
        }
        reconcileSequenceWithExistingIds();
    }

    /**
     * Older builds handed out IDs from a Redis counter. If any of those IDs sit at or above the
     * next HiLo block, move the sequence past them so the first new block cannot collide.
     */
    private void reconcileSequenceWithExistingIds() {
        try {
            Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM short_urls", Long.class);
            Long lastValue = jdbcTemplate.queryForObject("SELECT last_value FROM seq_short_urls", Long.class);
            if (maxId != null && lastValue != null && maxId > lastValue) {
                jdbcTemplate.queryForObject("SELECT setval('seq_short_urls', ?)", Long.class, maxId + ALLOCATION_SIZE);
                LOGGER.warning("Advanced 'seq_short_urls' past existing max id " + maxId + " to avoid collisions.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to reconcile HiLo sequence with existing ids: " + e.getMessage());
        }
    }

    /**
     * Returns the next unique ID, fetching a new block from PostgreSQL once the current one runs out.
     */
    public synchronized long nextId() {
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
        Long id = jdbcTemplate.queryForObject("SELECT nextval('seq_short_urls')", Long.class);
        if (id == null) {
            throw new IllegalStateException("Failed to fetch next HiLo block from 'seq_short_urls'.");
        }
        return id;
    }
}
