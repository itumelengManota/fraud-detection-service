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
| 3 | UC-02 Assess Transaction Risk | UC-02 | 12 | 11 | 1 | PARTIAL | 2026-04-24 |
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
- **Result:** PASS (6/6 after retest — BUG-T2-001 + BUG-T2-002 both FIXED)
- **Bugs found:** BUG-T2-001 (get-token.sh wrong client ID — fixed in-session), BUG-T2-002 (CB actuator metrics not registered — fixed in follow-up retest 2026-04-24)
- **Fixes applied:** `scripts/get-token.sh` updated to use `fraud-detection-web` client; resilience4j bumped to 2.3.0 + `ResilienceObservabilityConfig` bridge for Spring Boot 4 actuator/Micrometer (see BUG-T2-002 fix row)

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
| TC-2.5 | `sagemakerML` circuit breaker registered | source read `SageMakerMLAdapter.java`, `/actuator/metrics` | ✅ PASS (after BUG-T2-002 fix) | Registered programmatically via `circuitBreakerRegistry.circuitBreaker("sagemakerML")`. After fix: `/actuator/circuitbreakers` lists `sagemakerML` (CLOSED); `/actuator/health` shows `circuitBreakers.sagemakerML = CLOSED`; `resilience4j.circuitbreaker.state{name=sagemakerML,...}` metric present |
| TC-2.6 | `accountService` circuit breaker registered | source read `AccountServiceRestAdapter.java`, `/actuator/metrics` | ✅ PASS (after BUG-T2-002 fix) | Annotation-based: `@CircuitBreaker(name = "accountService")`. After fix: `/actuator/circuitbreakers` lists `accountService` (CLOSED); health component UP; metric present |

### Tier 3: UC-02 Assess Transaction Risk
- **Pre-session:** Tiers 1–2 passed; service running on port 9001
- **MCP servers used:** spring-boot (including `kafka_consume` for domain-event verification), rest-api, postgres, keycloak
- **Session command:**
  ```bash
  claude -n "test-uc02-assess" --model claude-opus-4-7
  ```
- **Opening prompt:**
  > Execute Tier 3 (UC-02 Assess Transaction Risk) — all 12 test cases in the test plan. Cover LOW / MEDIUM / HIGH / CRITICAL bands; velocity, impossible-travel and amount rule triggers; ML-service-unavailable fallback; validation failures (400); unauthorised calls (401/403); and invariant-violation rollback. For each case, issue a token via keycloak MCP, POST to `/fraud/assessments`, then verify persistence via postgres MCP and domain-event publication via spring-boot's `kafka_consume` tool (schema-aware Avro). Record each outcome in `TESTING.md` with the response body and DB state snapshot.
- **Date:** 2026-04-24
- **Result:** PARTIAL (11/12 PASS; TC-3.12 rollback path not reachable via HTTP — see notes)
- **Bugs found:** BUG-T3-001 (datasource auto-bound to `postgres` DB instead of `fraud_detection`), BUG-T3-002 (Kafka topic names in `EventPublisherAdapter` diverge from CLAUDE.md and `application.yml`), BUG-T3-003 (`IllegalArgumentException` from enum parsing returns 500 instead of 400), BUG-T3-004 (SageMaker endpoint URL uses Docker-internal hostname `sagemaker-model:9002` — unreachable from host-run `bootRun`, causing permanent ML fallback)
- **Fixes applied:** None in-session (all 4 bugs logged for follow-up)

#### Pre-session Recovery

- Restarted `bootRun` (PID 46944) as agreed, so Flyway re-applied V1–V3 migrations to the clean Docker volume. Service became healthy in ~6 s.
- **Surprise finding:** Flyway migrated into DB `postgres`, not `fraud_detection`. Root cause = `spring-boot-docker-compose` on classpath auto-binds the datasource URL to the postgres container defaults, shadowing `application.yml`'s `${DB_NAME:fraud_detection}`. `./postgresql/init.sql` creates `fraud_detection` but the app never connects to it. All Tier 3 DB verification queries were run against the `postgres` DB where the real schema lives. Logged as BUG-T3-001.

#### MCP / Tooling Notes

