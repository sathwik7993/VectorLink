package com.shortener.repository;

import com.shortener.entity.DailyUrlAnalytics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.util.List;

public class CustomDailyUrlAnalyticsRepositoryImpl implements CustomDailyUrlAnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomDailyUrlAnalyticsRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void batchUpsert(List<DailyUrlAnalytics> analyticsList) {
        if (analyticsList == null || analyticsList.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO daily_url_analytics (short_code, click_date, click_count, " +
                     "device_desktop_count, device_mobile_count, device_tablet_count, " +
                     "browser_chrome_count, browser_safari_count, browser_firefox_count, browser_other_count, " +
                     "os_windows_count, os_macos_count, os_android_count, os_ios_count, os_other_count) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (short_code, click_date) DO UPDATE SET " +
                     "click_count = daily_url_analytics.click_count + EXCLUDED.click_count, " +
                     "device_desktop_count = daily_url_analytics.device_desktop_count + EXCLUDED.device_desktop_count, " +
                     "device_mobile_count = daily_url_analytics.device_mobile_count + EXCLUDED.device_mobile_count, " +
                     "device_tablet_count = daily_url_analytics.device_tablet_count + EXCLUDED.device_tablet_count, " +
                     "browser_chrome_count = daily_url_analytics.browser_chrome_count + EXCLUDED.browser_chrome_count, " +
                     "browser_safari_count = daily_url_analytics.browser_safari_count + EXCLUDED.browser_safari_count, " +
                     "browser_firefox_count = daily_url_analytics.browser_firefox_count + EXCLUDED.browser_firefox_count, " +
                     "browser_other_count = daily_url_analytics.browser_other_count + EXCLUDED.browser_other_count, " +
                     "os_windows_count = daily_url_analytics.os_windows_count + EXCLUDED.os_windows_count, " +
                     "os_macos_count = daily_url_analytics.os_macos_count + EXCLUDED.os_macos_count, " +
                     "os_android_count = daily_url_analytics.os_android_count + EXCLUDED.os_android_count, " +
                     "os_ios_count = daily_url_analytics.os_ios_count + EXCLUDED.os_ios_count, " +
                     "os_other_count = daily_url_analytics.os_other_count + EXCLUDED.os_other_count";

        jdbcTemplate.batchUpdate(sql, analyticsList, analyticsList.size(), (PreparedStatement ps, DailyUrlAnalytics analytics) -> {
            ps.setString(1, analytics.getShortCode());
            ps.setDate(2, Date.valueOf(analytics.getClickDate()));
            ps.setLong(3, analytics.getClickCount());
            ps.setInt(4, analytics.getDeviceDesktopCount());
            ps.setInt(5, analytics.getDeviceMobileCount());
            ps.setInt(6, analytics.getDeviceTabletCount());
            ps.setInt(7, analytics.getBrowserChromeCount());
            ps.setInt(8, analytics.getBrowserSafariCount());
            ps.setInt(9, analytics.getBrowserFirefoxCount());
            ps.setInt(10, analytics.getBrowserOtherCount());
            ps.setInt(11, analytics.getOsWindowsCount());
            ps.setInt(12, analytics.getOsMacosCount());
            ps.setInt(13, analytics.getOsAndroidCount());
            ps.setInt(14, analytics.getOsIosCount());
            ps.setInt(15, analytics.getOsOtherCount());
        });
    }
}
