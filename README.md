# ⚡ VectorLink — Enterprise-Grade URL Shortener & Analytics Platform

**Live Link**: [https://vectorlink.duckdns.org/](https://vectorlink.duckdns.org/)

VectorLink is a production-ready, highly optimized URL shortener system built for microsecond-scale redirects, extreme resilience, and real-time observability. It features a premium Light Skeuomorphic web UI inspired by Apple VisionOS & macOS design aesthetics, secure persistent authentication (Google OAuth2 + JWT), comprehensive Prometheus & Grafana monitoring, and an event-driven analytics engine.

Designed to easily handle **50,000+ URL creations/day**, maintain **<5ms P99 redirect latency**, and achieve **<1s analytics ingestion lag** under high concurrency.

---

## ✨ Features Overview

### 🔑 Authentication & Session Security
- **Secure JWT Persistent Sessions**: Users stay logged in for **30 days** using secure, signed, and `HttpOnly` cookie-based JWT authentication.
- **Google OAuth2 Integration**: Seamless social sign-in with Google, plus fallback to local registration & login (passwords secured using BCrypt).
- **Secure REST APIs**: Role-based security securing all modification, deletion, and analytic endpoints, returning clean `401 Unauthorized` JSON responses on unauthorized API access.

### 🛡️ High Availability & Database Resilience
- **Redis Downtime Circuit Breaker**: If Redis goes offline, the system automatically stretches L1 cache (Caffeine) expiration from 15 seconds to 10 minutes to protect PostgreSQL from database thundering herds, and falls back to a Postgres database sequence (`seq_short_urls`) for ID generation. A recovery scheduler pings Redis every 30 seconds to restore normal cache behaviors.
- **Fail-Safe HiLo ID Generator**: Automatically pre-allocates blocks of 1,000 sequence IDs to prevent expensive database roundtrips on hot redirect creation paths.
- **Kafka Resilience Buffer**: Click events are sent asynchronously to Apache Kafka. If Kafka goes down, events automatically buffer in an in-memory queue (capacity 50,000) or are written to a local dead-letter log (`logs/dead_letter_clicks.log`). A background scheduler recovers and drains buffered click events to Kafka once it's healthy.

### 📈 Real-Time & Advanced Analytics
- **User-Agent & Browser Resolution**: Deep parser resolves Operating Systems (Windows, macOS, iOS, Android, Linux, Other) and Browsers (Chrome, Safari, Firefox, Edge, Other) from requests.
- **IP Geolocation**: Resolves and caches visitor country code via external APIs with local loopback/local IP address matching (to `"LO"`).
- **Interactive Analytics Dashboard**: Beautiful visualizations mapping clicks over time, browser/OS/device distributions using **Chart.js**, an **interactive 3D Global Analytics Map** powered by **MapLibre GL** showing extruded click bars rising progressively from precise geolocation points, an **hourly traffic distribution graph** (24h UTC), and **top referring domains** ranking.
- **Premium UI Animations**: Modern motion-design features hardware-accelerated entrance transitions (staggered slide-up using custom cubic-bezier curves) and interactive lift effects on card hover.

### 🔒 Privacy, Gating & Redirection Control
- **Password Protection**: Gate your short URLs with a password. Redirects are blocked until verified through a custom password entry card (`password.html`) which sets secure, short-term session cookies.
- **Fallback URL Support**: Define fallback destinations. If a link is deactivated or expired, traffic redirects to your fallback page instead of returning a 404 page.
- **Query Parameter Forwarding**: Automatically appends incoming query strings (e.g., `?utm_source=twitter`) from the short link onto the final long URL destination during the redirect process.

---

## 🛠️ Tech Stack & Architecture

- **Backend Framework**: Java 17, Spring Boot 3.2.4 (Security, OAuth2 Client, Cache, Kafka, Micrometer, Actuator)
- **Frontend UI**: Vanilla HTML5, CSS3 Light Skeuomorphic design (Apple VisionOS & macOS premium aesthetics), JavaScript (ES6+, Chart.js, MapLibre GL)
- **Primary Database**: PostgreSQL 15 (Declarative monthly range-partitioned tables for click logs, custom indexes for fast analytics queries)
- **Distributed Caching**: Redis 7 & Caffeine L1 Cache
- **Event Streaming**: Apache Kafka (KRaft Single-Broker mode)
- **Monitoring & Observability**: Prometheus v2.45, Grafana v10.0.3
- **CI/CD**: GitHub Actions CI/CD Pipeline

---

## 📂 Project Directory Structure

```
├── .github/workflows/
│   └── ci-cd.yml                   # CI/CD pipeline (tests, builds docker image, pushes to GHCR)
├── load-tests/                     # k6 performance testing scripts (smoke, load, stress, spike)
├── infrastructure/
│   ├── init-db/
│   │   └── 01-schema.sql           # PostgreSQL schema (users, partitioned click logs, daily summaries)
│   └── monitoring/
│       ├── prometheus.yml          # Prometheus scrape target configuration
│       └── grafana/                # Provisioned Grafana dashboard & datasources config
├── docker-compose.yml              # Local multi-container development/observability stack
├── Dockerfile                      # Multi-stage optimized JRE runtime container build
├── .env.example                    # Template for Google OAuth environment variables
└── src/
    └── main/
        ├── java/com/shortener/
        │   ├── config/             # Caching, Security, Rate Limiting, Kafka, Async configuration
        │   ├── controller/         # REST Controllers (Auth, URL shortener, Analytics, QR)
        │   ├── dto/                # Records (ClickEvent, Login/Register Request, UrlResponse)
        │   ├── entity/             # Hibernate JPA database mapping entities
        │   ├── repository/         # JPA repositories & Custom JDBC batch update queries
        │   └── service/            # Core business, ID generation, security & consumer services
        └── resources/
            ├── application.yml     # Application configuration
            └── static/             # Frontend static resources (pages, stylesheet, JS controllers)
```

---

## 🚦 Local Setup & Run

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Docker daemon must be running)
- Google OAuth credentials (Client ID and Secret obtained from the Google Cloud Console)

