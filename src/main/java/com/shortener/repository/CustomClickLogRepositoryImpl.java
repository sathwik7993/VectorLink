package com.shortener.repository;

import com.shortener.entity.ClickLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

public class CustomClickLogRepositoryImpl implements CustomClickLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomClickLogRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void batchInsert(List<ClickLog> clickLogs) {
        if (clickLogs == null || clickLogs.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO click_logs (short_code, clicked_at, referrer, user_agent, ip_address, country_code, device_type, event_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (event_id, clicked_at) DO NOTHING";
        
        jdbcTemplate.batchUpdate(sql, clickLogs, clickLogs.size(), (PreparedStatement ps, ClickLog click) -> {
            ps.setString(1, click.getShortCode());
            ps.setTimestamp(2, Timestamp.from(click.getClickedAt()));
            ps.setString(3, click.getReferrer());
            ps.setString(4, click.getUserAgent());
            ps.setString(5, click.getIpAddress());
            ps.setString(6, click.getCountryCode());
            ps.setString(7, click.getDeviceType());
            ps.setString(8, click.getEventId());
        });
    }
}