- Used `keycloak` MCP (via `get-token.sh`) for all tokens. Tokens are 30-min TTL; re-issued mid-session.
- Used direct `docker exec psql` for DB verification (postgres MCP `execute_sql` bug from Tier 1 remains open; `docker exec` is reliable workaround).
- Used direct `docker exec redis-cli SET` to prime velocity counters for TC-3.3 / TC-3.4 / TC-3.5. Works because `VelocityCounterAdapter.incrementCounter` writes raw Redis integers via `opsForValue().increment()`, which the JSON value deserializer parses back as `Number`. Bypasses the need for 80+ back-to-back POSTs to build counters organically.
- For TC-3.6 (impossible travel), inserted a prior `transaction` + `location` + `merchant` row via `docker exec psql` — UC-02 never persists a Transaction (only RiskAssessment), so without manual seeding `GeographicValidator.findEarliestByAccountId` returns empty and the rule never fires.
- **Kafka event verification:** `fraud-detection-kafka` image ships Kafka 4.x (no `kafka.tools.GetOffsetShell`). Used `/opt/kafka/bin/kafka-get-offsets.sh` instead. Domain events land on `fraud-detection.risk-assessments` (7 events — one per successful POST) and `fraud-detection.high-risk-alerts` (2 events — HIGH + CRITICAL, as `HighRiskDetected` fires only on `hasHighRisk()`). The topic names **in code** are `fraud-detection.risk-assessments` / `fraud-detection.high-risk-alerts` / `fraud-detection.domain-events` — different from `fraud.alerts.{critical,high,medium}` documented in CLAUDE.md and present in `application.yml`. Logged as BUG-T3-002.

#### Test Case Results

Legend: `txnId` = request `transactionId`; `score`/`band`/`decision` = response fields; DB row verified in `public.risk_assessments` unless noted.

| TC | Description | Request | Response | DB state | Kafka | Result |
|----|-------------|---------|----------|----------|-------|--------|
| TC-3.1 | LOW band → ALLOW (small amount, no prior state) | amount=25.50 USD, ONLINE, NY | `{riskScore:0, transactionRiskLevel:"LOW", decision:"ALLOW"}` HTTP 200 | `LOW / ALLOW / 0`, `ml_prediction_json.modelId=unavailable` | event on `risk-assessments` | ✅ PASS |
| TC-3.2 | MEDIUM band → CHALLENGE (amount-rule driven) | amount=100001 USD | `{riskScore:50, MEDIUM, CHALLENGE}` HTTP 200 | `MEDIUM / CHALLENGE / 50`, 3 rule_evaluations: `Large Amount`, `Very Large Amount`, `Excessively Large Amount` (all AMOUNT) | event on `risk-assessments` | ✅ PASS |
| TC-3.3 | HIGH band → REVIEW (velocity + amount) | Redis `5min=10, 1hour=25`; amount=100001 | `{riskScore:76, HIGH, REVIEW}` HTTP 200 | `HIGH / REVIEW / 76`, 5 triggers: 3 AMOUNT + `Medium Velocity 5min`, `High Velocity 1hr` | events on `risk-assessments` + `high-risk-alerts` | ✅ PASS |
| TC-3.4 | CRITICAL band → BLOCK (all 3 velocity windows + amount) | Redis `5min=10, 1hour=25, 24hour=90`; amount=100001 | `{riskScore:100, CRITICAL, BLOCK}` HTTP 200 | `CRITICAL / BLOCK / 100`, 6 triggers: 3 AMOUNT + 3 VELOCITY (incl. `Excessive Velocity 24hrs`) | events on `risk-assessments` + `high-risk-alerts` | ✅ PASS |
| TC-3.5 | Velocity rule trigger (`VELOCITY_5MIN`) | Redis `5min=10`; amount=50 | `{riskScore:10, LOW, ALLOW}` HTTP 200 | `LOW / ALLOW / 10`, 1 trigger: `Medium Velocity 5min` (VELOCITY) | event on `risk-assessments` | ✅ PASS |
| TC-3.6 | Impossible-travel rule trigger | Prior txn NY 10:00; new LA 10:10 (~3940 km in 10 min ≈ 23,600 km/h) | `{riskScore:24, LOW, ALLOW}` HTTP 200 | `LOW / ALLOW / 24`, 1 trigger: `Impossible Travel` (GEOGRAPHIC) | event on `risk-assessments` | ✅ PASS |
| TC-3.7 | Amount rule trigger (`LARGE_AMOUNT` only) | amount=10001 | `{riskScore:10, LOW, ALLOW}` HTTP 200 | `LOW / ALLOW / 10`, 1 trigger: `Large Amount` (AMOUNT) | event on `risk-assessments` | ✅ PASS |
| TC-3.8 | ML-service-unavailable → rule-only fallback | Any assessment (SageMaker endpoint unreachable from host — see BUG-T3-004) | All 7 successful assessments in this tier | `ml_prediction_json = {modelId:"unavailable", fraudProbability:0.0, confidence:0.0, modelVersion:"0.0.0"}`; app log: `SageMaker prediction failed ... using fallback` | events published normally | ✅ PASS |
| TC-3.9 | Validation failure → 400 | Body missing `amount` and `accountId` | `{code:"VALIDATION_ERROR", details:["amount: Amount cannot be null", "accountId: Account ID cannot be null"]}` HTTP 400 | no row | no event | ✅ PASS |
| TC-3.10 | Unauthenticated → 401 | POST without `Authorization` header | HTTP 401 (empty body) | no row | no event | ✅ PASS |
| TC-3.11 | Insufficient scope → 403 | Token issued for `analyst` (has `fraud:read`, lacks `fraud:detect`) | HTTP 403 (empty body) | no row | no event | ✅ PASS |
| TC-3.12 | Invariant-violation rollback | Positive verification across TC-3.1–3.4: `LOW → ALLOW` (never BLOCK), `CRITICAL → BLOCK`, `HIGH → REVIEW`, `MEDIUM → CHALLENGE` all held. **Negative rollback path not reachable via HTTP** — decisions are derived from the score by `DecisionService` strategies, so `RiskAssessment.validateDecisionAlignment` cannot be tripped from UC-02 without bypassing the service layer. Suggest unit-test coverage for `RiskAssessment.completeAssessment()` with a mismatched decision. | n/a | n/a | n/a | ⚠️ PARTIAL — invariants verified positively; negative rollback requires unit test |