### Steps

1. **Configure Environment Variables**:
   Copy `.env.example` to `.env` in the root directory and add your Google credentials:
   ```bash
   cp .env.example .env
   ```
   Edit `.env`:
   ```env
   GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=your-google-client-secret
   ```

2. **Launch the Container Stack**:
   Rebuild and launch all services (App, PostgreSQL, Redis, Kafka, Prometheus, Grafana) in detached mode:
   ```bash
   docker compose up --build -d
   ```

3. **Explore Services**:
   - ⚡ **VectorLink Web App**: [http://localhost:8080](http://localhost:8080)
   - 📊 **Prometheus Scrape Portal**: [http://localhost:9090](http://localhost:9090)
   - 📉 **Grafana Dashboard**: [http://localhost:3000](http://localhost:3000) (Default Login: `admin` / `admin`)

---

## 📡 Core API Endpoints

### Auth APIs
- `POST /api/v1/auth/register` — Register a new account locally.
- `POST /api/v1/auth/login` — Sign in and receive a persistent secure cookie.
- `POST /api/v1/auth/logout` — Revoke session cookie.
- `GET /api/v1/auth/me` — Retrieve active user session details.

### Short URL APIs
- `POST /api/v1/urls` — Create a shortened URL. Accepts custom alias, expiry TTL (seconds), password protection, and fallback URL.
- `GET /api/v1/urls` — Get all shortened URLs belonging to the authenticated user.
- `PATCH /api/v1/urls/{shortCode}/toggle` — Toggle the link active/inactive.
- `DELETE /api/v1/urls/{shortCode}` — Delete the link and evict it from all caches.
- `GET /api/v1/qr/{shortCode}` — Retrieve a high-quality downloadable QR Code PNG.

### Analytics APIs
- `GET /api/v1/analytics/{shortCode}` — Fetch daily aggregated analytics metrics for the given short code.
- `GET /api/v1/analytics/{shortCode}/countries` — Fetch visitor country code distributions (country to click count map).
- `GET /api/v1/analytics/{shortCode}/referrers` — Fetch visitor referrer distributions (referrer domain to click count map).
- `GET /api/v1/analytics/{shortCode}/hourly` — Fetch 24-hour traffic pattern distributions (hour of day to click count map).

---

## 📈 Monitoring & Performance Validation

### Grafana Monitoring
You can monitor the JVM, DB pooling metrics, Kafka buffer lag, and HTTP redirect latency histograms. Log in at [http://localhost:3000](http://localhost:3000) using the default credentials (`admin`/`admin`).

### Load Testing with k6
The `load-tests/` directory contains k6 test scripts. To run load tests against the endpoints (e.g., redirect endpoints):
1. Install [k6](https://k6.io/docs/getting-started/installation/).
2. Run any of the configured scenarios:
   ```bash
   k6 run load-tests/load_test.js
   ```

---

## 📜 License

This project is licensed under the MIT License.
