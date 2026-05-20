# ClearCore

A production-grade payment clearing and settlement backend built with Java 21 / Spring Boot 3.2, implementing core acquiring-bank workflows: authorization, capture, reversal, fraud screening, rate limiting, settlement, and event streaming.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     REST API  (port 8080)                     │
│               Spring Boot 3.2 · Tomcat 10.1                   │
└────────────────────────┬─────────────────────────────────────┘
                         │
         ┌───────────────┼────────────────┐
         ▼               ▼                ▼
   Authorization     Fraud Engine    Rate Limiter
   Service           (Chain of       (Redis Token
   (@Version         Responsibility) Bucket)
   optimistic lock)
         │               │                │
         └───────────────┼────────────────┘
                         ▼
               ┌──────────────────┐
               │   PostgreSQL 15   │  ←── Flyway migrations (V1, V2)
               │  (JPA/Hibernate)  │
               └─────────┬────────┘
                         │
               ┌─────────┴────────┐
               │   Apache Kafka    │  ←── transaction-events (6 partitions)
               │  (event stream)   │
               └─────────┬────────┘
                         │
               ┌─────────┴────────┐
               │   Spring Batch    │  ←── Hourly settlement job
               │  (settlement)     │      Double-entry ledger
               └──────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Web | Spring MVC · Tomcat 10.1 |
| Persistence | Spring Data JPA · Hibernate 6.3 · PostgreSQL 15 |
| Cache / Rate Limit | Spring Data Redis · Token Bucket |
| Messaging | Apache Kafka 3.6 · 6 partitions |
| Batch / Settlement | Spring Batch · hourly cron |
| Schema Migrations | Flyway 9.22 (V1, V2) |
| Testing | JUnit 5 · Mockito · Testcontainers |
| Coverage | JaCoCo 0.8.11 |
| Build | Maven 3 · Spring Boot Maven Plugin 3.2 |

---

## Features

### 1. Payment Authorization
- ISO 8583-aligned response codes (00 Approved, 05 Do Not Honor, 51 Insufficient Funds, 54 Expired, 62 Restricted)
- Optimistic locking via `@Version` — prevents double-spend under concurrent requests without serializing writes
- Idempotency via Redis — duplicate requests within TTL window return the cached response
- Merchant and card validation at authorization time

### 2. Capture & Reversal
- Partial capture support (capture less than the authorized amount)
- Pre-capture void (cancel before settlement)
- Post-capture refund (reverse after settlement)
- Explicit state machine: `AUTHORIZED → CAPTURED → SETTLED / REFUNDED`

### 3. Fraud Engine
- Chain-of-responsibility pattern — rules are pluggable, independently testable, zero coupling
- **Velocity check** — blocks cards exceeding N transactions per time window
- **Amount threshold** — flags single transactions above configurable limit
- **Geo mismatch** — detects card-present transactions in unexpected regions
- First failing rule short-circuits the chain

### 4. Rate Limiting
- Redis token bucket per merchant — correct across multiple application instances
- Returns `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers on every response
- HTTP 429 with `Retry-After` on exhaustion

### 5. Settlement (Spring Batch)
- Hourly scheduled job aggregates all `CAPTURED` transactions
- Double-entry ledger: debit merchant, credit acquirer
- Sum-zero integrity enforced via assertion tests on every settlement run
- Full job history persisted by Spring Batch in the database

### 6. Event Streaming (Kafka)
- Every payment state change publishes an event to `transaction-events` (6 partitions)
- Consumer group `payment-consumer-group` persists events to `event_log` table for audit trail

---

## Project Structure

```
src/
├── main/java/com/payments/
│   ├── PaymentProcessingApplication.java
│   ├── api/
│   │   ├── controller/          # TransactionController, SettlementController
│   │   ├── request/             # AuthorizationRequest, CaptureRequest, ReversalRequest
│   │   ├── response/            # TransactionResponse, SettlementResponse, ApiResponse
│   │   └── GlobalExceptionHandler.java
│   ├── batch/
│   │   ├── SettlementBatchConfig.java
│   │   └── SettlementJobScheduler.java
│   ├── config/                  # KafkaConfig, RedisConfig, JacksonConfig, SecurityConfig
│   ├── domain/
│   │   ├── entity/              # Transaction, Card, Merchant, LedgerEntry, SettlementRecord, EventLog
│   │   └── enums/               # TransactionStatus, ResponseCode, LedgerEntryType
│   ├── kafka/                   # TransactionEventPublisher, TransactionEventConsumer, TransactionEvent
│   ├── repository/              # 6 Spring Data JPA repositories
│   └── service/
│       ├── fraud/               # FraudRuleEngine, VelocityCheckRule, AmountThresholdRule, GeoMismatchRule
│       ├── AuthorizationService.java
│       ├── CaptureService.java
│       ├── ReversalService.java
│       ├── LedgerService.java
│       ├── RateLimiterService.java
│       └── SettlementService.java
├── main/resources/
│   ├── application.yml
│   └── db/migration/
│       ├── V1__initial_schema.sql
│       └── V2__seed_data.sql
└── test/java/com/payments/
    ├── concurrency/             # ConcurrencyTest — 20-thread overdraw
    ├── fraud/                   # FraudRuleTest — 10 rule scenarios
    ├── integration/             # TransactionLifecycleIntegrationTest (Testcontainers)
    └── service/                 # AuthorizationServiceTest, CaptureServiceTest,
                                 # ReversalServiceTest, RateLimiterServiceTest,
                                 # LedgerIntegrityTest, CardTest
