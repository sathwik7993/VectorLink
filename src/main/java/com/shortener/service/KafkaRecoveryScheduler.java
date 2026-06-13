package com.shortener.service;

import com.shortener.config.KafkaConfig;
import com.shortener.dto.ClickEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class KafkaRecoveryScheduler {

    private static final Logger LOGGER = Logger.getLogger(KafkaRecoveryScheduler.class.getName());
    private static final String LOG_FILE_PATH = "C:\\Users\\SATHWIK.CH\\Downloads\\projects\\URL shortner\\logs\\dead_letter_clicks.log";
    private static final Object fileLock = new Object();

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaRecoveryScheduler(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Scheduled(fixedRate = 10000)
    public void recoverKafkaEvents() {
        if (!isKafkaReachable()) {
            UrlShortenerService.setKafkaDown(true);
            return;
        }

        if (UrlShortenerService.isKafkaDown()) {
            LOGGER.info("Kafka is reachable again. Starting recovery process.");
            UrlShortenerService.setKafkaDown(false);
        }

        drainInMemoryQueue();
        replayLogFile();
    }

    private boolean isKafkaReachable() {
        Map<String, Object> config = kafkaTemplate.getProducerFactory().getConfigurationProperties();
        try (AdminClient adminClient = AdminClient.create(config)) {
            adminClient.listTopics(new ListTopicsOptions().timeoutMs(2000)).names().get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void drainInMemoryQueue() {
        ConcurrentLinkedQueue<ClickEvent> buffer = UrlShortenerService.getKafkaBuffer();
        if (buffer.isEmpty()) {
            return;
        }

        LOGGER.info("Draining in-memory ClickEvent buffer of size: " + buffer.size());
        ClickEvent event;
        while ((event = buffer.poll()) != null) {
            UrlShortenerService.decrementBufferSize();
            try {
                kafkaTemplate.send(KafkaConfig.CLICK_TOPIC, event.shortCode(), event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Kafka went down during buffer draining. Re-buffering remaining events.");
                UrlShortenerService.setKafkaDown(true);
                UrlShortenerService.bufferClickEventDirectly(event);
                break;
            }
        }
    }

    private void replayLogFile() {
        File file = new File(LOG_FILE_PATH);
        if (!file.exists() || file.length() == 0) {
            return;
        }

        LOGGER.info("Replaying click events from local dead-letter log: " + LOG_FILE_PATH);
        List<String> failedLines = new ArrayList<>();

        synchronized (fileLock) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        ClickEvent event = objectMapper.readValue(line, ClickEvent.class);
                        if (UrlShortenerService.isKafkaDown()) {
                            failedLines.add(line);
                        } else {
                            try {
                                kafkaTemplate.send(KafkaConfig.CLICK_TOPIC, event.shortCode(), event).get(); // blocking send
                            } catch (Exception ex) {
                                LOGGER.log(Level.WARNING, "Failed to send replayed event to Kafka: " + ex.getMessage());
                                UrlShortenerService.setKafkaDown(true);
                                failedLines.add(line);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Failed to parse log line as ClickEvent: " + line, e);
                    }
                }

                if (failedLines.isEmpty()) {
                    Files.deleteIfExists(file.toPath());
                } else {
                    Files.write(file.toPath(), failedLines);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error reading/writing dead-letter log file: " + e.getMessage(), e);
            }
        }
    }
}
