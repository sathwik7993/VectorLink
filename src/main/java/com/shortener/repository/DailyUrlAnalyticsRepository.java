package com.shortener.repository;

import com.shortener.entity.DailyUrlAnalytics;
import com.shortener.entity.DailyUrlAnalyticsKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DailyUrlAnalyticsRepository extends JpaRepository<DailyUrlAnalytics, DailyUrlAnalyticsKey>, CustomDailyUrlAnalyticsRepository {
    List<DailyUrlAnalytics> findByShortCodeOrderByClickDateAsc(String shortCode);
}
