package com.shortener.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class DailyUrlAnalyticsKey implements Serializable {
    private String shortCode;
    private LocalDate clickDate;

    public DailyUrlAnalyticsKey() {}

    public DailyUrlAnalyticsKey(String shortCode, LocalDate clickDate) {
        this.shortCode = shortCode;
        this.clickDate = clickDate;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyUrlAnalyticsKey that = (DailyUrlAnalyticsKey) o;
        return Objects.equals(shortCode, that.shortCode) && Objects.equals(clickDate, that.clickDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, clickDate);
    }

    @Override
    public String toString() {
        return "DailyUrlAnalyticsKey{" +
                "shortCode='" + shortCode + '\'' +
                ", clickDate=" + clickDate +
                '}';
    }
}
