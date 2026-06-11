package com.shortener.repository;

import com.shortener.entity.DailyUrlAnalytics;
import java.util.List;

public interface CustomDailyUrlAnalyticsRepository {
    void batchUpsert(List<DailyUrlAnalytics> analyticsList);
}
