# FIX Protocol Trading Simulator

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![QuickFIX/J](https://img.shields.io/badge/QuickFIX%2FJ-2.3.1-blueviolet?style=flat-square)](http://www.quickfixj.org/)
[![FIX Protocol](https://img.shields.io/badge/FIX-4.4-blue?style=flat-square)]()
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=flat-square&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

A **self-contained FIX 4.4 trading simulator** built with [QuickFIX/J](http://www.quickfixj.org/) and Spring Boot 3.

Submit orders via REST API → they are routed over a live FIX 4.4 session to a mock exchange running inside the same JVM → execution reports update order state asynchronously in real time.

---

## Table of Contents

- [Architecture](#architecture)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Quick Start — Docker (recommended)](#quick-start--docker-recommended)
- [Quick Start — Eclipse](#quick-start--eclipse)
- [Quick Start — Maven CLI](#quick-start--maven-cli)
- [API Reference](#api-reference)
- [FIX Message Flow](#fix-message-flow)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [Configuration Reference](#configuration-reference)

---

## Architecture

```
 REST Client (curl / Swagger UI / Postman)
        │
        │  HTTP POST /api/orders
        ▼
 ┌─────────────────────────────────────────────────────────┐
 │                  Spring Boot Application                 │
 │                                                         │
 │  OrderController ──► OrderService ──► OrderRepository   │
 │                           │              (ConcurrentHashMap)
 │                           │ sendNewOrderSingle()
 │                           ▼
 │            ┌─────────────────────────┐
 │            │       FixClient         │  FIX Initiator
 │            │   (QuickFIX/J)          │  SenderCompID: CLIENT
 │            └────────────┬────────────┘  Port: 9878
 │                         │
 │           FIX 4.4 NewOrderSingle (MsgType=D)
 │                         │
 │            ┌────────────▼────────────┐
 │            │  MockExchangeAcceptor   │  FIX Acceptor
 │            │   (QuickFIX/J)          │  SenderCompID: EXCHANGE
 │            └────────────┬────────────┘  Port: 9878
 │                         │
 │     FIX 4.4 ExecutionReport (MsgType=8)
 │        OrdStatus=NEW  → then → OrdStatus=FILLED
 │                         │
 │            ┌────────────▼────────────┐
 │            │       FixClient         │
 │            │   fromApp() callback    │
 │            └────────────┬────────────┘
 │                         │ updateOrderStatus()
 │                         ▼
 │                   OrderService
 │                   OrderRepository
 └─────────────────────────────────────────────────────────┘
```

---

## Features

| Feature | Details |
|---|---|
| **FIX 4.4 Protocol** | Full session management, heartbeats, logon/logout |
| **NewOrderSingle** | Place LIMIT and MARKET orders |
| **OrderCancelRequest** | Cancel open orders |
| **ExecutionReport** | Asynchronous fill simulation (NEW → FILLED) |
| **REST API** | 4 endpoints with full Swagger/OpenAPI documentation |
| **In-memory Order Book** | Thread-safe `ConcurrentHashMap` with dual indexing |
| **Docker** | Multi-stage build, single `docker-compose up` to run |
| **Tests** | 30+ unit tests — repository, service, controller layers |
| **Actuator** | `/actuator/health`, `/actuator/metrics` endpoints |

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 17 or later | [Download Temurin 17](https://adoptium.net/) |
| Maven | 3.8+ | Bundled with Eclipse (m2e) |
| Docker Desktop | 24+ | Only needed for Docker run |
| Eclipse IDE | 2023-09+ | With m2e plugin (bundled) |

---

## Quick Start — Docker (recommended)

No Java installation required. One command.

```bash
# 1. Clone the repository
git clone https://github.com/RIDHVI07/fix-trading-simulator.git
cd fix-trading-simulator

# 2. Build and run
docker-compose up --build

# 3. Open Swagger UI in your browser
open http://localhost:8080/swagger-ui.html
```

**Expected startup output:**

```
fix-trading-simulator  | MockExchangeAcceptor started on port 9878
fix-trading-simulator  | FixClient initiator started — connecting to localhost:9878
fix-trading-simulator  | FIX session logged on: FIX.4.4:CLIENT->EXCHANGE
fix-trading-simulator  | Started TradingSimulatorApplication in 4.2 seconds
```

**Stop the container:**

```bash
docker-compose down
```

---

## Quick Start — Eclipse

### Step 1 — Import the project

1. Open **Eclipse IDE**
2. Go to **File → Import → Maven → Existing Maven Projects**
3. Click **Browse** → navigate to the cloned `fix-trading-simulator` folder
4. Click **Finish**
5. Eclipse will automatically download all Maven dependencies (this takes ~2 minutes on first import)

> **Tip:** If you see red error markers, right-click the project → **Maven → Update Project** → check **Force Update of Snapshots/Releases** → OK.

### Step 2 — Run the application

1. In **Package Explorer**, expand `src/main/java`
2. Navigate to `com.trading.simulator`
3. Right-click `TradingSimulatorApplication.java`
4. Select **Run As → Java Application**
5. Watch the Console tab — you should see:

```
MockExchangeAcceptor started on port 9878
FixClient initiator started
FIX session logged on: FIX.4.4:CLIENT->EXCHANGE
Started TradingSimulatorApplication in 4.x seconds
```

### Step 3 — Open Swagger UI

Navigate to: **http://localhost:8080/swagger-ui.html**

---

## Quick Start — Maven CLI

```bash
# Clone
git clone https://github.com/RIDHVI07/fix-trading-simulator.git
cd fix-trading-simulator

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

---

## API Reference

Once running, the full interactive API is available at:

**`http://localhost:8080/swagger-ui.html`**

### Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/orders` | Place a new order |
| `GET` | `/api/orders` | List all orders (optional `?symbol=AAPL` filter) |
| `GET` | `/api/orders/{orderId}` | Get order by ID |
| `DELETE` | `/api/orders/{orderId}` | Cancel an open order |

---

### Place a LIMIT order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "symbol":   "AAPL",
    "side":     "BUY",
    "type":     "LIMIT",
    "quantity": 100,
    "price":    150.50
  }'
```

**Response `201 Created`:**

```json
{
  "orderId":   "3f7a2b1c-...",
  "symbol":    "AAPL",
  "side":      "BUY",
  "type":      "LIMIT",
  "quantity":  100,
  "filledQty": 0,
  "price":     150.50,
  "status":    "PENDING",
  "createdAt": "2025-05-08T14:30:01"
}
```

After ~2 seconds, polling `GET /api/orders/{orderId}` will show:

```json
{
  "status":        "FILLED",
  "filledQty":     100,
  "avgFillPrice":  150.50
}
```

---

### Place a MARKET order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "symbol":   "TSLA",
    "side":     "SELL",
    "type":     "MARKET",
    "quantity": 50
  }'
```

---

### List all orders

```bash
curl http://localhost:8080/api/orders

# Filter by symbol
curl "http://localhost:8080/api/orders?symbol=AAPL"
```

---

### Cancel an order

```bash
curl -X DELETE http://localhost:8080/api/orders/{orderId}
```

---

## FIX Message Flow

```
Client (FixClient)                    Exchange (MockExchangeAcceptor)
      |                                           |
      |──── Logon (MsgType=A) ──────────────────►|
      |◄─── Logon (MsgType=A) ─────────────────── |
      |                                           |
      |  [POST /api/orders]                       |
      |──── NewOrderSingle (D) ─────────────────►|
      |         ClOrdID=CLO-123                   |  Validates order
      |         Symbol=AAPL                       |
      |         Side=1 (BUY)                      |
      |         OrdType=2 (LIMIT)                 |
      |         Price=150.50                      |
      |         OrderQty=100                      |
      |                                           |
      |◄─── ExecutionReport (8) ──────────────── |
      |         OrdStatus=0 (NEW)                 |  Acknowledged
      |         CumQty=0                          |
      |         LeavesQty=100                     |
      |                                           |
      |       [~2 seconds later]                  |
      |◄─── ExecutionReport (8) ──────────────── |
      |         OrdStatus=2 (FILLED)              |  Fully filled
      |         CumQty=100                        |
      |         AvgPx=150.50                      |
      |                                           |
```

---

## Running Tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=OrderServiceTest

# Run with coverage report
mvn verify
```

**Test coverage summary:**

| Layer | Test Class | Tests |
|---|---|---|
| Repository | `OrderRepositoryTest` | 10 tests |
| Service | `OrderServiceTest` | 14 tests |
| Controller | `OrderControllerTest` | 10 tests |
| Integration | `TradingSimulatorApplicationTests` | 1 test |

---

## Project Structure

```
fix-trading-simulator/
├── src/
│   ├── main/java/com/trading/simulator/
│   │   ├── TradingSimulatorApplication.java   # Entry point
│   │   ├── config/
│   │   │   └── OpenApiConfig.java             # Swagger configuration
│   │   ├── controller/
│   │   │   └── OrderController.java           # REST endpoints
│   │   ├── dto/
│   │   │   ├── PlaceOrderRequest.java         # Inbound request body
│   │   │   └── OrderResponse.java             # Outbound response body
│   │   ├── fix/
│   │   │   ├── FixClient.java                 # FIX initiator (CLIENT side)
│   │   │   └── MockExchangeAcceptor.java      # FIX acceptor (EXCHANGE side)
│   │   ├── model/
│   │   │   ├── Order.java
│   │   │   ├── OrderSide.java                 # BUY | SELL
│   │   │   ├── OrderStatus.java               # PENDING → NEW → FILLED
│   │   │   └── OrderType.java                 # MARKET | LIMIT
│   │   ├── repository/
│   │   │   └── OrderRepository.java           # Thread-safe in-memory store
│   │   └── service/
│   │       └── OrderService.java              # Business logic
│   ├── main/resources/
│   │   └── application.properties
│   └── test/java/com/trading/simulator/
│       ├── TradingSimulatorApplicationTests.java
│       ├── controller/
│       │   └── OrderControllerTest.java
│       ├── repository/
│       │   └── OrderRepositoryTest.java
│       └── service/
│           └── OrderServiceTest.java
├── Dockerfile                                 # Multi-stage build
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Configuration Reference

All properties are in `src/main/resources/application.properties` and can be overridden via environment variables.

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP server port |
| `fix.acceptor.port` | `9878` | FIX session port (acceptor listens here) |
| `fix.initiator.host` | `localhost` | Host the initiator connects to |
| `fix.fill.delay.seconds` | `2` | Seconds before exchange sends FILLED report |

**Override via environment variable (Docker):**

```yaml
environment:
  - fix.fill.delay.seconds=5
```

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| FIX Engine | QuickFIX/J 2.3.1 |
| FIX Version | FIX 4.4 |
| API Docs | SpringDoc OpenAPI 2.3 (Swagger UI) |
| Build | Maven 3.8+ |
| Container | Docker + Docker Compose |
| Testing | JUnit 5, Mockito, MockMvc |

---

## Author

**Ridhvi Kulshrestha** — Backend Developer · Java & Spring Boot · Electronic Trading Systems

- Email: ridhvikul07@gmail.com
- LinkedIn: [linkedin.com/in/ridhvi-kulshrestha](https://linkedin.com/in/ridhvi-kulshrestha)
- GitHub: [github.com/RIDHVI07](https://github.com/RIDHVI07)
