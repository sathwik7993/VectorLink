package com.shortener.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_url_analytics")
@IdClass(DailyUrlAnalyticsKey.class)
public class DailyUrlAnalytics {

    @Id
    @Column(name = "short_code", nullable = false, length = 15)
    private String shortCode;

    @Id
    @Column(name = "click_date", nullable = false)
    private LocalDate clickDate;

    @Column(name = "click_count", nullable = false)
    private Long clickCount;

    @Column(name = "device_desktop_count", nullable = false)
    private Integer deviceDesktopCount;

    @Column(name = "device_mobile_count", nullable = false)
    private Integer deviceMobileCount;

    @Column(name = "device_tablet_count", nullable = false)
    private Integer deviceTabletCount;

    public DailyUrlAnalytics() {
        this.clickCount = 0L;
        this.deviceDesktopCount = 0;
        this.deviceMobileCount = 0;
        this.deviceTabletCount = 0;
    }

    public DailyUrlAnalytics(String shortCode, LocalDate clickDate, Long clickCount, Integer deviceDesktopCount, Integer deviceMobileCount, Integer deviceTabletCount) {
        this.shortCode = shortCode;
        this.clickDate = clickDate;
        this.clickCount = clickCount != null ? clickCount : 0L;
        this.deviceDesktopCount = deviceDesktopCount != null ? deviceDesktopCount : 0;
        this.deviceMobileCount = deviceMobileCount != null ? deviceMobileCount : 0;
        this.deviceTabletCount = deviceTabletCount != null ? deviceTabletCount : 0;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public LocalDate getClickDate() {
        return clickDate;
    }

    public void setClickDate(LocalDate clickDate) {
        this.clickDate = clickDate;
    }

    public Long getClickCount() {
        return clickCount;
    }

    public void setClickCount(Long clickCount) {
        this.clickCount = clickCount;
    }

    public Integer getDeviceDesktopCount() {
        return deviceDesktopCount;
    }

    public void setDeviceDesktopCount(Integer deviceDesktopCount) {
        this.deviceDesktopCount = deviceDesktopCount;
    }

    public Integer getDeviceMobileCount() {
        return deviceMobileCount;
    }

    public void setDeviceMobileCount(Integer deviceMobileCount) {
        this.deviceMobileCount = deviceMobileCount;
    }

    public Integer getDeviceTabletCount() {
        return deviceTabletCount;
    }

    public void setDeviceTabletCount(Integer deviceTabletCount) {
        this.deviceTabletCount = deviceTabletCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String shortCode;
        private LocalDate clickDate;
        private Long clickCount = 0L;
        private Integer deviceDesktopCount = 0;
        private Integer deviceMobileCount = 0;
        private Integer deviceTabletCount = 0;

        public Builder shortCode(String shortCode) {
            this.shortCode = shortCode;
            return this;
        }

        public Builder clickDate(LocalDate clickDate) {
            this.clickDate = clickDate;
            return this;
        }

        public Builder clickCount(Long clickCount) {
            this.clickCount = clickCount;
            return this;
        }

        public Builder deviceDesktopCount(Integer deviceDesktopCount) {
            this.deviceDesktopCount = deviceDesktopCount;
            return this;
        }

        public Builder deviceMobileCount(Integer deviceMobileCount) {
            this.deviceMobileCount = deviceMobileCount;
            return this;
        }

        public Builder deviceTabletCount(Integer deviceTabletCount) {
            this.deviceTabletCount = deviceTabletCount;
            return this;
        }

        public DailyUrlAnalytics build() {
            return new DailyUrlAnalytics(shortCode, clickDate, clickCount, deviceDesktopCount, deviceMobileCount, deviceTabletCount);
        }
    }
}
