-- Fallback sequence for ID generation when Redis is down
CREATE SEQUENCE IF NOT EXISTS seq_short_urls START WITH 1000000;

-- 1. Create short_urls table
CREATE TABLE IF NOT EXISTS short_urls (
    id BIGINT PRIMARY KEY,
    short_code VARCHAR(15) UNIQUE NOT NULL,
    long_url TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for expiration cleanup and query checking
CREATE INDEX IF NOT EXISTS idx_short_urls_expires_at ON short_urls (expires_at);

-- 2. Create click_logs table partitioned by range on clicked_at
CREATE TABLE IF NOT EXISTS click_logs (
    id BIGSERIAL,
    short_code VARCHAR(15) NOT NULL,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    referrer VARCHAR(255),
    user_agent VARCHAR(512),
    ip_address VARCHAR(45),
    country_code CHAR(2),
    device_type VARCHAR(50),
    event_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id, clicked_at)
) PARTITION BY RANGE (clicked_at);

-- Create a unique constraint on (event_id, clicked_at) for idempotency/deduplication
ALTER TABLE click_logs ADD CONSTRAINT uq_click_logs_event_id UNIQUE (event_id, clicked_at);

-- Create initial monthly partitions for 2026 and a default partition
CREATE TABLE IF NOT EXISTS click_logs_y2026m06 PARTITION OF click_logs
    FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS click_logs_y2026m07 PARTITION OF click_logs
    FOR VALUES FROM ('2026-07-01 00:00:00+00') TO ('2026-08-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS click_logs_y2026m08 PARTITION OF click_logs
    FOR VALUES FROM ('2026-08-01 00:00:00+00') TO ('2026-09-01 00:00:00+00');

CREATE TABLE IF NOT EXISTS click_logs_default PARTITION OF click_logs DEFAULT;

-- Index to optimize analytics aggregation query speeds
CREATE INDEX IF NOT EXISTS idx_click_logs_short_code_clicked_at ON click_logs (short_code, clicked_at);

-- 3. Create pre-aggregated daily_url_analytics table
CREATE TABLE IF NOT EXISTS daily_url_analytics (
    short_code VARCHAR(15) NOT NULL,
    click_date DATE NOT NULL,
    click_count BIGINT DEFAULT 0 NOT NULL,
    device_desktop_count INT DEFAULT 0 NOT NULL,
    device_mobile_count INT DEFAULT 0 NOT NULL,
    device_tablet_count INT DEFAULT 0 NOT NULL,
    PRIMARY KEY (short_code, click_date)
);

-- Index to optimize date-range queries for analytics dashboard
CREATE INDEX IF NOT EXISTS idx_daily_analytics_date ON daily_url_analytics (click_date);