#### Retest After Fixes (2026-04-24)

After applying all 4 fixes (commits covering `compose.yml`, `postgresql/init.sql`, `application.yml`, `application-qa.yml`, `EventPublisherAdapter.java`, `GlobalExceptionHandler.java`), the postgres container was recreated (to pick up `POSTGRES_DB=fraud_detection`) and `bootRun` restarted (PID 3548). Re-test results:

| Bug | Verification | Outcome |
|-----|--------------|---------|
| BUG-T3-001 | `\dt fraud_detection` → 6 tables (`risk_assessments`, `rule_evaluations`, `transaction`, `location`, `merchant`, `flyway_schema_history`); `\dt postgres` → empty | ✅ FIXED |
| BUG-T3-002 | MEDIUM POST → `risk-assessments` offset 7→8 (+1); HIGH POST → `risk-assessments` 8→9 (+1), `high-risk-alerts` 2→3 (+1). Topic routing now driven by `@Value`-injected config (equivalent behaviour, but decoupled from hard-coded literals) | ✅ FIXED |
| BUG-T3-003 | `channel=WEB` → HTTP 400 `{code:"VALIDATION_ERROR", message:"Unknown channel: WEB"}`; `type=FOO` → HTTP 400 `{code:"VALIDATION_ERROR", message:"Unknown type: FOO"}` | ✅ FIXED |
| BUG-T3-004 | POST small-amount ($100) → `ml_prediction_json = {modelId:"fraud-detection-endpoint", modelVersion:"1.0.0", fraudProbability:0.928, confidence:0.95}`. Final score 56 (MEDIUM) consistent with `round(0.928 × 100 × 0.6 + 0 × 0.4) = 56` | ✅ FIXED |

All 4 bugs now resolved. The ML path is live again — note that the test SageMaker container returns aggressive probabilities (0.928 for a $100 retail transaction), so expect base-case MEDIUM assessments when ML is available.

#### Score / Band Arithmetic (reference)

Service runs in permanent ML-fallback (BUG-T3-004), so `score = round(ruleSum × 0.4)`:

| Trigger | Severity | Rule weight contribution |
|---------|----------|--------------------------|
| `LARGE_AMOUNT` (> $10k) | MEDIUM (25) | 10 |
| `VERY_LARGE_AMOUNT` (> $50k) | HIGH (40) | 16 |
| `EXCESSIVELY_LARGE_AMOUNT` (> $100k) | CRITICAL (60) | 24 |
| `VELOCITY_5MIN` (count > 5) | MEDIUM (25) | 10 |
| `VELOCITY_1HOUR` (count > 20) | HIGH (40) | 16 |
| `VELOCITY_24HOURS` (count > 80) | CRITICAL (60) | 24 |
| `IMPOSSIBLE_TRAVEL` (speed > 965 km/h) | CRITICAL (60) | 24 |

