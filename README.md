# Cashi Fee Workflow

A RESTful fee processing service built with **Kotlin** and **Restate** durable workflow engine.
Calculates, charges, and records fees for financial transactions with full idempotency guarantees.

## Architecture

```
Client → Restate Server (:8080) → Fee Service (:9080) → PostgreSQL (:5432)
```

### Components

| Component | Role |
|-----------|------|
| **FeeWorkflow** | Restate `@Workflow` — orchestrates fee calculation, charging, and recording as durable steps |
| **FeeService** | Restate `@Service` — handles queries (fee history, by transaction) |
| **FeeStrategyResolver** | Strategy pattern — resolves the correct fee calculation strategy per transaction type |
| **FeeRepository** | PostgreSQL persistence with idempotent inserts via partial unique indexes |
| **Restate Server** | Durable execution runtime — manages retries, journal replay, and workflow state |

### Transaction Types

| Type | Rate | Description |
|------|------|-------------|
| Mobile Top Up | 0.15% | Standard rate |
| Bank Transfer | 0.10% | Min $0.50 |
| Crypto Exchange | 0.30% | Crypto rate |
| Card Payment | 0.20% | Card rate |

## Running

### Prerequisites

- Docker and Docker Compose

### Start

```bash
docker compose up --build
```

This starts:
- **PostgreSQL** on port 5432
- **Fee Service** on port 9080
- **Restate Server** on port 8080 (ingress) and 9070 (UI)
- **Registration** container that auto-registers the service with Restate

### API Endpoints

All requests go through Restate ingress at `http://localhost:8080`.

#### Process a fee

```bash
curl -X POST http://localhost:8080/FeeWorkflow/txn_001/run \
  -H 'content-type: application/json' \
  -d '{
    "transactionId": "txn_001",
    "amount": 1000,
    "asset": "USD",
    "assetType": "FIAT",
    "type": "Mobile Top Up",
    "state": "SETTLED - PENDING FEE",
    "createdAt": "2023-08-30 15:42:17.610059"
  }'
```

Response:
```json
{
  "transactionId": "txn_001",
  "amount": 1000.0,
  "asset": "USD",
  "type": "Mobile Top Up",
  "fee": 1.5,
  "rate": 0.0015,
  "description": "Standard fee rate of 0.15%"
}
```

#### Check workflow status

```bash
curl -X POST http://localhost:8080/FeeWorkflow/txn_001/getStatus
```

#### Query fee by transaction ID _(extra)_

> Not required by the spec — added as a bonus feature via `FeeService`.

```bash
curl -X POST http://localhost:8080/FeeService/byTransactionId \
  -H 'content-type: application/json' \
  -d '"txn_001"'
```

#### Query all fees _(extra)_

```bash
curl -X POST http://localhost:8080/FeeService/allFees
```

## Testing

```bash
./gradlew test
```

### Test Structure

| Test | Style | What it covers |
|------|-------|----------------|
| `FeeStrategyTest` | Kotest FeatureSpec (BDD) | Fee calculations for all 4 transaction types, strategy resolution, edge cases |
| `FeeRepositoryTest` | Kotest FeatureSpec + Testcontainers | PostgreSQL persistence, idempotent inserts |
| `FeeWorkflowTest` | JUnit 5 + `@RestateTest` | Full workflow execution, fee persistence, query service integration |

Repository and workflow tests require Docker (Testcontainers).

## Design Decisions

**Why `@Workflow` over `@Service`** — The `@Workflow` annotation ensures each transaction ID is processed exactly once. Re-submitting the same transaction returns the original result without re-execution. This provides built-in idempotency at the Restate level.

**Why Strategy Pattern** — New transaction types can be added by implementing `FeeStrategy` and registering it in `FeeStrategyResolver`, without modifying existing code (Open/Closed principle).

**Why PostgreSQL** — Production-realistic persistence. A unique index on `transaction_id` enforces idempotency at the database level as a second safety net.

## Deployment

The service is deployed at **https://api.cashi.aistithy.com**.
Restate UI is available at **https://restate.cashi.aistithy.com**.

#### Process a fee

```bash
curl -X POST https://api.cashi.aistithy.com/FeeWorkflow/txn_001/run \
  -H 'content-type: application/json' \
  -d '{
    "transactionId": "txn_001",
    "amount": 1000,
    "asset": "USD",
    "assetType": "FIAT",
    "type": "Mobile Top Up",
    "state": "SETTLED - PENDING FEE"
  }'
```

#### Check workflow status

```bash
curl -X POST https://api.cashi.aistithy.com/FeeWorkflow/txn_001/getStatus
```

#### Query fee by transaction ID _(extra)_

```bash
curl -X POST https://api.cashi.aistithy.com/FeeService/byTransactionId \
  -H 'content-type: application/json' \
  -d '"txn_001"'
```

#### Query all fees _(extra)_

```bash
curl -X POST https://api.cashi.aistithy.com/FeeService/allFees
```

## Local Development (without Docker)

```bash
# Terminal 1: Start PostgreSQL
docker run -p 5432:5432 -e POSTGRES_DB=feeservice -e POSTGRES_USER=feeservice -e POSTGRES_PASSWORD=feeservice postgres:16-alpine

# Terminal 2: Start the service
./gradlew run

# Terminal 3: Start Restate
docker run --name restate_dev --rm -p 8080:8080 -p 9070:9070 --add-host=host.docker.internal:host-gateway docker.restate.dev/restatedev/restate:latest

# Terminal 4: Register the service
docker run --rm docker.restate.dev/restatedev/restate-cli:latest deployments register http://host.docker.internal:9080
```
