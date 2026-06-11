package com.shortener.repository;

import com.shortener.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {
    
    Optional<ShortUrl> findByShortCode(String shortCode);
    
    @Modifying
    @Query("DELETE FROM ShortUrl s WHERE s.expiresAt < :now")
    int deleteExpiredUrls(@Param("now") Instant now);
}
