package com.shortener.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class ClickLogKey implements Serializable {
    private Long id;
    private Instant clickedAt;

    public ClickLogKey() {}

    public ClickLogKey(Long id, Instant clickedAt) {
        this.id = id;
        this.clickedAt = clickedAt;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClickLogKey that = (ClickLogKey) o;
        return Objects.equals(id, that.id) && Objects.equals(clickedAt, that.clickedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, clickedAt);
    }

    @Override
    public String toString() {
        return "ClickLogKey{" +
                "id=" + id +
                ", clickedAt=" + clickedAt +
                '}';
    }
}
