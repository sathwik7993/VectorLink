package com.shortener.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_logs")
@IdClass(ClickLogKey.class)
public class ClickLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Id
    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "short_code", nullable = false, length = 15)
    private String shortCode;

    @Column(name = "referrer", length = 255)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "country_code", length = 2)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    private String countryCode;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId; // Deduplication hash

    @Column(name = "browser", length = 50)
    private String browser;

    @Column(name = "operating_system", length = 50)
    private String operatingSystem;

    public ClickLog() {}

    public ClickLog(Long id, Instant clickedAt, String shortCode, String referrer, String userAgent, String ipAddress, String countryCode, String deviceType, String eventId, String browser, String operatingSystem) {
        this.id = id;
        this.clickedAt = clickedAt;
        this.shortCode = shortCode;
        this.referrer = referrer;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.countryCode = countryCode;
        this.deviceType = deviceType;
        this.eventId = eventId;
        this.browser = browser;
        this.operatingSystem = operatingSystem;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(Instant clickedAt) {
        this.clickedAt = clickedAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Instant clickedAt;
        private String shortCode;
        private String referrer;
        private String userAgent;
        private String ipAddress;
        private String countryCode;
        private String deviceType;
        private String eventId;
        private String browser;
        private String operatingSystem;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder clickedAt(Instant clickedAt) {
            this.clickedAt = clickedAt;
            return this;
        }

        public Builder shortCode(String shortCode) {
            this.shortCode = shortCode;
            return this;
        }

        public Builder referrer(String referrer) {
            this.referrer = referrer;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder countryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public Builder deviceType(String deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder browser(String browser) {
            this.browser = browser;
            return this;
        }

        public Builder operatingSystem(String operatingSystem) {
            this.operatingSystem = operatingSystem;
            return this;
        }

        public ClickLog build() {
            return new ClickLog(id, clickedAt, shortCode, referrer, userAgent, ipAddress, countryCode, deviceType, eventId, browser, operatingSystem);
        }
    }
}
