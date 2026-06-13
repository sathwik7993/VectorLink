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

    @Column(name = "browser_chrome_count", nullable = false)
    private Integer browserChromeCount;

    @Column(name = "browser_safari_count", nullable = false)
    private Integer browserSafariCount;

    @Column(name = "browser_firefox_count", nullable = false)
    private Integer browserFirefoxCount;

    @Column(name = "browser_other_count", nullable = false)
    private Integer browserOtherCount;

    @Column(name = "os_windows_count", nullable = false)
    private Integer osWindowsCount;

    @Column(name = "os_macos_count", nullable = false)
    private Integer osMacosCount;

    @Column(name = "os_android_count", nullable = false)
    private Integer osAndroidCount;

    @Column(name = "os_ios_count", nullable = false)
    private Integer osIosCount;

    @Column(name = "os_other_count", nullable = false)
    private Integer osOtherCount;

    public DailyUrlAnalytics() {
        this.clickCount = 0L;
        this.deviceDesktopCount = 0;
        this.deviceMobileCount = 0;
        this.deviceTabletCount = 0;
        this.browserChromeCount = 0;
        this.browserSafariCount = 0;
        this.browserFirefoxCount = 0;
        this.browserOtherCount = 0;
        this.osWindowsCount = 0;
        this.osMacosCount = 0;
        this.osAndroidCount = 0;
        this.osIosCount = 0;
        this.osOtherCount = 0;
    }

    public DailyUrlAnalytics(String shortCode, LocalDate clickDate, Long clickCount, Integer deviceDesktopCount, Integer deviceMobileCount, Integer deviceTabletCount,
                             Integer browserChromeCount, Integer browserSafariCount, Integer browserFirefoxCount, Integer browserOtherCount,
                             Integer osWindowsCount, Integer osMacosCount, Integer osAndroidCount, Integer osIosCount, Integer osOtherCount) {
        this.shortCode = shortCode;
        this.clickDate = clickDate;
        this.clickCount = clickCount != null ? clickCount : 0L;
        this.deviceDesktopCount = deviceDesktopCount != null ? deviceDesktopCount : 0;
        this.deviceMobileCount = deviceMobileCount != null ? deviceMobileCount : 0;
        this.deviceTabletCount = deviceTabletCount != null ? deviceTabletCount : 0;
        this.browserChromeCount = browserChromeCount != null ? browserChromeCount : 0;
        this.browserSafariCount = browserSafariCount != null ? browserSafariCount : 0;
        this.browserFirefoxCount = browserFirefoxCount != null ? browserFirefoxCount : 0;
        this.browserOtherCount = browserOtherCount != null ? browserOtherCount : 0;
        this.osWindowsCount = osWindowsCount != null ? osWindowsCount : 0;
        this.osMacosCount = osMacosCount != null ? osMacosCount : 0;
        this.osAndroidCount = osAndroidCount != null ? osAndroidCount : 0;
        this.osIosCount = osIosCount != null ? osIosCount : 0;
        this.osOtherCount = osOtherCount != null ? osOtherCount : 0;
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

    public Integer getBrowserChromeCount() {
        return browserChromeCount;
    }

    public void setBrowserChromeCount(Integer browserChromeCount) {
        this.browserChromeCount = browserChromeCount;
    }

    public Integer getBrowserSafariCount() {
        return browserSafariCount;
    }

    public void setBrowserSafariCount(Integer browserSafariCount) {
        this.browserSafariCount = browserSafariCount;
    }

    public Integer getBrowserFirefoxCount() {
        return browserFirefoxCount;
    }

    public void setBrowserFirefoxCount(Integer browserFirefoxCount) {
        this.browserFirefoxCount = browserFirefoxCount;
    }

    public Integer getBrowserOtherCount() {
        return browserOtherCount;
    }

    public void setBrowserOtherCount(Integer browserOtherCount) {
        this.browserOtherCount = browserOtherCount;
    }

    public Integer getOsWindowsCount() {
        return osWindowsCount;
    }

    public void setOsWindowsCount(Integer osWindowsCount) {
        this.osWindowsCount = osWindowsCount;
    }

    public Integer getOsMacosCount() {
        return osMacosCount;
    }

    public void setOsMacosCount(Integer osMacosCount) {
        this.osMacosCount = osMacosCount;
    }

    public Integer getOsAndroidCount() {
        return osAndroidCount;
    }

    public void setOsAndroidCount(Integer osAndroidCount) {
        this.osAndroidCount = osAndroidCount;
    }

    public Integer getOsIosCount() {
        return osIosCount;
    }

    public void setOsIosCount(Integer osIosCount) {
        this.osIosCount = osIosCount;
    }

    public Integer getOsOtherCount() {
        return osOtherCount;
    }

    public void setOsOtherCount(Integer osOtherCount) {
        this.osOtherCount = osOtherCount;
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
        private Integer browserChromeCount = 0;
        private Integer browserSafariCount = 0;
        private Integer browserFirefoxCount = 0;
        private Integer browserOtherCount = 0;
        private Integer osWindowsCount = 0;
        private Integer osMacosCount = 0;
        private Integer osAndroidCount = 0;
        private Integer osIosCount = 0;
        private Integer osOtherCount = 0;

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

        public Builder browserChromeCount(Integer browserChromeCount) {
            this.browserChromeCount = browserChromeCount;
            return this;
        }

        public Builder browserSafariCount(Integer browserSafariCount) {
            this.browserSafariCount = browserSafariCount;
            return this;
        }

        public Builder browserFirefoxCount(Integer browserFirefoxCount) {
            this.browserFirefoxCount = browserFirefoxCount;
            return this;
        }

        public Builder browserOtherCount(Integer browserOtherCount) {
            this.browserOtherCount = browserOtherCount;
            return this;
        }

        public Builder osWindowsCount(Integer osWindowsCount) {
            this.osWindowsCount = osWindowsCount;
            return this;
        }

        public Builder osMacosCount(Integer osMacosCount) {
            this.osMacosCount = osMacosCount;
            return this;
        }

        public Builder osAndroidCount(Integer osAndroidCount) {
            this.osAndroidCount = osAndroidCount;
            return this;
        }

        public Builder osIosCount(Integer osIosCount) {
            this.osIosCount = osIosCount;
            return this;
        }

        public Builder osOtherCount(Integer osOtherCount) {
            this.osOtherCount = osOtherCount;
            return this;
        }

        public DailyUrlAnalytics build() {
            return new DailyUrlAnalytics(shortCode, clickDate, clickCount, deviceDesktopCount, deviceMobileCount, deviceTabletCount,
                    browserChromeCount, browserSafariCount, browserFirefoxCount, browserOtherCount,
                    osWindowsCount, osMacosCount, osAndroidCount, osIosCount, osOtherCount);
        }
    }
}
