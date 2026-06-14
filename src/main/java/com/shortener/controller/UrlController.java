package com.shortener.controller;

import com.shortener.dto.UrlRequest;
import com.shortener.dto.UrlResponse;
import com.shortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UrlController {

    private final UrlShortenerService urlShortenerService;

    public UrlController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    /**
     * Endpoint to create a shortened URL mapping.
     */
    @PostMapping("/api/v1/urls")
    public ResponseEntity<UrlResponse> shortenUrl(@RequestBody UrlRequest request) {
        if (request.longUrl() == null || request.longUrl().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UrlResponse response = urlShortenerService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint to list all shortened URLs ordered by creation date (newest first).
     */
    @GetMapping("/api/v1/urls")
    public ResponseEntity<List<UrlResponse>> listAllUrls() {
        List<UrlResponse> urls = urlShortenerService.listAllUrls();
        return ResponseEntity.ok(urls);
    }

    /**
     * Endpoint to delete a shortened URL by its short code.
     */
    @DeleteMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlShortenerService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint to toggle active/inactive state of a shortened URL.
     */
    @PatchMapping("/api/v1/urls/{shortCode}/toggle")
    public ResponseEntity<UrlResponse> toggleUrlActive(@PathVariable String shortCode) {
        UrlResponse response = urlShortenerService.toggleUrlActive(shortCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to redirect from a short code to the target long URL.
     */
    @GetMapping("/{shortCode:[a-zA-Z0-9\\-]{3,30}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String token = request.getParameter("token");
        String cookieValue = null;
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("vlink_pass_" + shortCode)) {
                    cookieValue = cookie.getValue();
                    break;
                }
            }
        }

        String referrer = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = getClientIp(request);

        try {
            // 1. Resolve redirect destination (Fast layer: L1/L2 caches, Fallback: Postgres with mutex)
            String longUrl = urlShortenerService.resolveUrl(shortCode, token, cookieValue, userAgent);

            // 2. Query parameter forwarding
            longUrl = appendQueryParams(longUrl, request.getQueryString());

            // 3. Asynchronously track the click via Kafka event stream
            urlShortenerService.trackClick(shortCode, referrer, userAgent, ipAddress);

            // 5. Return 302 Found redirect
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, longUrl)
                    .build();

        } catch (com.shortener.exception.PasswordRequiredException e) {
            // Redirect unauthorized traffic to /password.html?code={shortCode}
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/password.html?code=" + shortCode)
                    .build();
        }
    }

    /**
     * Endpoint to verify short URL password.
     */
    @PostMapping("/api/v1/urls/verify-password")
    public ResponseEntity<PasswordVerifyResponse> verifyPassword(
            @RequestBody PasswordVerifyRequest verifyRequest,
            jakarta.servlet.http.HttpServletResponse response) {
        
        boolean success = urlShortenerService.verifyPassword(verifyRequest.shortCode(), verifyRequest.password());
        if (success) {
            String hash = urlShortenerService.getPasswordHash(verifyRequest.shortCode());
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("vlink_pass_" + verifyRequest.shortCode(), hash);
            cookie.setPath("/");
            cookie.setMaxAge(3600);
            response.addCookie(cookie);
            
            return ResponseEntity.ok(new PasswordVerifyResponse(true, hash));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new PasswordVerifyResponse(false, null));
        }
    }

    private String appendQueryParams(String targetUrl, String queryString) {
        if (queryString == null || queryString.trim().isEmpty()) {
            return targetUrl;
        }
        if (targetUrl.contains("?")) {
            return targetUrl + "&" + queryString;
        } else {
            return targetUrl + "?" + queryString;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static record PasswordVerifyRequest(String shortCode, String password) {}
    public static record PasswordVerifyResponse(boolean success, String token) {}
}
