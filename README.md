# High-Performance URL Shortener & Event-Driven Analytics Pipeline

This repository contains a production-ready, highly optimized URL Shortener system designed to handle **50K URL creations/day**, achieve **<5ms P99 redirect latency**, maintain **<1s analytics ingestion lag**, and maintain an **80%+ cache hit rate**.

## 🚀 Key Features

* **URL Enumeration Security**: Employs a reversible 62-bit **Feistel Block Cipher** on sequence IDs before encoding, ensuring short codes look completely random (e.g. `1 -> 4d3X2`) but remain collision-free.
* **Dual-Layer Caching (L1 + L2)**: 
  * **L1 (Local Caffeine Cache)**: 15-second TTL, shielding the system from thundering herds on viral links.
  * **L2 (Distributed Redis Cache)**: Cached with the URL's lifetime (Write-Through on creation, Cache-Aside fallback).
* **Stampede Locking**: Thread-synchronization on cache misses blocks concurrent database queries for the same short code.
* **Async Ingestion Pipeline**: Decouples the redirect path from logging. Click metadata is pushed to **Kafka**, consumed in batches, and written to a partitioned **PostgreSQL** table in bulk.
* **Idempotent Analytics**: Embeds a SHA-256 fingerprint in events to guarantee exact-once ingestion semantics via PostgreSQL `ON CONFLICT DO NOTHING` statements.
* **Fallback ID Generator**: Redis `INCR` acts as primary sequence. If Redis goes down, the system transparently falls back to a PostgreSQL sequence (`seq_short_urls`), avoiding downtime.

---

## 🛠️ Tech Stack
* **Java 17** & **Spring Boot 3.2.4** (JPA, Redis, Kafka, Cache, JDBC)
* **PostgreSQL 15** (Declarative Range Partitioning)
* **Redis 7** (Distributed cache & atomic counter)
* **Apache Kafka** (KRaft single-broker mode)
* **Docker Compose**

---

## 📂 Project Structure

```
├── docker-compose.yml              # PostgreSQL, Redis, Kafka containers
├── README.md                       # This document
├── infrastructure/
│   └── init-db/
│       └── 01-schema.sql           # Partitioned tables, indexes, fallback sequence
└── src/
    └── main/
        ├── java/com/shortener/
        │   ├── UrlShortenerApplication.java  # Main App (Caching & Scheduling enabled)
        │   ├── config/             # Redis, Kafka, L1 Caffeine configurations
        │   ├── controller/         # Redirection, shortener creation, and stats endpoints
        │   ├── dto/                # DTO records (ClickEvent, UrlRequest, UrlResponse)
        │   ├── entity/             # JPA Entities (composite partition keys mapped)
        │   ├── exception/          # Custom ResourceNotFoundException
        │   ├── repository/         # Repositories (JPA & Custom Bulk JDBC batch inserts)
        │   ├── service/            # Core business logic (Shortener, Consumer, ID Generator)
        │   └── util/               # Feistel Cipher & Base62 encoders
        └── resources/
            └── application.yml     # Application properties (Postgres, Redis, Kafka mappings)
```

---

## 🚦 Getting Started

### Prerequisites
* **Java 17 SDK**
* **Docker Desktop** & **Docker Compose**
* An IDE (e.g. **IntelliJ IDEA** or **VS Code**)

### Step 1: Start the Infrastructure
Make sure your Docker daemon is running, then execute:
```bash
docker compose up -d
```
*This starts PostgreSQL on port `5432`, Redis on `6379`, and Kafka on `9092`. The database tables and partitions are automatically initialized by `01-schema.sql`.*

### Step 2: Run the Spring Boot Application
You can run the application directly from your IDE by launching `com.shortener.UrlShortenerApplication`, or run via command line:
```bash
# If you have maven installed
mvn spring-boot:run
```

---

## 📡 Testing the API

Here are the curl commands to interact with the service:

### 1. Create a Shortened URL
Create a mapping with a custom Time-To-Live (TTL) of 24 hours (`86400` seconds):
```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "longUrl": "https://news.ycombinator.com",
    "ttlSeconds": 86400
  }'
```
**Example Response:**
```json
{
  "shortUrl": "2rJ7d",
  "longUrl": "https://news.ycombinator.com",
  "expiresAt": "2026-06-12T16:53:30Z"
}
```

### 2. Follow the Redirect (Generates Analytics Event)
Request the redirect. Use `-v` to inspect the response status (`302 Found`) and `Location` header:
```bash
curl -v http://localhost:8080/2rJ7d
```
*Behind the scenes, this lookup fetches the target in <5ms. The app asynchronously logs user headers (User-Agent, Referrer, IP) to Kafka.*

### 3. Retrieve Aggregated Analytics Dashboard
Request the day-by-day click aggregates for the URL:
```bash
curl http://localhost:8080/api/v1/analytics/2rJ7d
```
**Example Response:**
```json
[
  {
    "shortCode": "2rJ7d",
    "clickDate": "2026-06-11",
    "clickCount": 1,
    "deviceDesktopCount": 1,
    "deviceMobileCount": 0,
    "deviceTabletCount": 0
  }
]
```

---

## 🔍 System Verification & Load Testing Ideas
To test the system limits under **50K URLs/day** and redirect latency:
1. **Verify Cache Miss Lock**: Trigger 1,000 parallel threads for a missing key and monitor Postgres queries. You should see exactly 1 database query executed, while 999 threads are blocked until the lock releases.
2. **Verify L1 Cache**: Benchmark the redirection endpoint using `wrk` or `JMeter`. You should see response times drop to sub-millisecond levels as Caffeine intercepts reads before they reach Redis.
3. **Verify Kafka Consumer Lag**: Push 100,000 click events into Kafka. Query the consumer offset lag to see it drain within seconds due to our optimized JDBC batch upserts.
