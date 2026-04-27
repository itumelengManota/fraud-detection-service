# CLAUDE.md — Fraud Detection Service

> This file is auto-read by Claude Code at the start of every session. It provides the authoritative architectural and testing context for the Fraud Detection Service.

---

## 1. Overview

**Repository:** `~/git/fraud-detection-service`
**Owner:** Twenty9ine (Pty) Ltd
**Author:** Ignatius Itumeleng Manota
**Language / runtime:** Java 25
**Framework:** Spring Boot 4 (Spring 7)
**Build tool:** Gradle (Groovy DSL)
**Architecture style:** Hexagonal (Ports and Adapters) + Domain-Driven Design
**Primary purpose:** Real-time transaction fraud scoring (target: 100k TPS peak, sub-100ms p99 decision latency).

This is a **single-service, single-bounded-context** implementation. It is the reference codebase for conventions used across the Twenty9ine Java portfolio (including GovSight Java services).

---

## 2. Implemented Use Cases

Exactly four use cases are implemented today. They are specified in full Cockburn format in `docs/fraud-detection-service_use-case-spec_v2.docx`.

| ID | Use Case | Inbound Port | Entry Point |
|----|----------|--------------|-------------|
| UC-01 | Process Transaction | `ProcessTransactionUseCase` | Kafka consumer on `transactions.normalized` |
| UC-02 | Assess Transaction Risk | `AssessTransactionRiskUseCase` | `POST /fraud/assessments` (and internal from UC-01) |
| UC-03 | Get Risk Assessment | `GetRiskAssessmentUseCase` | `GET /fraud/assessments/{transactionId}` |
| UC-04 | Find Risk-Leveled Assessments | `FindRiskLeveledAssessmentsUseCase` | `GET /fraud/assessments?...` |

Planned but not yet implemented: **UC-05 Configure Fraud Rules** (Drools rule artifacts are currently classpath-loaded at startup).

---

## 3. Package Structure (hexagonal)

```
com.twenty9ine.frauddetection
├── application/
│   ├── dto/                          # Boundary DTOs (RiskAssessmentDto, PagedResultDto, LocationDto)
│   ├── port/
│   │   ├── in/                       # Inbound ports (use case interfaces)
│   │   │   ├── AssessTransactionRiskUseCase.java
│   │   │   ├── ProcessTransactionUseCase.java
│   │   │   ├── GetRiskAssessmentUseCase.java
│   │   │   ├── FindRiskLeveledAssessmentsUseCase.java
│   │   │   ├── command/              # Commands (mutating)
│   │   │   └── query/                # Queries (reading)
│   │   └── out/                      # Outbound ports
│   │       ├── MLServicePort.java
│   │       ├── VelocityServicePort.java
│   │       ├── AccountServicePort.java
│   │       ├── EventPublisherPort.java
│   │       ├── TransactionRepository.java
│   │       └── RiskAssessmentRepository.java
│   └── service/                      # Application services (orchestrators)
│       ├── FraudDetectionApplicationService.java
│       └── ProcessTransactionApplicationService.java
├── domain/
│   ├── aggregate/                    # RiskAssessment (aggregate root)
│   ├── service/                      # Pure domain services
│   │   ├── RiskScoringService.java
│   │   ├── DecisionService.java
│   │   ├── {Low,Medium,High,Critical}RiskStrategy.java
│   │   ├── RuleEngineService.java
│   │   └── GeographicValidator.java
│   ├── event/                        # Domain events (RiskAssessmentCompleted, HighRiskDetected)
│   ├── exception/                    # Domain exceptions
│   └── valueobject/                  # Transaction, Money, RiskScore, Decision, etc.
└── infrastructure/
    ├── adapter/
    │   ├── rest/                     # FraudDetectionController, GlobalExceptionHandler
    │   ├── kafka/                    # TransactionEventConsumer, EventPublisherAdapter
    │   ├── persistence/              # Spring Data JDBC repositories + entities + mappers
    │   ├── cache/                    # VelocityCounterAdapter (Redis)
    │   ├── ml/                       # SageMakerMLAdapter
    │   └── account/                  # AccountServiceRestAdapter
    ├── config/                       # Spring configuration
    └── exception/                    # Infrastructure exceptions
```

**Invariants that any change to this codebase must preserve:**

