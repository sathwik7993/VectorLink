package com.shortener.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LinkAccessTokenServiceTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789";
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    private LinkAccessTokenService at(Instant instant) {
        return new LinkAccessTokenService(SECRET, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void acceptsAFreshTokenForTheSameLinkAndHash() {
        String token = at(NOW).issue("abc123", "$2a$10$hash");
        assertThat(at(NOW.plusSeconds(60)).verify("abc123", "$2a$10$hash", token)).isTrue();
    }

    @Test
    void rejectsExpiredTokens() {
        String token = at(NOW).issue("abc123", "$2a$10$hash");
        assertThat(at(NOW.plus(LinkAccessTokenService.TTL)).verify("abc123", "$2a$10$hash", token)).isFalse();
    }

    @Test
    void isBoundToTheLinkAndItsCurrentPassword() {
        String token = at(NOW).issue("abc123", "$2a$10$hash");
        assertThat(at(NOW).verify("other1", "$2a$10$hash", token)).isFalse();
        assertThat(at(NOW).verify("abc123", "$2a$10$changed", token)).isFalse();
    }

    @Test
    void rejectsTamperedMalformedAndRawHashTokens() {
        String token = at(NOW).issue("abc123", "$2a$10$hash");
        String extendedExpiry = (Long.parseLong(token.substring(0, token.indexOf('.'))) + 99999) + token.substring(token.indexOf('.'));
        assertThat(at(NOW).verify("abc123", "$2a$10$hash", extendedExpiry)).isFalse();
        assertThat(at(NOW).verify("abc123", "$2a$10$hash", "garbage")).isFalse();
        assertThat(at(NOW).verify("abc123", "$2a$10$hash", null)).isFalse();
        // The stored hash itself must never work as an access credential
        assertThat(at(NOW).verify("abc123", "$2a$10$hash", "$2a$10$hash")).isFalse();
    }

    @Test
    void differentSecretsProduceIncompatibleTokens() {
        String token = at(NOW).issue("abc123", "$2a$10$hash");
        LinkAccessTokenService other = new LinkAccessTokenService("another-secret-0123456789abcdef0123", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(other.verify("abc123", "$2a$10$hash", token)).isFalse();
    }
}