```

---

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.8+
- Docker Desktop

### 1. Start Infrastructure

```bash
docker-compose up postgres redis zookeeper kafka -d
```

### 2. Run the Application

```bash
mvn spring-boot:run
```

Application starts on `http://localhost:8080`. Flyway applies migrations automatically on first boot.

### 3. Verify Health

```bash
# Linux / macOS
curl http://localhost:8080/actuator/health

# PowerShell
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health | Select-Object -ExpandProperty Content
```

Expected: `{"status":"UP"}`

---

## API Reference

### Authorize a Payment

```http
POST /api/v1/payments/authorize
Content-Type: application/json

{
  "amount": 100.00,
  "currency": "USD",
  "cardNumber": "4111111111111111",
  "merchantId": "MERCH001",
  "idempotencyKey": "unique-key-001"
}
```

### Capture a Payment

```http
POST /api/v1/payments/{transactionId}/capture
Content-Type: application/json

{
  "amount": 100.00
}
```

### Reverse / Refund

```http
POST /api/v1/payments/{transactionId}/reverse
```

### Health & Metrics

```http
GET /actuator/health
GET /actuator/metrics
```

---

## Running Tests

```bash
# All tests (requires Docker for Testcontainers)
mvn test

# View coverage report (after mvn test)
# macOS
open target/site/jacoco/index.html
# Windows
start target/site/jacoco/index.html
```

---

## Configuration

Key properties in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payments
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: payment-consumer-group
      auto-offset-reset: earliest

payments:
  fraud:
    velocity-limit-per-minute: 5
    single-txn-amount-threshold: 100000
  rate-limit:
    requests-per-minute: 100
    burst-size: 20
  settlement:
    cron: "0 0 * * * *"
    batch-size: 100
```

---

## Evidence of Running System

| Checkpoint | Output |
|---|---|
| Spring Boot started | `Started PaymentProcessingApplication in 12.067s` |
| PostgreSQL connected | `HikariPool-1 - Start completed` |
| Flyway migrations applied | `Successfully applied 2 migrations to schema "public"` |
| JPA repositories wired | `Found 6 JPA repository interfaces` |
| Tomcat listening | `Tomcat started on port 8080 (http)` |
| Kafka consumer joined | `partitions assigned: [transaction-events-0..5]` |
| Actuator health | `HTTP 200 {"status":"UP"}` |

---

## Key Design Decisions

**Why optimistic locking?** Pessimistic locking serializes all auth requests through a single row lock, killing throughput under load. `@Version`-based optimistic locking allows concurrent reads and only retries on the rare actual conflict — correct behaviour with near-zero overhead at normal concurrency levels.

**Why chain-of-responsibility for fraud?** Rules need to be added, removed, and reordered without touching existing code. CoR makes each rule a standalone, independently testable unit with no coupling between rules.

**Why Redis token bucket over in-memory rate limiting?** In-memory state breaks across multiple application instances. Redis gives a single shared counter that enforces limits correctly whether the system runs one pod or ten.

**Why Spring Batch for settlement?** Settlement is inherently chunk-oriented — read N captured transactions, process, write ledger entries. Spring Batch provides restart/retry semantics, configurable skip policies, and persistent job history out of the box.