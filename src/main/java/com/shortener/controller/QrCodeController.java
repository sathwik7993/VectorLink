package com.shortener.controller;

import com.shortener.service.QrCodeService;
import com.shortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qr")
public class QrCodeController {

    private final QrCodeService qrCodeService;

    public QrCodeController(QrCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    /**
     * Generates and returns a QR code image (PNG) for the given short code.
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<byte[]> getQrCode(@PathVariable String shortCode, HttpServletRequest request) {
        // Build the full short URL based on the current request
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String baseUrl;
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
            baseUrl = scheme + "://" + host;
        } else {
            baseUrl = scheme + "://" + host + ":" + port;
        }
        String shortUrl = baseUrl + "/" + shortCode;

        byte[] qrImage = qrCodeService.generateQrCode(shortUrl, 300, 300);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(qrImage.length);

        return new ResponseEntity<>(qrImage, headers, HttpStatus.OK);
    }
}
