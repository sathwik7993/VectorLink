package com.shortener.repository;

import com.shortener.entity.ClickLog;
import com.shortener.entity.ClickLogKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface ClickLogRepository extends JpaRepository<ClickLog, ClickLogKey>, CustomClickLogRepository {

    @Transactional(readOnly = true)
    @Query("SELECT c.countryCode, COUNT(c) FROM ClickLog c WHERE c.shortCode = :shortCode OR (:customAlias IS NOT NULL AND c.shortCode = :customAlias) GROUP BY c.countryCode")
    List<Object[]> getCountryCounts(@Param("shortCode") String shortCode, @Param("customAlias") String customAlias);

    @Transactional(readOnly = true)
    @Query("SELECT COALESCE(c.referrer, 'Direct'), COUNT(c) FROM ClickLog c WHERE c.shortCode = :shortCode OR (:customAlias IS NOT NULL AND c.shortCode = :customAlias) GROUP BY COALESCE(c.referrer, 'Direct') ORDER BY COUNT(c) DESC")
    List<Object[]> getReferrerCounts(@Param("shortCode") String shortCode, @Param("customAlias") String customAlias);

    @Transactional(readOnly = true)
    @Query("SELECT CAST(EXTRACT(HOUR FROM c.clickedAt) AS int), COUNT(c) FROM ClickLog c WHERE c.shortCode = :shortCode OR (:customAlias IS NOT NULL AND c.shortCode = :customAlias) GROUP BY EXTRACT(HOUR FROM c.clickedAt) ORDER BY CAST(EXTRACT(HOUR FROM c.clickedAt) AS int) ASC")
    List<Object[]> getHourlyTraffic(@Param("shortCode") String shortCode, @Param("customAlias") String customAlias);

    @Transactional(readOnly = true)
    @Query("SELECT c.latitude, c.longitude, COALESCE(c.city, 'Unknown'), COALESCE(c.region, 'Unknown'), COALESCE(c.countryCode, 'Unknown'), COUNT(c), MAX(c.clickedAt) " +
           "FROM ClickLog c " +
           "WHERE (c.shortCode = :shortCode OR (:customAlias IS NOT NULL AND c.shortCode = :customAlias)) " +
           "AND c.latitude IS NOT NULL AND c.longitude IS NOT NULL " +
           "GROUP BY c.latitude, c.longitude, c.city, c.region, c.countryCode")
    List<Object[]> getGeographicLocations(@Param("shortCode") String shortCode, @Param("customAlias") String customAlias);
}
