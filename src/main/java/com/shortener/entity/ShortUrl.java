package com.shortener.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

    @Id
    private Long id; // The sequence ID, obfuscated before mapping to Base62

    @Column(name = "short_code", nullable = false, unique = true, length = 15)
    private String shortCode;

    @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
    private String longUrl;

    @Column(name = "custom_alias", unique = true, length = 30)
    private String customAlias;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "fallback_url", columnDefinition = "TEXT")
    private String fallbackUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public ShortUrl() {
        this.createdAt = Instant.now();
    }

    public ShortUrl(Long id, String shortCode, String longUrl, String customAlias,
                    boolean active, long clickCount, Instant createdAt, Instant expiresAt,
                    String passwordHash, String fallbackUrl) {
        this(id, shortCode, longUrl, customAlias, active, clickCount, createdAt, expiresAt, passwordHash, fallbackUrl, null);
    }

    public ShortUrl(Long id, String shortCode, String longUrl, String customAlias,
                    boolean active, long clickCount, Instant createdAt, Instant expiresAt,
                    String passwordHash, String fallbackUrl, User user) {
        this.id = id;
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.customAlias = customAlias;
        this.active = active;
        this.clickCount = clickCount;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt;
        this.passwordHash = passwordHash;
        this.fallbackUrl = fallbackUrl;
        this.user = user;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFallbackUrl() {
        return fallbackUrl;
    }

    public void setFallbackUrl(String fallbackUrl) {
        this.fallbackUrl = fallbackUrl;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String shortCode;
        private String longUrl;
        private String customAlias;
        private boolean active = true;
        private long clickCount = 0;
        private Instant createdAt;
        private Instant expiresAt;
        private String passwordHash;
        private String fallbackUrl;
        private User user;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder shortCode(String shortCode) {
            this.shortCode = shortCode;
            return this;
        }

        public Builder longUrl(String longUrl) {
            this.longUrl = longUrl;
            return this;
        }

        public Builder customAlias(String customAlias) {
            this.customAlias = customAlias;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder clickCount(long clickCount) {
            this.clickCount = clickCount;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder fallbackUrl(String fallbackUrl) {
            this.fallbackUrl = fallbackUrl;
            return this;
        }

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public ShortUrl build() {
            return new ShortUrl(id, shortCode, longUrl, customAlias, active, clickCount, createdAt, expiresAt, passwordHash, fallbackUrl, user);
        }
    }
}
