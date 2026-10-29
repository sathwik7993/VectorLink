package com.shortener.repository;

import com.shortener.entity.ClickLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

public class CustomClickLogRepositoryImpl implements CustomClickLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomClickLogRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public int[] batchInsert(List<ClickLog> clickLogs) {
        if (clickLogs == null || clickLogs.isEmpty()) {
            return new int[0];
        }

        String sql = "INSERT INTO click_logs (short_code, clicked_at, referrer, user_agent, ip_address, country_code, device_type, event_id, browser, operating_system, latitude, longitude, city, region) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (event_id, clicked_at) DO NOTHING";
        
        int[][] counts = jdbcTemplate.batchUpdate(sql, clickLogs, clickLogs.size(), (PreparedStatement ps, ClickLog click) -> {
            ps.setString(1, click.getShortCode());
            ps.setTimestamp(2, Timestamp.from(click.getClickedAt()));
            ps.setString(3, click.getReferrer());
            ps.setString(4, click.getUserAgent());
            ps.setString(5, click.getIpAddress());
            ps.setString(6, click.getCountryCode());
            ps.setString(7, click.getDeviceType());
            ps.setString(8, click.getEventId());
            ps.setString(9, click.getBrowser());
            ps.setString(10, click.getOperatingSystem());
            if (click.getLatitude() != null) {
                ps.setDouble(11, click.getLatitude());
            } else {
                ps.setNull(11, java.sql.Types.DOUBLE);
            }
            if (click.getLongitude() != null) {
                ps.setDouble(12, click.getLongitude());
            } else {
                ps.setNull(12, java.sql.Types.DOUBLE);
            }
            ps.setString(13, click.getCity());
            ps.setString(14, click.getRegion());
        });
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).toArray();
    }
}
