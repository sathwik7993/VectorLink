package com.shortener.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Issues and verifies short-lived access grants for password-protected links.
 *
 * A grant is "<expiryEpochSeconds>.<base64url HMAC-SHA256(shortCode | expiry | passwordHash)>".
 * It is bound to one link, expires on its own, and becomes invalid as soon as the link's stored
 * password hash changes. The stored hash itself is never sent to clients.
 */
@Service
public class LinkAccessTokenService {

    public static final Duration TTL = Duration.ofHours(1);

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public LinkAccessTokenService(@Value("${app.jwt.secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    LinkAccessTokenService(String secret, Clock clock) {
        // Domain-separated key so a link grant can never be confused with a session JWT signature
        this.key = sha256(("vectorlink-link-access:" + secret).getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String issue(String shortCode, String passwordHash) {
        long expiry = clock.instant().plus(TTL).getEpochSecond();
        return expiry + "." + sign(shortCode, expiry, passwordHash);
    }

    public boolean verify(String shortCode, String passwordHash, String token) {
        if (token == null || passwordHash == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long expiry;
        try {
            expiry = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (clock.instant().getEpochSecond() >= expiry) {
            return false;
        }
        byte[] expected = sign(shortCode, expiry, passwordHash).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sign(String shortCode, long expiry, String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] tag = mac.doFinal((shortCode + "|" + expiry + "|" + passwordHash).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tag);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign link access token", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