1. **Dependency direction.** `domain/` depends on nothing in `application/` or `infrastructure/`. `application/` depends only on `domain/`. `infrastructure/` implements `application/port/out/` and drives `application/port/in/`.
2. **Risk score banding.** LOW = 0–40, MEDIUM = 41–70, HIGH = 71–90, CRITICAL = 91–100. Enforced in `RiskAssessment.determineRiskLevel`.
3. **Decision mapping (via strategies).** LOW → ALLOW, MEDIUM → CHALLENGE, HIGH → REVIEW, CRITICAL → BLOCK.
4. **Aggregate invariants.** CRITICAL MUST result in BLOCK; LOW MUST NOT result in BLOCK. Enforced in `RiskAssessment.validateDecisionAlignment`.
5. **Scoring weight invariant.** `mlWeight + ruleWeight == 1.0` at construction of `RiskScoringService`. If the ML service is null, the service automatically degrades to `mlWeight=0.0, ruleWeight=1.0`.
6. **Impossible-travel threshold.** Required speed > 965 km/h between consecutive transactions for the same account triggers `IMPOSSIBLE_TRAVEL`.

---

## 4. Technology Stack

**Backend runtime**
- Java 25, Spring Boot 4.x, Spring Framework 7
- Spring Security (OAuth2 resource server, JWT)
- Spring Data JDBC (no JPA; repositories + entities + explicit mappers)
- Spring Kafka + Apache Kafka client
- Apache Avro (via Apicurio Registry serdes)
- Drools / KIE (rules engine)
- Resilience4j (circuit breaker, retry, time-limiter, bulkhead)
- AWS SDK v2 (SageMaker Runtime)
- Lettuce (Redis client)
- Flyway (database migrations)
- Lombok

**Testing (existing)**
- JUnit 5, AssertJ, Mockito
- Testcontainers (PostgreSQL, Kafka, Redis, Keycloak, Apicurio)
- Shared `AbstractIntegrationTest` base class
- Parallel test execution

**Infrastructure (docker-compose/compose.yml)**
- PostgreSQL 16 (5432) — databases `fraud_detection`, `keycloak`
- Redis 7.4 (6379)
- Kafka 3.8.1 (9092) + Kafka UI (8083)
- Apicurio Registry 3.0.6 (8081) + UI (8082)
- Keycloak 26.5 (8180) — realm `fraud-detection`
- Mockoon CLI (3001) — mocks external Account Service
- SageMaker local model container (8080)
- Grafana LGTM stack (3000; OTLP on 4317/4318)

**Application port:** 9001

---

## 5. Keycloak Realm and Users

**Realm:** `fraud-detection`
**Admin console:** `http://localhost:8180` (admin / admin)
**Client:** `fraud-detection-client` (confidential)
**Resource server audiences:** `fraud-detection-service`, `fraud-detection-web`, `account`

**Test users (preconfigured):**

| Username | Password | Intended role |
|----------|----------|---------------|
| detector | detector123 | Can invoke POST /fraud/assessments |
| analyst | analyst123 | Can read GET /fraud/assessments/... |
| admin | admin123 | Superuser |

Use `./scripts/get-token.sh <username>` to obtain a JWT. The token is cached at `/tmp/fraud-detection-token.txt`.

---

## 6. Kafka Topics

| Topic | Role |
|-------|------|
| `transactions.normalized` | Inbound (UC-01 trigger). Consumed by `TransactionEventConsumer`. |
| `fraud-detection.risk-assessments` | Outbound — `RiskAssessmentCompleted` events (one per processed transaction). |
| `fraud-detection.high-risk-alerts` | Outbound — `HighRiskDetected` events (only when `hasHighRisk()`, i.e. HIGH or CRITICAL). |
| `fraud-detection.domain-events` | Outbound — fallback for domain event types not matched by the switch in `EventPublisherAdapter` (no events route here today). |

Serialization: Avro via Apicurio Registry (ccompat at `http://localhost:8081/apis/ccompat/v7`).

---

## 7. Key Endpoints

| Method | Path | Use Case |
|--------|------|----------|
| POST | `/fraud/assessments` | UC-02 |
| GET | `/fraud/assessments/{transactionId}` | UC-03 |
| GET | `/fraud/assessments` | UC-04 |
| GET | `/actuator/health` | Liveness/readiness |
| GET | `/actuator/**` | Introspection (metrics, flyway, caches, env, loggers, heapdump) |
| GET | `/v3/api-docs` | OpenAPI 3 spec |
| GET | `/swagger-ui.html` | Swagger UI (OAuth2 PKCE) |

---

## 8. Running the Service

```bash
# Bring up infrastructure
docker compose -f docker-compose/compose.yml up -d

# Wait ~30s for Keycloak to finish realm import, then run the service
./gradlew bootRun

# Or build and run the jar
./gradlew clean build
java -jar build/libs/fraud-detection-service-*.jar
```

Verify: `curl http://localhost:9001/actuator/health` should return `{"status":"UP"}`.

---

## 9. Testing Workflow (Claude Code)

Testing is executed **session-per-tier on a dedicated branch** — the same pattern used for GovSight.

**Testing branch:**
```bash
git checkout -b testing/e2e-validation
```

