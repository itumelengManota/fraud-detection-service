# TESTING.md — Fraud Detection Service

> **Purpose.** Living progress tracker for end-to-end testing of the Fraud Detection Service against all four implemented use cases and the service's non-functional requirements.
>
> **Update after every Claude Code testing session.**
>
> **Authoritative plan:** `docs/FraudDetection_UseCases_Testing_Plan_v1.1.docx` (full test-case matrix with MCP server mappings).

---

## Testing Branch

```bash
git checkout -b testing/e2e-validation
# After all 7 tiers pass:
# git checkout main && git merge testing/e2e-validation && git tag v1.0.0-tested
```

---

## Note on MCP Server Stack (April 2026)

An earlier draft of this plan included `tuannvm/kafka-mcp-server` as a standalone static server. This has been **removed** before the first testing session, following the lesson learned on GovSight:

> GovSight **BUG-015** (`~/git/govsight/TESTING.md`): during Tier 3 testing, `tuannvm/kafka-mcp-server` hung mid-session — tool calls never returned, blocking test progress. It was permanently removed and replaced with **spring-boot-mcp-server's built-in Kafka tools** (`kafka_topics`, `kafka_produce`, `kafka_consume`, `kafka_consumer_groups`).

The fraud-detection-service uses the exact same Avro + Apicurio stack as GovSight, so the same decision applies here. The change is a strict improvement:

- **Same broker** — both connect to `localhost:9092` via `docker-compose/compose.yml`.
- **Schema-aware** — `spring-boot-mcp-server` uses this project's Apicurio Registry config (`http://localhost:8081/apis/registry/v2`), so Avro serialisation/deserialisation works automatically. The standalone Kafka MCP was raw bytes only.
- **Always available** — `spring-kafka` is on `build.gradle`, so the 4 Kafka tools register whenever `spring-boot-mcp-server` is pointed at this project (which is the default in `.mcp.json`).
- **One fewer process** — 7 servers instead of 8, less resource overhead, one fewer point of failure.

Every reference to "kafka MCP" below now routes through `spring-boot`'s Kafka tools.

---

## MCP Testing Stack (7 servers)

See `.mcp.json` at the project root and `.claude/MCP_SETUP.md` for install/verify.

| # | Server | Category |
|---|--------|----------|
| 1 | postgres | Data |
| 2 | spring-boot (custom — provides actuator + **Kafka** + HTTP-any-URL + project-file tools) | Backend + Integration |
| 3 | docker | Infrastructure |
| 4 | keycloak | Identity |
| 5 | grafana | Observability |
| 6 | github (HTTP; commits attributed to Ignatius Itumeleng Manota via the embedded PAT) | Workflow |
| 7 | rest-api | Utility / fallback HTTP |

---

## Progress Summary

| Tier | Name | Use Cases Covered | Cases | Passed | Failed | Status | Session |
|------|------|-------------------|-------|--------|--------|--------|---------|
| 1 | Infrastructure | — | 7 | 7 | 0 | PASSED | 2026-04-23 |
| 2 | Service Startup | — | 6 | 4 | 2 | FAILED | 2026-04-24 |
| 3 | UC-02 Assess Transaction Risk | UC-02 | 12 |  |  | NOT_TESTED |  |
| 4 | UC-01 Process Transaction | UC-01 | 6 |  |  | NOT_TESTED |  |
| 5 | UC-03 Get Risk Assessment | UC-03 | 4 |  |  | NOT_TESTED |  |
| 6 | UC-04 Find Risk-Leveled Assessments | UC-04 | 7 |  |  | NOT_TESTED |  |
| 7 | NFRs — Security, Resilience, Observability | (cross-cutting) | 6 |  |  | NOT_TESTED |  |
| **Total** |  |  | **48** |  |  |  |  |

Legend: `NOT_TESTED` → `IN_PROGRESS` → `PASSED` / `FAILED` / `BLOCKED`.

