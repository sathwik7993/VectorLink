package com.shortener.controller;

import com.shortener.service.DeadLetterReplayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only operations (secured by ROLE_ADMIN in SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AdminController {

    private final DeadLetterReplayService deadLetterReplayService;

    public AdminController(DeadLetterReplayService deadLetterReplayService) {
        this.deadLetterReplayService = deadLetterReplayService;
    }

    @GetMapping("/dlq/replay")
    public ResponseEntity<Map<String, Boolean>> replayStatus() {
        return ResponseEntity.ok(Map.of("running", deadLetterReplayService.isRunning()));
    }

    @PostMapping("/dlq/replay/start")
    public ResponseEntity<Map<String, Boolean>> startReplay() {
        return ResponseEntity.ok(Map.of("running", deadLetterReplayService.start()));
    }

    @PostMapping("/dlq/replay/stop")
    public ResponseEntity<Map<String, Boolean>> stopReplay() {
        return ResponseEntity.ok(Map.of("running", deadLetterReplayService.stop()));
    }
}
