package com.shortener.repository;

import com.shortener.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    Optional<ShortUrl> findByCustomAlias(String alias);

    List<ShortUrl> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + 1 WHERE s.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode);

    @Modifying
    @Query("DELETE FROM ShortUrl s WHERE s.expiresAt < :now")
    int deleteExpiredUrls(@Param("now") Instant now);
}