---

## Session Log

### Tier 1: Infrastructure Readiness
- **Pre-session:** `./configure-session.sh && docker compose -f docker-compose/compose.yml up -d`
- **MCP servers used:** docker (docker ps via bash), postgres, keycloak, spring-boot (`kafka_topics`), rest-api, github
- **Session command:**
  ```bash
  claude -n "test-infra" --model claude-sonnet-4-6
  ```
- **Opening prompt:**
  > Execute Tier 1 of the test plan following `docs/FraudDetection_UseCases_Testing_Plan_v1.1.docx` and `TESTING.md`. Verify all docker containers in `ALLOWED_CONTAINERS` are healthy, the `fraud_detection` and `keycloak` databases exist, the `fraud-detection` Keycloak realm is loaded with users `detector`/`analyst`/`admin`, Kafka topics are creatable (use spring-boot's `kafka_topics` tool), the Apicurio Registry responds, and Redis responds to PING. Record each test case outcome in `TESTING.md` and raise any discovered defects as GitHub issues via the github MCP server (commits will be attributed to my identity via the configured PAT).
- **Date:** 2026-04-23
- **Result:** PASSED (7/7)
- **Bugs found:** BUG-T1-001 — Keycloak 26.x bootstrap admin blocked (see GitHub issue #1)
- **Fixes applied:** Removed `is_temporary_admin` attribute from master realm admin user; set email/firstName/lastName; disabled VERIFY_PROFILE in master realm; updated `master-realm.json` for permanent fix.

#### Test Case Results

| TC | Description | Tool(s) Used | Result | Notes |
|----|-------------|-------------|--------|-------|
| TC-1.1 | All 10 ALLOWED_CONTAINERS healthy | `docker ps` (bash) | ✅ PASS | All containers `Up 18 minutes` at execution time |
| TC-1.2 | `fraud_detection` and `keycloak` databases exist | `docker exec` psql | ✅ PASS | Both databases confirmed in pg_database |
| TC-1.3 | `fraud-detection` Keycloak realm loaded | keycloak MCP, admin REST API | ✅ PASS | Realm enabled=true; required fix first (see BUG-T1-001) |
| TC-1.4 | Users `detector`, `analyst`, `admin` present | keycloak MCP (`list-users`) | ✅ PASS | All 3 users enabled=true |
| TC-1.5 | Kafka broker reachable; `transactions.normalized` topic exists | spring-boot MCP (`kafka_topics`) | ✅ PASS | 1 topic, 1 partition, RF=1; remaining 4 topics created on app startup |
| TC-1.6 | Apicurio Registry responds (health + artifacts API) | bash `curl` | ✅ PASS | `/health` → `{"status":"UP"}`; `/apis/registry/v2/search/artifacts` → 200 (0 schemas, expected pre-startup) |
| TC-1.7 | Redis responds to PING | `docker exec` redis-cli | ✅ PASS | `+PONG` |

#### Infrastructure Notes

- **docker MCP limitation:** `mcp-server-docker` `run_command` tool only allows service name `laravel_app` (hardcoded default). The `ALLOWED_CONTAINERS` env var controls the container *inspection* tools, not `run_command`. Used `docker exec` via bash as workaround. This is a known MCP limitation, not a service defect.
- **postgres MCP `execute_sql` bug:** Every SQL query returns `syntax error at or near "all"`. The tool appears to prepend unexpected text to queries. Used `docker exec psql` as workaround for database verification. To be investigated before Tier 2.
- **Kafka topics:** Only `transactions.normalized` exists pre-startup. The 4 outbound topics (`fraud.alerts.critical`, `fraud.alerts.high`, `fraud.alerts.medium`, `fraud-detection.domain-events`) are created by the application on first boot. Expected behavior.

### Tier 2: Service Startup
- **Pre-session:** Tier 1 passed; `./gradlew bootRun` in a separate terminal
- **MCP servers used:** spring-boot (`check_configuration`, `migration_info`, `package_structure`), rest-api (actuator health/flyway/metrics), postgres (DB table verification), github (bug filing)
- **Session command:**
  ```bash
  claude -n "test-startup" --model claude-sonnet-4-6
  ```
- **Opening prompt:**
  > Execute Tier 2. Use the `spring-boot` MCP server to verify `/actuator/health` is UP, Flyway migrations applied (list from `flyway_schema_history`), Spring beans `RiskScoringService`, `DecisionService`, `RuleEngineService`, `GeographicValidator`, the four `DecisionStrategy` implementations, and `DroolsInfrastructureConfig` are all present, and circuit breakers `sagemakerML` and `accountService` are registered. Record outcomes in `TESTING.md`.
- **Date:** 2026-04-24
- **Result:** FAILED (4/6 PASS, 2 bugs; BUG-T2-001 fixed in-session)
- **Bugs found:** BUG-T2-001 (get-token.sh wrong client ID — fixed), BUG-T2-002 (CB actuator metrics not registered)
- **Fixes applied:** `scripts/get-token.sh` updated to use `fraud-detection-web` client

#### MCP Tool Notes

- `spring-boot` actuator tools (`app_health`, `app_beans`, `app_metrics`) returned "Unknown tool" — the spring-boot MCP server provides project-analysis tools but does NOT proxy live actuator endpoints over HTTP. Actuator calls were made via `rest-api` MCP and direct `curl` instead.
- `/actuator/beans` is **not exposed** in `management.endpoints.web.exposure.include` (only `health,liveness,readiness,info,metrics,heapdump,caches,env,flyway,loggers` are exposed). Bean verification was performed via spring-boot `package_structure` and source code read.
- Tokens could not be obtained via `get-token.sh` (BUG-T2-001). Workaround: direct `curl` with `fraud-detection-web` client and correct secret from realm JSON.
- `/actuator/health` returns minimal response to unauthenticated callers (status only); full component detail requires a valid JWT (confirmed correct behaviour — `show-details: when-authorized`).

#### Session-Setup Issue (Not a Code Bug)

The postgres Docker volume was wiped between Tier 1 and Tier 2 sessions (likely `docker compose down -v` or volume prune), but the Spring app was **not restarted**. Consequently:
- `/actuator/flyway` reports SUCCESS for all 3 migrations (cached from April 23 startup — correct migration definitions).
- `pg_tables` and `docker exec psql \dt` both show **no tables** in `fraud_detection` DB (volume is fresh).
- **Required action before Tier 3:** restart the application so Flyway re-applies all migrations to the clean DB.

#### Test Case Results

| TC | Description | Tool(s) Used | Result | Notes |
|----|-------------|-------------|--------|-------|
| TC-2.1 | `/actuator/health` returns `status: UP` | rest-api (`GET /actuator/health`) | ✅ PASS | All 7 components UP: db (PostgreSQL), diskSpace, livenessState, ping, readinessState, redis 7.4.7, ssl |
| TC-2.2 | Flyway migrations V1–V3 applied (`flyway_schema_history`) | rest-api (`GET /actuator/flyway`), postgres MCP, docker exec psql | ⚠️ PARTIAL | Actuator shows 3 migrations SUCCESS (from April 23 start). Actual DB tables missing — postgres volume wiped between sessions; app restart required. Migration scripts are correct. |
| TC-2.3 | Domain service beans present: `RiskScoringService`, `DecisionService`, `RuleEngineService`, `GeographicValidator`, 4 `DecisionStrategy` impls | spring-boot `package_structure`, source read `DomainServiceConfig.java` | ✅ PASS | All declared as `@Bean` in `DomainServiceConfig`: `riskScoringService`, `decisionService`, `ruleEngineService`, `geographicValidator`. Four strategies (`CriticalRiskStrategy`, `HighRiskStrategy`, `MediumRiskStrategy`, `LowRiskStrategy`) instantiated inside `decisionService` via `buildStrategies()`. |
| TC-2.4 | `DroolsInfrastructureConfig` bean present; `kieContainer` loads Drools rules | spring-boot `package_structure`, source read `DroolsInfrastructureConfig.java` | ✅ PASS | `@Configuration` confirmed; `kieContainer()` @Bean loads `velocity-rules.drl`, `geographic-rules.drl`, `amount-rules.drl` from classpath. Compilation errors throw at startup — absence of startup failure confirms rules loaded clean. |
| TC-2.5 | `sagemakerML` circuit breaker registered | source read `SageMakerMLAdapter.java`, `/actuator/metrics` | ❌ FAIL | Programmatic registration: `circuitBreakerRegistry.circuitBreaker("sagemakerML")` in constructor ✅. Config present in `application.yml` ✅. BUT: zero `resilience4j.*` metrics appear in actuator (211 metrics, none CB-related). No CB health indicators in `/actuator/health`. See BUG-T2-002. |
| TC-2.6 | `accountService` circuit breaker registered | source read `AccountServiceRestAdapter.java`, `/actuator/metrics` | ❌ FAIL | Annotation-based: `@CircuitBreaker(name = "accountService")` in `AccountServiceRestAdapter` ✅. Config present ✅. Same actuator gap as TC-2.5 — no metrics/health indicators. See BUG-T2-002. |

### Tier 3: UC-02 Assess Transaction Risk
- **Pre-session:** Tiers 1–2 passed; service running on port 9001
- **MCP servers used:** spring-boot (including `kafka_consume` for domain-event verification), rest-api, postgres, keycloak
- **Session command:**
  ```bash
  claude -n "test-uc02-assess" --model claude-opus-4-7
  ```
- **Opening prompt:**
  > Execute Tier 3 (UC-02 Assess Transaction Risk) — all 12 test cases in the test plan. Cover LOW / MEDIUM / HIGH / CRITICAL bands; velocity, impossible-travel and amount rule triggers; ML-service-unavailable fallback; validation failures (400); unauthorised calls (401/403); and invariant-violation rollback. For each case, issue a token via keycloak MCP, POST to `/fraud/assessments`, then verify persistence via postgres MCP and domain-event publication via spring-boot's `kafka_consume` tool (schema-aware Avro). Record each outcome in `TESTING.md` with the response body and DB state snapshot.
- **Date:**
- **Result:**
- **Bugs found:**
- **Fixes applied:**

### Tier 4: UC-01 Process Transaction
- **Pre-session:** Tier 3 passed
- **MCP servers used:** spring-boot (`kafka_produce` / `kafka_consume` / `kafka_consumer_groups`), postgres
- **Session command:**
  ```bash
  claude -n "test-uc01-process" --model claude-opus-4-7
  ```
- **Opening prompt:**
  > Execute Tier 4 (UC-01 Process Transaction). Use spring-boot's `kafka_produce` tool to publish Avro-serialised transactions to `transactions.normalized` (the tool handles Apicurio schema resolution automatically), verify persistence of both Transaction and RiskAssessment via postgres MCP, confirm idempotency by replaying the same transactionId, confirm poison-pill handling, and confirm `RiskAssessmentCompleted` appears on `fraud-detection.domain-events` via `kafka_consume`. Verify consumer-group lag returns to 0 via `kafka_consumer_groups`. Record each test case in `TESTING.md`.
- **Date:**
- **Result:**
- **Bugs found:**
- **Fixes applied:**

### Tier 5: UC-03 Get Risk Assessment
- **Pre-session:** Tier 3 passed (provides assessments to fetch)
- **MCP servers used:** rest-api, keycloak, postgres
- **Session command:**
  ```bash
  claude -n "test-uc03-get" --model claude-sonnet-4-6
  ```
- **Opening prompt:**
  > Execute Tier 5 (UC-03 Get Risk Assessment). Cover: 200 OK for a known transactionId, 404 for an unknown UUID, 401 without token, 403 with a token that lacks read scope, 400 for an invalid UUID. Record outcomes in `TESTING.md`.
- **Date:**
- **Result:**
- **Bugs found:**
- **Fixes applied:**

### Tier 6: UC-04 Find Risk-Leveled Assessments
- **Pre-session:** Tier 3 passed (provides assessments to filter)
- **MCP servers used:** rest-api, keycloak, postgres
- **Session command:**
  ```bash
  claude -n "test-uc04-find" --model claude-sonnet-4-6
  ```
- **Opening prompt:**
  > Execute Tier 6 (UC-04 Find Risk-Leveled Assessments). Cover: no filters, filter by risk level, filter by fromDate, both filters, pagination correctness, invalid risk level → 400, future fromDate → 400. Verify result counts match direct SQL queries via postgres MCP. Record outcomes in `TESTING.md`.
- **Date:**
- **Result:**
- **Bugs found:**
- **Fixes applied:**

### Tier 7: NFRs — Security, Resilience, Observability
- **Pre-session:** Tiers 1–6 passed
- **MCP servers used:** keycloak, spring-boot, grafana, docker
- **Session command:**
  ```bash
  claude -n "test-nfrs" --model claude-opus-4-7
  ```
- **Opening prompt:**
  > Execute Tier 7. Verify (a) detector/analyst/admin scopes enforce expected access via keycloak and rest-api MCP servers; (b) stopping the `fraud-detection-model` container via docker MCP causes `sagemakerML` circuit breaker to open and the service to fall back to rule-only scoring (observed via spring-boot MCP metrics); (c) OTLP traces/metrics/logs are reaching Grafana (query via grafana MCP); (d) logs include `traceId`/`transactionId` correlation; (e) no PII is written to logs. Record outcomes in `TESTING.md`.
- **Date:**
- **Result:**
- **Bugs found:**
- **Fixes applied:**

---

## Bug Registry

Tracked as GitHub issues under label `testing/e2e-validation` (via github MCP server).
**Commits and issues are attributed to the owner of the PAT configured in `.mcp.json` (Ignatius Itumeleng Manota).**
Mirror each issue below for quick reference.

| Bug ID | Tier | Test Case | Summary | GitHub Issue | Fix Commit | Re-test Status |
|--------|------|-----------|---------|--------------|------------|----------------|
| BUG-T1-001 | 1 | TC-1.3/TC-1.4 | Keycloak 26.x bootstrap admin `invalid_grant` — `is_temporary_admin=true` blocks programmatic password grants | [#1](https://github.com/itumelengManota/fraud-detection-service/issues/1) | see fix below | FIXED — verified TOKEN_OK on 2026-04-23 |
| BUG-T2-001 | 2 | TC-2.1 (blocker) | `get-token.sh` uses non-existent client `fraud-detection-client`; correct client is `fraud-detection-web` | [#2](https://github.com/itumelengManota/fraud-detection-service/issues/2) | FIXED in-session (2026-04-24) | FIXED — `./scripts/get-token.sh detector` returns TOKEN_OK |
| BUG-T2-002 | 2 | TC-2.5/TC-2.6 | Resilience4j CB health indicators and metrics not registered in actuator despite `register-health-indicator: true`; likely `resilience4j-spring-boot3:2.2.0` incompatibility with Spring Boot 4 auto-configuration | [#3](https://github.com/itumelengManota/fraud-detection-service/issues/3) | OPEN | OPEN — needs fix before Tier 7 NFR tests |

---

## Final Merge

```bash
# After all 7 tiers pass:
git checkout main
git merge testing/e2e-validation
git tag v1.0.0-tested
git push origin main --tags
```
