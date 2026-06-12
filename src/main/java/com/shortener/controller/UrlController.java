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
        // 1. Resolve redirect destination (Fast layer: L1/L2 caches, Fallback: Postgres with mutex)
        String longUrl = urlShortenerService.resolveUrl(shortCode);

        // 2. Extract request metadata for analytics ingestion
        String referrer = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = getClientIp(request);

        // 3. Asynchronously track the click via Kafka event stream
        urlShortenerService.trackClick(shortCode, referrer, userAgent, ipAddress);

        // 4. Return 302 Found redirect
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, longUrl)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
