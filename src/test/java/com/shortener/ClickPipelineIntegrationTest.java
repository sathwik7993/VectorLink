package com.shortener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortener.config.KafkaConfig;
import com.shortener.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the click analytics pipeline against real Postgres, Redis and Kafka:
 * create URL -> redirect -> Kafka -> batch consumer -> Postgres, plus poison-pill routing to the
 * dead-letter topic and DLT replay. Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ClickPipelineIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("url_shortener")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("infrastructure/init-db/01-schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void redirectClickFlowsThroughKafkaIntoPostgres() throws Exception {
        String shortCode = createShortUrl("https://example.com/integration");

        mockMvc.perform(get("/" + shortCode).with(remote("10.0.0.7")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/integration"));

        awaitClickCount(shortCode, 1);
        Long daily = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(click_count), 0) FROM daily_url_analytics WHERE short_code = ?", Long.class, shortCode);
        assertThat(daily).isEqualTo(1L);
    }

    @Test
    void poisonPillAndUnpersistableEventGoToDeadLetterTopic() throws Exception {
        String marker = UUID.randomUUID().toString();
        String poison = "not-json-" + marker;
        // short_code is VARCHAR(15): valid JSON, but the insert always fails
        String unpersistable = clickEventJson("way-too-long-short-code-" + marker.substring(0, 8), "evt-" + marker);
        String good = clickEventJson("ok" + marker.substring(0, 6), "evt-good-" + marker);

        try (KafkaProducer<String, byte[]> producer = rawProducer()) {
            producer.send(new ProducerRecord<>(KafkaConfig.CLICK_TOPIC, "k", poison.getBytes(StandardCharsets.UTF_8))).get();
            producer.send(new ProducerRecord<>(KafkaConfig.CLICK_TOPIC, "k", unpersistable.getBytes(StandardCharsets.UTF_8))).get();
            producer.send(new ProducerRecord<>(KafkaConfig.CLICK_TOPIC, "k", good.getBytes(StandardCharsets.UTF_8))).get();
        }

        List<String> deadLettered = readDeadLetterValues(marker, 2);
        assertThat(deadLettered).anyMatch(v -> v.equals(poison));
        assertThat(deadLettered).anyMatch(v -> v.contains("evt-" + marker));

        // The good record behind the failures still gets persisted
        awaitClickCount("ok" + marker.substring(0, 6), 1);
    }

    @Test
    void adminCanReplayDeadLetteredEvents() throws Exception {
        String shortCode = "rp" + UUID.randomUUID().toString().substring(0, 6);
        try (KafkaProducer<String, byte[]> producer = rawProducer()) {
            producer.send(new ProducerRecord<>(KafkaConfig.CLICK_DLT, 0, shortCode,
                    clickEventJson(shortCode, "evt-replay-" + shortCode).getBytes(StandardCharsets.UTF_8))).get();
        }

        Cookie admin = new Cookie("vlink_jwt", jwtService.generateToken("admin@example.com", "ADMIN"));
        Cookie user = new Cookie("vlink_jwt", jwtService.generateToken("user@example.com", "USER"));

        mockMvc.perform(post("/api/v1/admin/dlq/replay/start").cookie(user).with(remote("10.0.0.8")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/dlq/replay/start").cookie(admin).with(remote("10.0.0.8")))
                .andExpect(status().isOk());
        try {
            awaitClickCount(shortCode, 1);
        } finally {
            mockMvc.perform(post("/api/v1/admin/dlq/replay/stop").cookie(admin).with(remote("10.0.0.8")))
                    .andExpect(status().isOk());
        }
    }

    // ---- authorization and link-password regressions (security audit run-1) ----

    @Test
    void nonOwnerCannotToggleOrDeleteAnotherUsersLink() throws Exception {
        Cookie alice = registeredUser("alice-own@example.test");
        Cookie bob = registeredUser("bob-own@example.test");
        String code = createShortUrlAs(alice, Map.of("longUrl", "https://alice.example.test/private", "ttlSeconds", 3600));

        mockMvc.perform(patch("/api/v1/urls/" + code + "/toggle").cookie(bob).with(remote(nextIp())))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/urls/" + code).cookie(bob).with(remote(nextIp())))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/urls/" + code + "/toggle").cookie(alice).with(remote(nextIp())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/urls/" + code).cookie(alice).with(remote(nextIp())))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonOwnerCannotReadAnalyticsOrSubscribeToStream() throws Exception {
        Cookie alice = registeredUser("alice-analytics@example.test");
        Cookie bob = registeredUser("bob-analytics@example.test");
        String code = createShortUrlAs(alice, Map.of("longUrl", "https://alice.example.test/stats", "ttlSeconds", 3600));

        for (String suffix : List.of("", "/countries", "/referrers", "/hourly", "/locations", "/stream")) {
            mockMvc.perform(get("/api/v1/analytics/" + code + suffix).cookie(bob).with(remote(nextIp())))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/v1/analytics/" + code + "/referrers").cookie(alice).with(remote(nextIp())))
                .andExpect(status().isOk());
    }

    @Test
    void linkPasswordGrantIsSignedHttpOnlyAndNeverForwarded() throws Exception {
        Cookie alice = registeredUser("alice-pw@example.test");
        String code = createShortUrlAs(alice, Map.of("longUrl", "https://dest.example.test/doc?x=1",
                "password", "link-pw-123", "ttlSeconds", 3600));

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM short_urls WHERE short_code = ?", String.class, code);
        assertThat(storedHash).startsWith("$2"); // BCrypt, not a fast hash

        // The stored hash is not an access credential
        mockMvc.perform(get("/" + code).param("token", storedHash).with(remote(nextIp())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/password.html?code=" + code));

        mockMvc.perform(post("/api/v1/urls/verify-password").with(remote(nextIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shortCode", code, "password", "wrong"))))
                .andExpect(status().isUnauthorized());

        var verified = mockMvc.perform(post("/api/v1/urls/verify-password").with(remote(nextIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shortCode", code, "password", "link-pw-123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(verified.getContentAsString()).doesNotContain(storedHash).doesNotContain("token");
        Cookie grant = verified.getCookie("vlink_pass_" + code);
        assertThat(grant).isNotNull();
        assertThat(grant.isHttpOnly()).isTrue();
        assertThat(grant.getValue()).isNotEqualTo(storedHash);

        mockMvc.perform(get("/" + code).cookie(grant).with(remote(nextIp())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://dest.example.test/doc?x=1"));

        // A grant passed as ?token= works but is stripped before the redirect to the destination
        mockMvc.perform(get("/" + code + "?token=" + grant.getValue() + "&utm_source=share").with(remote(nextIp())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://dest.example.test/doc?x=1&utm_source=share"));
    }

    @Test
    void actuatorIsNotServedOnThePublicPort() throws Exception {
        for (String path : List.of("/actuator/prometheus", "/actuator/metrics", "/actuator/health")) {
            int status = mockMvc.perform(get(path).with(remote(nextIp()))).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isNotEqualTo(200);
        }
    }

    // ---- helpers ----

    private static final java.util.concurrent.atomic.AtomicInteger IP_COUNTER = new java.util.concurrent.atomic.AtomicInteger(10);

    /** Fresh private client IP per request so tests never share a rate-limit bucket. */
    private static String nextIp() {
        int n = IP_COUNTER.incrementAndGet();
        return "10.1." + (n / 250) + "." + (n % 250 + 1);
    }

    private Cookie registeredUser(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(remote(nextIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "dummy-pass-1"))))
                .andExpect(status().isCreated());
        return new Cookie("vlink_jwt", jwtService.generateToken(email, "USER"));
    }

    private String createShortUrlAs(Cookie user, Map<String, Object> request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/urls").cookie(user).with(remote(nextIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("shortUrl").asText();
    }

    private String createShortUrl(String longUrl) throws Exception {
        Cookie jwt = new Cookie("vlink_jwt", jwtService.generateToken("it@example.com", "USER"));
        String body = mockMvc.perform(post("/api/v1/urls")
                        .cookie(jwt)
                        .with(remote("10.0.0.6"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("longUrl", longUrl, "ttlSeconds", 3600))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("shortUrl").asText(); // UrlResponse.shortUrl holds the short code
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String clickEventJson(String shortCode, String eventId) throws Exception {
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("shortCode", shortCode);
        event.put("clickedAt", Instant.now().toString());
        event.put("userAgent", "Mozilla/5.0 Chrome");
        event.put("ipAddress", "10.0.0.9");
        event.put("countryCode", "AU");
        event.put("deviceType", "desktop");
        event.put("eventId", eventId);
        event.put("browser", "Chrome");
        event.put("operatingSystem", "Windows");
        return objectMapper.writeValueAsString(event);
    }

    private void awaitClickCount(String shortCode, long expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(TIMEOUT);
        long count = 0;
        while (Instant.now().isBefore(deadline)) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM click_logs WHERE short_code = ?", Long.class, shortCode);
            if (count >= expected) {
                return;
            }
            Thread.sleep(250);
        }
        assertThat(count).as("click_logs rows for " + shortCode).isGreaterThanOrEqualTo(expected);
    }

    private KafkaProducer<String, byte[]> rawProducer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
    }

    private List<String> readDeadLetterValues(String marker, int expected) {
        List<String> matches = new ArrayList<>();
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + marker,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class))) {
            consumer.subscribe(List.of(KafkaConfig.CLICK_DLT));
            Instant deadline = Instant.now().plus(TIMEOUT);
            while (matches.size() < expected && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    String value = new String(record.value(), StandardCharsets.UTF_8);
                    if (value.contains(marker)) {
                        matches.add(value);
                    }
                }
            }
        }
        assertThat(matches).as("dead-lettered records containing " + marker).hasSize(expected);
        return matches;
    }
}
