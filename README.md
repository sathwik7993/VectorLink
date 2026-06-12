# ⚡ VectorLink — High-Performance URL Shortener & Analytics Platform

VectorLink is a production-grade, highly optimized URL shortener system built for low latency and high scalability. It features a modern dark-glassmorphism web dashboard, real-time analytics aggregation, QR code generation, rate limiting, and an event-driven architecture.

Designed to handle **50,000+ URL creations/day**, maintain **<5ms P99 redirect latency**, and achieve **<1s analytics ingestion lag** under heavy concurrency.

---

## ✨ Key Features

- **🎨 Premium Web UI**: Responsive dark glassmorphism landing page, dashboard (link toggling, deleting, filtering), and interactive analytics charts (powered by Chart.js).
- **🔒 URL Obfuscation**: Employs a 62-bit **Feistel Block Cipher** on sequence IDs before Base62 encoding. Ensures short codes look completely random (e.g. `1` becomes `4d3X2`) to prevent URL harvesting/enumeration attacks.
- **🏷️ Custom Aliases**: Support for user-defined custom short paths (e.g. `localhost:8080/my-brand`) with strict alphanumeric validation.
- **📱 QR Code Generator**: Generates downloadable high-quality PNG QR codes for every shortened URL instantly using the ZXing library.
- **🚀 Dual-Layer Caching (L1 + L2)**: 
  - **L1 (Local Caffeine Cache)**: Shielding the database from hot-spot thundering herds on viral links.
  - **L2 (Distributed Redis Cache)**: Cache-Aside fallback for low-latency retrieval.
- **🔒 Rate Limiting**: Built-in Bucket4j IP-based rate limiting (20 req/min for creator APIs, 100 req/min for redirects) preventing DDoS and scraping attacks.
- **📈 Event-Driven Analytics**: Redirect paths offload click events asynchronously to **Apache Kafka**. A batch consumer gathers metadata (User-Agent, IP, Country, Referer) and aggregates it into a partitioned **PostgreSQL** schema in bulk.
- **🛡️ Fallback Resilience**: Redis `INCR` acts as the primary sequence provider. If Redis goes offline, the system transparently falls back to a PostgreSQL sequence (`seq_short_urls`), preventing downtime.

---

## 🛠️ Architecture Stack

- **Backend**: Java 17, Spring Boot 3.2.4 (JPA, Redis, Spring Kafka, Caffeine, Actuator)
- **Frontend**: Vanilla HTML5, CSS3 (Glassmorphism design system), JavaScript (ES6+, Chart.js)
- **Database**: PostgreSQL 15 (Declarative Range Partitioning by month)
- **Cache**: Redis 7 & Caffeine L1
- **Message Broker**: Apache Kafka (KRaft Single-Broker mode)
- **Containerization**: Docker & Docker Compose

---

## 📂 Project Structure

```
├── docker-compose.yml              # PostgreSQL, Redis, Kafka, Web App stack
├── Dockerfile                      # Multi-stage optimized JRE production build
├── render.yaml                     # Render.com Blueprint configuration
├── fly.toml                        # Fly.io deployment config
├── infrastructure/
│   └── init-db/
│       └── 01-schema.sql           # Schema with partitioned tables & indexes
└── src/
    └── main/
        ├── java/com/shortener/
        │   ├── config/             # Caching, Web, Rate Limiting, Kafka configurations
        │   ├── controller/         # URL creation, redirection, QR, and analytics APIs
        │   ├── dto/                # Data Transfer Records (ClickEvent, Request/Response)
        │   ├── entity/             # Hibernate JPA database mapping entities
        │   ├── exception/          # Global exception advice handling
        │   ├── repository/         # JPA repositories & Custom JDBC batch updates
        │   └── service/            # Core business logic services
        └── resources/
            ├── application.yml     # Local configuration properties
            ├── application-prod.yml# Production properties (Kafka optional)
            └── static/             # Frontend static files (HTML, CSS, JS)
```

---

## 🚦 Getting Started (Setup & Run)

### Prerequisites
- [Java 17 SDK](https://adoptium.net/temurin/releases/?version=17) or higher
- [Maven 3.9+](https://maven.apache.org/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (daemon must be running)

---

### Option 1: Run Everything with Docker Compose (Recommended)

This is the easiest way to spin up the entire production-grade stack (App, Postgres, Redis, Kafka):

1. Start the Docker daemon (open Docker Desktop).
2. Run the compose script from the project root:
   ```bash
   docker compose up --build -d
   ```
3. Open your browser and go to:
   👉 **[http://localhost:8080](http://localhost:8080)**

---

### Option 2: Run Application Locally (For Development)

If you want to run the Spring Boot app locally in your IDE/terminal while hosting the database, cache, and broker inside Docker:

1. Start only the backing infrastructure services:
   ```bash
   docker compose up -d postgres redis kafka
   ```
2. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
3. Open your browser and go to:
   👉 **[http://localhost:8080](http://localhost:8080)**

---

## 📡 API Reference

### 1. Create a Short URL
- **Endpoint**: `POST /api/v1/urls`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "longUrl": "https://news.ycombinator.com",
    "ttlSeconds": 86400,
    "customAlias": "hn" 
  }
  ```
- **Response** (201 Created):
  ```json
  {
    "shortCode": "hn",
    "longUrl": "https://news.ycombinator.com",
    "expiresAt": "2026-06-13T17:39:42Z",
    "customAlias": "hn",
    "active": true,
    "clickCount": 0,
    "qrCodeUrl": "/api/v1/qr/hn"
  }
  ```

### 2. List All URLs
- **Endpoint**: `GET /api/v1/urls`
- **Response** (200 OK): Returns a list of all active/inactive shortened URLs ordered by creation date (newest first).

### 3. Toggle Active State
- **Endpoint**: `PATCH /api/v1/urls/{shortCode}/toggle`
- **Response** (200 OK): Toggles the target link's active state. Inactive links will immediately return a `404 Not Found` when resolved.

### 4. Delete a Link
- **Endpoint**: `DELETE /api/v1/urls/{shortCode}`
- **Response** (204 No Content): Deletes the URL from the database and evicts it from all caches.

### 5. Fetch Daily Analytics
- **Endpoint**: `GET /api/v1/analytics/{shortCode}`
- **Response** (200 OK):
  ```json
  [
    {
      "shortCode": "hn",
      "clickDate": "2026-06-12",
      "clickCount": 14,
      "deviceDesktopCount": 10,
      "deviceMobileCount": 3,
      "deviceTabletCount": 1
    }
  ]
  ```

### 6. Get QR Code PNG
- **Endpoint**: `GET /api/v1/qr/{shortCode}`
- **Response** (200 OK): Returns a downloadable high-quality PNG QR Code image.

---

## 🐳 Cloud Deployment

For deploying VectorLink to the cloud for free, refer to the step-by-step instructions in the [Deployment Guide (DEPLOYMENT.md)](./DEPLOYMENT.md) which includes setup details for:
- **Render.com** (via `render.yaml` Blueprint)
- **Fly.io** (via `fly.toml`)
- **Upstash Redis** (for free-tier serverless cache)

---

## 📜 License

This project is open-source and licensed under the MIT License.