**Authoritative documents:**
- `docs/FraudDetection_UseCases_Testing_Plan_v1.1.docx` — full test plan (7 tiers, ~45 test cases, each mapped to MCP servers)
- `TESTING.md` — living progress tracker updated after every session
- `.claude/MCP_SETUP.md` — MCP server install and verification

**Before every testing session:**
```bash
./configure-session.sh             # brings up infra, obtains token, checks MCP prerequisites
```

**Starting a tier session (example — Tier 3):**
```bash
claude -n "test-uc02-assess-risk" --model claude-sonnet-4-6
> Test Tier 3 (UC-02 Assess Transaction Risk) following TESTING.md and FraudDetection_UseCases_Testing_Plan_v1.1.docx.
> Use the spring-boot MCP server to introspect beans, endpoints, and actuator metrics.
> Use the postgres MCP server to verify persisted RiskAssessments.
> Use the kafka MCP server to verify published domain events.
> Record each test case outcome in TESTING.md.
```

---

## 10. MCP Servers (Testing Stack — 7 servers)

See `.mcp.json` (project root) and `.claude/MCP_SETUP.md` for detailed install/verify instructions.

| # | Server | Role | Coverage |
|---|--------|------|----------|
| 1 | postgres (crystaldba/postgres-mcp) | Query `fraud_detection` DB — verify RiskAssessment persistence, inspect Flyway schema | Data |
| 2 | spring-boot (custom, 86 tools) | Actuator introspection (beans, health, metrics, flyway, loggers, HTTP any URL), project file read, **and Kafka tools** (`kafka_topics`, `kafka_produce`, `kafka_consume`, `kafka_consumer_groups`) — schema-aware via Apicurio | Backend + Integration |
| 3 | docker (ofershap/mcp-server-docker) | Inspect/start/stop specific allowed containers; logs | Infrastructure |
| 4 | keycloak (keycloak-mcp) | Verify realm, users, client scopes; issue tokens programmatically | Identity |
| 5 | grafana (mcp-grafana) | Query metrics (Prometheus), traces (Tempo), logs (Loki) via OTLP | Observability |
| 6 | github (GitHub Copilot MCP) | Raise / close / track bug issues for the testing branch; **committer is the owner of the configured PAT** (Ignatius Itumeleng Manota) | Workflow |
| 7 | rest-api (dkmaker-mcp-rest-api) | Fallback authenticated HTTP calls to `http://localhost:9001` | Utility |

**No standalone Kafka MCP server.** GovSight's BUG-015 documented that `tuannvm/kafka-mcp-server` hung mid-session during Tier 3 testing and was permanently removed in favour of spring-boot-mcp-server's built-in, schema-aware Kafka tools. The fraud-detection-service uses the exact same Avro + Apicurio stack, so the same decision applies here — `spring-boot`'s Kafka tools auto-register whenever it's pointed at this service's project directory (spring-kafka is on the classpath), and Apicurio serialisation/deserialisation works out of the box.

**Out of scope** (present in GovSight stack, intentionally excluded here):
- `fastapi-mcp-server` — no Python service.
- `dart` (Flutter MCP) — no Flutter UI.
- `next-devtools-mcp` — no Next.js UI.
- `playwright-mcp` — no browser-driven UI.

---

## 11. Session Conventions

**Branch discipline.** All testing work happens on `testing/e2e-validation`. Bug fixes are committed to that branch. Only merge to `main` after all 7 tiers pass.

**Commit messages.** Conventional commits: `test: ...`, `fix: ...`, `docs: ...`, `chore: ...`.

**Build hooks (`.claude/settings.json`).** On `.java` edits, Claude Code runs `./gradlew compileJava` to catch compile errors immediately. `rm -rf` and similar destructive commands are blocked.

**Filesystem scope for Claude.** The Filesystem MCP is assumed pointed at `~/git/fraud-detection-service` (and sibling projects `~/git/spring-boot-mcp-server`). Never touch system directories.

---

## 12. Known Lessons (do not re-learn)

- **`bash_tool` vs. user filesystem.** `bash_tool` runs on Claude's Linux container, not Ignatius's Mac. Never use `bash` to operate on `/Users/...` paths — always use `Filesystem:*` tools.
- **Java 25 + AOT.** Leave the existing AOT-disabling settings alone; they were added deliberately. Do not re-enable processAot without a matching compatibility plan.
- **SageMaker circuit breaker.** `sagemakerML` circuit breaker is intentionally aggressive (50% failure rate, 100ms slow-call threshold). If tests generate ML fallback, that is expected — the service degrades to rule-only scoring.
- **`uv` for Python tooling.** Any Python helper invoked from this service's ecosystem (e.g., validation scripts) uses `uv` / `uvx`. No `pip` directly.

---

**Document owner:** Ignatius Itumeleng Manota • **Confidential** • Twenty9ine (Pty) Ltd
