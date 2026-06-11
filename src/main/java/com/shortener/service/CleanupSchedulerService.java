package com.shortener.service;

import com.shortener.repository.ShortUrlRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.logging.Logger;

@Service
public class CleanupSchedulerService {

    private static final Logger LOGGER = Logger.getLogger(CleanupSchedulerService.class.getName());
    
    private final ShortUrlRepository shortUrlRepository;

    public CleanupSchedulerService(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    /**
     * Purges expired URLs from PostgreSQL. Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredUrls() {
        LOGGER.info("Starting background task: Cleaning up expired URLs.");
        try {
            int deletedCount = shortUrlRepository.deleteExpiredUrls(Instant.now());
            LOGGER.info("Expired URL cleanup complete. Removed " + deletedCount + " URLs.");
        } catch (Exception e) {
            LOGGER.severe("Failed to run expired URL cleanup task: " + e.getMessage());
        }
    }
}