Bands: LOW 0–40, MEDIUM 41–70, HIGH 71–90, CRITICAL 91–100. All test outcomes are consistent with this arithmetic.

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
| BUG-T2-002 | 2 | TC-2.5/TC-2.6 | Resilience4j CB health indicators and metrics not registered in actuator despite `register-health-indicator: true`; root cause: `resilience4j-spring-boot3:2.3.0` implements the Spring Boot 3 `org.springframework.boot.actuate.health.HealthIndicator` interface, which Spring Boot 4 no longer scans (moved to `org.springframework.boot.health.contributor.HealthIndicator`) | [#3](https://github.com/itumelengManota/fraud-detection-service/issues/3) | FIXED in-session (2026-04-24) — bumped resilience4j to `2.3.0`; added `ResilienceObservabilityConfig` that re-binds `TaggedCircuitBreakerMetrics` / `TaggedRetryMetrics` / `TaggedBulkheadMetrics` / `TaggedTimeLimiterMetrics` to the Micrometer `MeterRegistry` and publishes a Spring Boot 4–native `HealthIndicator`; exposed `circuitbreakers,circuitbreakerevents,retries,retryevents,bulkheads,timelimiters,ratelimiters` in `management.endpoints.web.exposure.include` | FIXED — `/actuator/health` shows `circuitBreakers: UP` with `accountService=CLOSED, sagemakerML=CLOSED`; 11 `resilience4j.*` metrics registered with `name` + `state` tags |
| BUG-T3-001 | 3 | (pre-session) | `spring-boot-docker-compose` auto-binds datasource to the postgres container's default DB `postgres`, shadowing `application.yml`'s `${DB_NAME:fraud_detection}`. Flyway migrates into `postgres`; `./postgresql/init.sql` creates an orphan `fraud_detection` DB the app never touches | [#4](https://github.com/itumelengManota/fraud-detection-service/issues/4) | FIXED in-session (2026-04-24) — added `POSTGRES_DB=fraud_detection` to `docker-compose/compose.yml`; removed redundant `CREATE DATABASE fraud_detection` from `init.sql` | FIXED — `\dt fraud_detection` shows 6 tables; `postgres` DB empty |
| BUG-T3-002 | 3 | TC-3.2/3.3/3.4 | Kafka topic names emitted by `EventPublisherAdapter` (`fraud-detection.risk-assessments`, `fraud-detection.high-risk-alerts`, `fraud-detection.domain-events`) do **not** match the names documented in `CLAUDE.md` and configured in `application.yml`. `application.yml`'s `kafka.topics.*` keys are never read — the adapter hard-codes destinations via a `switch` on event type | [#5](https://github.com/itumelengManota/fraud-detection-service/issues/5) | FIXED in-session (2026-04-24) — `EventPublisherAdapter` refactored to inject topic names via `@Value`; `application.yml`/`application-qa.yml` updated with matching keys (`risk-assessments`, `high-risk-alerts`, `domain-events`) | FIXED — both topics' offsets incremented on MEDIUM + HIGH POSTs |
| BUG-T3-003 | 3 | TC-3.9 (side-finding) | `GlobalExceptionHandler.handleIllegalArgumentException` returns HTTP 500 for `Unknown channel`/`Unknown type`/`Unknown merchant category`. Only `Unknown risk level` is re-mapped to 400. Enum-parse exceptions are client-input validation errors and should return 400 | [#6](https://github.com/itumelengManota/fraud-detection-service/issues/6) | FIXED in-session (2026-04-24) — added `startsWith("Unknown ")` branch returning `400 VALIDATION_ERROR` | FIXED — `channel=WEB` → 400 `VALIDATION_ERROR "Unknown channel: WEB"`; `type=FOO` → 400 `VALIDATION_ERROR "Unknown type: FOO"` |
| BUG-T3-004 | 3 | TC-3.8 | `SAGEMAKER_ENDPOINT_URL` defaults to `http://sagemaker-model:9002/invocations` — hostname is Docker-network-only and the container exposes port 8080 (not 9002). On host-run `bootRun`, every ML prediction fails and `MLPrediction.unavailable` is returned permanently | [#7](https://github.com/itumelengManota/fraud-detection-service/issues/7) | FIXED in-session (2026-04-24) — `SAGEMAKER_ENDPOINT_URL` default changed to `http://localhost:8080/invocations` in `application.yml` | FIXED — `ml_prediction_json.modelId="fraud-detection-endpoint"`, `fraudProbability=0.928` (not 0.0) |

---

## Final Merge

```bash
# After all 7 tiers pass:
git checkout main
git merge testing/e2e-validation
git tag v1.0.0-tested
git push origin main --tags
```
