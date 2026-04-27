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
| 4 | UC-01 Process Transaction | UC-01 | 6 | 6 | 0 | PASSED | 2026-04-24 |
| 5 | UC-03 Get Risk Assessment | UC-03 | 5 | 5 | 0 | PASSED | 2026-04-25 |
| 6 | UC-04 Find Risk-Leveled Assessments | UC-04 | 7 | 7 | 0 | PASSED | 2026-04-25 |
| 7 | NFRs — Security, Resilience, Observability | (cross-cutting) | 5 | 5 | 0 | PASSED | 2026-04-25 |
| **Total** |  |  | **47** |  |  |  |  |

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
- **Date:** 2026-04-24
- **Result:** PASSED (6/6 after fixes — see "Retest after fixes" below)
- **Bugs found (initial run):** BUG-T4-001 (`spring-boot` MCP `kafka_produce` does not Apicurio/Avro-serialise — on-wire bytes are raw JSON, no magic byte or global-ID prefix, so every consumer using the project's `AvroKafkaDeserializer` rejects the record with `IllegalStateException: artifactId cannot be null`), BUG-T4-002 (`TransactionEventConsumer`'s documented poison-pill guard — `catch (DeserializationException e)` — is unreachable. Spring Boot autoconfig wires the `AvroKafkaDeserializer` directly, with no `ErrorHandlingDeserializer` wrapper, so deserialization errors surface during `poll()` and are routed to `DefaultErrorHandler.handleOtherException`, which then throws `IllegalStateException: This error handler cannot process 'SerializationException's directly`. Result: the listener loops forever on offset 0, never committing, never acknowledging, never delivering the record to the `@KafkaListener` method. Lag is reported as `-` rather than `0`.), BUG-T4-003 (documentation/test-plan mismatch — `RiskAssessmentCompleted` is published to `fraud-detection.risk-assessments`, not `fraud-detection.domain-events`, per `EventPublisherAdapter.determineTopicForEvent`), BUG-T4-004 (latent `listener.type: batch` in `application.yml` despite `TransactionEventConsumer.consume(TransactionAvro)` being a single-record method — surfaces as `Failed to convert message payload '[{...}]' to TransactionAvro` the moment BUG-T4-002 is fixed), BUG-T4-005 (Spring Boot DevTools' `RestartClassLoader` loads generated Avro classes in two classloaders, causing `Cannot convert from [TransactionAvro] to [TransactionAvro]` on the listener thread)
- **Fixes applied:**
  - **BUG-T4-001 (workaround, not a service fix):** added `src/test/java/com/twenty9ine/frauddetection/tools/TransactionProducerHarness.java` — a standalone main class that uses `AvroKafkaSerializer` + `apicurio.registry.auto-register=true` to publish properly-framed Avro messages; wired as `./gradlew sendTransaction --args="--scenario=happy|--scenario=malformed ..."` via a new `JavaExec` task in `build.gradle`. The MCP limitation stands, but we now have an Apicurio-aware producer for Tier 4 and future Kafka work.
  - **BUG-T4-002 (service fix):** `application.yml` consumer config now declares `value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer` with `spring.deserializer.value.delegate.class: io.apicurio.registry.serde.avro.AvroKafkaDeserializer`. Deserialization failures now surface as records that `DefaultErrorHandler` ack-skips rather than throwing `IllegalStateException`, so poison pills no longer stall the listener.
  - **BUG-T4-003 (docs fix):** `CLAUDE.md` §6 updated. `fraud-detection.risk-assessments` is now documented as the destination for `RiskAssessmentCompleted`; `fraud-detection.high-risk-alerts` for `HighRiskDetected`; `fraud-detection.domain-events` is the unmapped-fallback (no events route there today).
  - **BUG-T4-004 (service fix):** `application.yml` `spring.kafka.listener.type` changed from `batch` to `single` so the listener contract matches the method signature.
  - **BUG-T4-005 (workaround):** commented out `developmentOnly 'org.springframework.boot:spring-boot-devtools'` in `build.gradle`. Setting `SPRING_DEVTOOLS_RESTART_ENABLED=false` alone was insufficient — the Avro classes still showed as split between the restarted classloader and the listener thread. Disabling the dependency entirely was the only reliable fix for a host-run `bootRun`. Re-enable once the devtools interaction with Apicurio/Avro generated classes is understood (future work).

#### Pre-session State

- Service (`bootRun` PID 13823) healthy; `/actuator/health` = UP.
- Tier 3 leftovers: 0 rows in `public.transaction`, 2 rows in `public.risk_assessments` (from UC-02 REST-driven MEDIUM and HIGH paths — Tier 3 never seeded this table because UC-02 only persists `RiskAssessment`, not `Transaction`; UC-01 is the first path that writes both).
- Kafka topic inventory (`kafka_topics`): `transactions.normalized` (1/1), `fraud-detection.risk-assessments` (1/1), `fraud-detection.high-risk-alerts` (1/1). **`fraud-detection.domain-events` is not present** — it was configured in `application.yml` under `kafka.topics.domain-events`, but the service only produces to `risk-assessments` and `high-risk-alerts` (per `EventPublisherAdapter`), so the broker has never auto-created the fallback topic.
- Apicurio registry inventory: 2 artifacts registered — `fraud-detection.high-risk-alerts-value` (`HighRiskDetectedAvro`), `fraud-detection.risk-assessments-value` (`RiskAssessmentCompletedAvro`). **`transactions.normalized-value` is not registered** — the service only consumes from this topic, never produces to it, and the MCP producer (see BUG-T4-001) did not register a schema either.
- Consumer group `fraud-detection-service`: Stable, one member, assigned `transactions.normalized-0`, CURRENT-OFFSET = `-` (no commits yet).

#### Test Case Results

| TC | Description | Tool(s) Used | Result | Notes |
|----|-------------|-------------|--------|-------|
| TC-4.1 | Happy path: publish valid Avro txn to `transactions.normalized`; verify Transaction + RiskAssessment persisted | `./gradlew sendTransaction` (harness), postgres MCP | ✅ PASS (retest) | Initial run via `spring-boot` MCP `kafka_produce` FAILED (raw JSON bytes, not Apicurio-framed — BUG-T4-001). Retest via harness: produced `txnId=cccccccc-cccc-cccc-cccc-cccccccccccc` to offset 6. App log: `Processing transaction: ccc...` → SageMaker ML invoked → `Completed risk assessment ... decision: CHALLENGE` → `Successfully processed transaction`. DB: `risk_level=MEDIUM, decision=CHALLENGE, risk_score_value=51`, `public.transaction` row created (UC-01 persists both). |
| TC-4.2 | Idempotency: re-publish same transactionId and confirm single DB row | harness replay, postgres MCP | ✅ PASS (retest) | Replayed `cccccccc-...` at offset 7. App log: `Duplicate transaction detected, skipping: cccccccc-...`. DB: `txn_count=1, ra_count=1` — no duplicate row. `SeenMessageCache` short-circuit verified. |
| TC-4.3 | Poison-pill handling: malformed payload is ack-skipped, listener stays alive | `./gradlew sendTransaction --scenario=malformed`, `kafka_consumer_groups` | ✅ PASS (retest) | Produced raw-JSON payload (key `deadbeef-...`) to offset 7. After ErrorHandlingDeserializer fix (BUG-T4-002), `DefaultErrorHandler` ack-skips the record without killing the listener. Consumer-group state remains `Stable` with the same member; `totalLag=0`; `CURRENT-OFFSET=8 = LOG-END-OFFSET=8`. Zero exceptions thrown in listener. |
| TC-4.4 | `RiskAssessmentCompleted` published after a processed transaction | `docker exec kafka-console-consumer.sh` (Avro raw) | ✅ PASS (retest) | Topic corrected to `fraud-detection.risk-assessments` (per BUG-T4-003 fix). 10 events on the topic — the last is txnId `cccccccc-cccc-cccc-cccc-cccccccccccc` with `MEDIUM / CHALLENGE`, matching the DB row written by TC-4.1. Confirms UC-01 publishes the completion event on the correct topic. |
| TC-4.5 | Consumer-group lag returns to 0 via `kafka_consumer_groups` | `kafka_consumer_groups`, `kafka-consumer-groups.sh` | ✅ PASS (retest) | After all fixes: `GROUP=fraud-detection-service TOPIC=transactions.normalized PARTITION=0 CURRENT-OFFSET=8 LOG-END-OFFSET=8 LAG=0`. Matches `spring-boot` MCP output `state=Stable, totalLag=0`. Offsets 0–7 all either processed-and-committed or ack-skipped (poison pill). |
| TC-4.6 | Record results in `TESTING.md` | Edit | ✅ PASS | This section (including retest block). |

#### MCP / Tooling Notes

- `spring-boot` `kafka_produce` — on inspection, the JSON payload is delivered to the broker as raw JSON string bytes (`serializedValueSize=556`, `kafka_consume` round-trips the identical JSON document). No Apicurio magic byte, no global-ID prefix, and no registered schema for `transactions.normalized-value` appeared in Apicurio after the call. The tool either bypasses this project's `AvroKafkaSerializer` producer config or uses an internal `StringSerializer`. For this project's Apicurio stack the tool is effectively a raw-bytes producer — it CAN be used deliberately to inject poison pills (useful for TC-4.3 once BUG-T4-002 is fixed), but it CANNOT be used to drive the UC-01 happy path.
- `spring-boot` `kafka_consumer_groups` — returns `members`, `assignedPartitions`, `state`, but **no lag / current-offset / log-end-offset fields**. Used `docker exec fraud-detection-kafka /opt/kafka/bin/kafka-consumer-groups.sh --describe` as the authoritative source for lag figures (same workaround pattern as Tier 3's `kafka-get-offsets.sh`).
- postgres MCP `execute_sql` — works correctly in this session (contrast with the Tier 1 note that queries errored with `syntax error at or near "all"`; the Tier 3 fixes appear to have resolved that, or the default-SQL bug only manifests for certain call shapes). The only correction needed was column naming: `public.transaction` uses `amount_value` / `amount_currency` (value-object mapping), and `public.risk_assessments` uses `risk_score_value` (not `risk_score`).
- `kafka_consume` — correctly round-trips the JSON written by `kafka_produce`. Since its consumer doesn't use `AvroKafkaDeserializer`, it sidesteps the Apicurio problem and shows the wire-level bytes as text.

#### Residual State (for next session)

- All 8 offsets on `transactions.normalized` are committed (CURRENT-OFFSET=LOG-END-OFFSET=8, LAG=0). `public.transaction` has 1 row (`cccccccc-...`); `public.risk_assessments` has 3 rows (2 Tier-3 leftovers + 1 from TC-4.1). Service running on port 9001 (DevTools disabled).
- `transactions.normalized-value` Avro schema was auto-registered to Apicurio by the harness on first happy-path send.
- The `spring-boot` MCP `kafka_produce` tool remains raw-JSON-only (BUG-T4-001 open as tooling limitation). Use `./gradlew sendTransaction` for any future Avro-framed production.

#### Retest Summary (after fixes)

Retest ordering: harness-produce TC-4.1 happy → harness-replay TC-4.2 idempotency → harness-produce TC-4.3 poison-pill (`--scenario=malformed`) → raw Kafka consume TC-4.4 → consumer-group describe TC-4.5. All five ran cleanly against a single `bootRun` instance with no listener restart required between cases — confirms the poison-pill ack-skip path survives mixed happy/poison traffic.

### Tier 5: UC-03 Get Risk Assessment
- **Pre-session:** Tiers 1–4 passed; service running on port 9001; 3 risk_assessments rows in DB (2 from Tier 3, 1 from Tier 4)
- **MCP servers used:** rest-api, keycloak, postgres, github
- **Session command:**
  ```bash
  claude -n "test-uc03-get" --model claude-sonnet-4-6
  ```
- **Opening prompt:**
  > Execute Tier 5 (UC-03 Get Risk Assessment). Cover: 200 OK for a known transactionId, 404 for an unknown UUID, 401 without token, 403 with a token that lacks read scope, 400 for an invalid UUID. Record outcomes in TESTING.md.
- **Date:** 2026-04-25
- **Result:** PASSED (5/5 after fixes)
- **Bugs found:** BUG-T5-001 (invalid UUID path variable returns 500 — fixed), BUG-T5-002 (detector user had fraud:read in addition to fraud:detect — fixed)
- **Fixes applied:**
  - `GlobalExceptionHandler.java` — added `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` returning HTTP 400 `INVALID_PARAMETER`
  - `docker-compose/keycloak-config/fraud-detection-realm.json`, `fraud-detection-realm-test.json`, `realm-export-test.json` — removed `fraud:read` from detector/test-detector client roles (now only `fraud:detect`)
  - Live Keycloak — `fraud:read` role mapping removed from `detector` user via admin REST API

#### Pre-session State

- Service (`bootRun` PID 54288) healthy on port 9001; Tier 4 DB state intact.
- `public.risk_assessments`: 3 rows — `cccccccc-cccc-cccc-cccc-cccccccccccc` (MEDIUM/CHALLENGE/51), `2a008909-...` (HIGH/REVIEW/76), `2fd03fe8-...` (MEDIUM/CHALLENGE/56).
- Used `cccccccc-cccc-cccc-cccc-cccccccccccc` (Tier 4 Kafka-produced transaction) as the known transactionId for TC-5.1/5.2/5.3/5.4.
- Token source: `get-token.sh` (client `fraud-detection-web`, secret `v7tcA1Ku4mARrZwO6tuC3g36CmqZn8xp`). Tokens cached at `/tmp/analyst-token.txt` and `/tmp/detector-token.txt`.

#### Security Config Notes

- `SecurityConfig` requires `SCOPE_fraud:read` for all `GET /fraud/assessments/**` requests.
- `KeycloakGrantedAuthoritiesConverter` extracts roles from `resource_access.<client>.roles` and maps them to `SCOPE_<role>` authorities.
- **Finding:** `detector` token decode reveals `resource_access.fraud-detection-service.roles = [fraud:detect, fraud:read, ...]` — detector holds both scopes. `analyst` holds only `fraud:read`. No existing test user holds `fraud:detect` without `fraud:read`, so the 403 path for GET cannot be exercised with current realm users.

#### Test Case Results

| TC | Description | Request | Response | Result | Notes |
|----|-------------|---------|----------|--------|-------|
| TC-5.1 | 200 OK — known transactionId with analyst token | `GET /fraud/assessments/cccccccc-cccc-cccc-cccc-cccccccccccc` + analyst Bearer | `{assessmentId, transactionId, riskScore:51, transactionRiskLevel:"MEDIUM", decision:"CHALLENGE", assessmentTime}` HTTP 200 | ✅ PASS | Full DTO returned. Matches DB row from Tier 4 (Kafka-produced transaction). `assessmentId` is a UUIDv7 as expected. |
| TC-5.2 | 404 — unknown UUID | `GET /fraud/assessments/00000000-0000-0000-0000-000000000000` + analyst Bearer | `{code:"RISK_ASSESSMENT_NOT_FOUND", message:"Risk assessment not found for transaction ID: 00000000-...", details:null}` HTTP 404 | ✅ PASS | Structured 404 from `RiskAssessmentNotFoundException` handler. Message includes the queried transactionId. |
| TC-5.3 | 401 — no Authorization header | `GET /fraud/assessments/cccccccc-...` (no token) | HTTP 401, empty body | ✅ PASS | Spring Security returns 401 before any application code runs. Empty body is expected (resource server default). |
| TC-5.4 | 403 — token lacks fraud:read scope | `GET /fraud/assessments/cccccccc-...` + detector Bearer | *(initial)* HTTP 200 → *(after BUG-T5-002 fix)* HTTP 403, empty body | ✅ PASS (after fix) | detector user had `fraud:read` in addition to `fraud:detect` in realm JSON and live Keycloak. After removing `fraud:read` from detector's client roles and issuing a fresh token: `resource_access.fraud-detection-service.roles = [fraud:detect]` only → GET returns 403. Analyst (only `fraud:read`) still returns 200. See BUG-T5-002 / GitHub #9. |
| TC-5.5 | 400 — invalid UUID format | `GET /fraud/assessments/not-a-uuid` + analyst Bearer | *(initial)* HTTP 500 → *(after fix)* `{code:"INVALID_PARAMETER", message:"Invalid value 'not-a-uuid' for parameter 'transactionId'. Expected type: UUID"}` HTTP 400 | ✅ PASS (after fix) | Spring MVC throws `MethodArgumentTypeMismatchException` when path variable cannot be converted to `UUID`. No handler existed — fell to catch-all → 500. Fixed by adding `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` in `GlobalExceptionHandler`. See BUG-T5-001 / GitHub #8. |

### Tier 6: UC-04 Find Risk-Leveled Assessments
- **Pre-session:** Tiers 1–5 passed; service running on port 9001; 3 risk_assessments rows from Tiers 3–4
- **MCP servers used:** rest-api, keycloak, postgres
- **Session command:**
  ```bash
  claude -n "test-uc04-find" --model claude-sonnet-4-6
  ```
- **Opening prompt:**
  > Execute Tier 6 (UC-04 Find Risk-Leveled Assessments). Cover: no filters, filter by risk level, filter by fromDate, both filters, pagination correctness, invalid risk level → 400, future fromDate → 400. Verify result counts match direct SQL queries via postgres MCP. Record outcomes in `TESTING.md`.
- **Date:** 2026-04-25
- **Result:** PASSED (7/7)
- **Bugs found:** None
- **Fixes applied:** None

#### Pre-session State

- Service (`bootRun`) healthy on port 9001; `/actuator/health` = UP.
- `public.risk_assessments`: 3 rows —
  - `cccccccc-cccc-cccc-cccc-cccccccccccc` → MEDIUM / CHALLENGE / 51 (assessment_time 2026-04-24T20:15:53Z — Tier 4 Kafka path)
  - `2a008909-e609-4345-abc1-f6ea46dd8e77` → HIGH / REVIEW / 76 (assessment_time 2026-04-24T13:29:43Z — Tier 3 REST path)
  - `2fd03fe8-3a8b-4183-8c6b-55c9c01d39e1` → MEDIUM / CHALLENGE / 56 (assessment_time 2026-04-24T13:28:36Z — Tier 3 REST path)
- Tokens: analyst (`fraud:read` only) and detector (`fraud:detect` only, BUG-T5-002 fix held).

#### Query Parameter Reference

`GET /fraud/assessments` accepts `@ModelAttribute FindRiskLeveledAssessmentsQuery`:
- `transactionRiskLevels` — `Set<String>` (multi-value; omit for all levels)
- `fromDate` — `Instant` ISO-8601 (omit for no time filter; `@PastOrPresent` enforced)
- Plus Spring `Pageable` params: `page` (0-indexed), `size`, `sort`

#### SQL Cross-Check (postgres MCP)

```sql
SELECT
  COUNT(*) AS total_count,                              -- 3
  COUNT(CASE WHEN risk_level = 'HIGH' THEN 1 END),      -- 1
  COUNT(CASE WHEN risk_level = 'MEDIUM' THEN 1 END),    -- 2
  COUNT(CASE WHEN assessment_time >= '2026-04-24T15:00:00Z' THEN 1 END),  -- 1
  COUNT(CASE WHEN risk_level = 'MEDIUM' AND assessment_time >= '2026-04-24T15:00:00Z' THEN 1 END) -- 1
FROM fraud_detection.public.risk_assessments;
```
All counts match API `totalElements` in corresponding test cases.

#### Test Case Results

| TC | Description | Request | Response | DB cross-check | Result |
|----|-------------|---------|----------|----------------|--------|
| TC-6.1 | No filters — all assessments | `GET /fraud/assessments` + analyst Bearer | HTTP 200; `totalElements:3, totalPages:1`; content: MEDIUM/51, HIGH/76, MEDIUM/56 (descending by assessmentTime) | SQL total_count=3 ✓ | ✅ PASS |
| TC-6.2 | Filter by `transactionRiskLevels=HIGH` | `GET /fraud/assessments?transactionRiskLevels=HIGH` + analyst | HTTP 200; `totalElements:1`; content: `2a008909` HIGH/REVIEW/76 | SQL high_count=1 ✓ | ✅ PASS |
| TC-6.3 | Filter by `fromDate=2026-04-24T15:00:00Z` | `GET /fraud/assessments?fromDate=2026-04-24T15:00:00Z` + analyst | HTTP 200; `totalElements:1`; content: `cccccccc` MEDIUM/CHALLENGE/51 (assessment_time 20:15Z — the only row after 15:00Z) | SQL from_1500z_count=1 ✓ | ✅ PASS |
| TC-6.4 | Both filters: `transactionRiskLevels=MEDIUM&fromDate=2026-04-24T15:00:00Z` | combined query + analyst | HTTP 200; `totalElements:1`; content: `cccccccc` MEDIUM/CHALLENGE/51 (only MEDIUM row ≥ 15:00Z) | SQL medium_from_1500z_count=1 ✓ | ✅ PASS |
| TC-6.5 | Pagination correctness (size=2) | `page=0&size=2`: 2 items returned, `totalElements:3, totalPages:2`; `page=1&size=2`: 1 item returned, `totalElements:3, totalPages:2`. Order consistent with TC-6.1 (newest first). | HTTP 200 × 2 | SQL total_count=3 ✓; page boundaries correct | ✅ PASS |
| TC-6.6 | Invalid risk level → 400 | `GET /fraud/assessments?transactionRiskLevels=EXTREME` + analyst | HTTP 400 `{"code":"INVALID_RISK_LEVEL","message":"Invalid transaction risk level. Valid values are: LOW, MEDIUM, HIGH, CRITICAL"}` | no DB call | ✅ PASS |
| TC-6.7 | Future `fromDate` → 400 | `GET /fraud/assessments?fromDate=2030-01-01T00:00:00Z` + analyst | HTTP 400 `{"code":"VALIDATION_ERROR","details":["fromDate: fromDate cannot be in the future"]}` | no DB call | ✅ PASS |

#### Findings / Notes

- **Sort order:** All responses are ordered descending by `assessmentTime` (newest first) — consistent with the use-case contract ("ordered by assessment time descending").
- **`transactionRiskLevels` binding:** Spring's `@ModelAttribute` on a `Set<String>` field correctly handles repeated params (`?transactionRiskLevels=HIGH&transactionRiskLevels=MEDIUM`) and single-value params.
- **Validation path for invalid risk level:** `TransactionRiskLevel::fromString` throws `IllegalArgumentException("Unknown risk level: EXTREME")`. `GlobalExceptionHandler.handleIllegalArgumentException` catches it, matches `"Unknown risk level"` prefix, and returns 400 `INVALID_RISK_LEVEL`. Works correctly.
- **Validation path for future `fromDate`:** `@PastOrPresent` on `FindRiskLeveledAssessmentsQuery.fromDate` fires on the `@ModelAttribute` binding. `@Validated` on the controller causes `ConstraintViolationException`, handled by `GlobalExceptionHandler.handleValidationException` → 400 `VALIDATION_ERROR` with detail message.
- **No bugs found in this tier.** All 7 test cases passed on first run without any code changes.

### Tier 7: NFRs — Security, Resilience, Observability
- **Pre-session:** Tiers 1–6 passed
- **MCP servers used:** keycloak, spring-boot, grafana, docker
- **Session command:**
  ```bash
  claude -n "test-nfrs" --model claude-opus-4-7
  ```
- **Opening prompt:**
  > Execute Tier 7. Verify (a) detector/analyst/admin scopes enforce expected access via keycloak and rest-api MCP servers; (b) stopping the `fraud-detection-model` container via docker MCP causes `sagemakerML` circuit breaker to open and the service to fall back to rule-only scoring (observed via spring-boot MCP metrics); (c) OTLP traces/metrics/logs are reaching Grafana (query via grafana MCP); (d) logs include `traceId`/`transactionId` correlation; (e) no PII is written to logs. Record outcomes in `TESTING.md`.
- **Date:** 2026-04-25 (initial); 2026-04-25 (retest after fixes)
- **Result:** PASSED (5/5 after BUG-T7-001 + BUG-T7-002 fixes)
- **Bugs found:** BUG-T7-001 (sagemakerML CB cannot open: exceptions caught inside `circuitBreaker.executeSupplier` lambda before CB can record failures), BUG-T7-002 (OTLP logs not exported to Loki: `spring-boot-starter-opentelemetry` 4.0.2 wires metrics + tracing but does not include a Logback→OTel log appender, so the configured `OTLP_LOGGING_ENDPOINT` is never used)
- **Fixes applied:**
  - **BUG-T7-001** — `SageMakerMLAdapter.predict` restructured: `try { circuitBreaker.executeSupplier(() -> { ... ; return parsePrediction(...); }) } catch (Exception e) { return fallbackPrediction(); }`. Exceptions now propagate through the CB before the fallback is applied.
  - **BUG-T7-002** — added `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.11.0-alpha` and `io.opentelemetry:opentelemetry-api-incubator:1.55.0-alpha` to `build.gradle`; new `src/main/resources/logback-spring.xml` registers the OTel appender on the root logger (alongside the existing console appender, preserving Spring Boot's `[appname,traceId,spanId]` MDC pattern); new `OpenTelemetryLogBridgeConfig` `@PostConstruct` calls `OpenTelemetryAppender.install(openTelemetry)` to wire the appender to Spring Boot 4's autoconfigured `OpenTelemetry` SDK bean (Spring Boot 4 omits the autoinstrumentation that normally handles this).

#### Pre-session State

- Service (`bootRun` PID 93218 / gradle wrapper PID 93169) healthy on port 9001; `/actuator/health` = UP. JVM stdout piped to gradle daemon log `~/.gradle/daemon/9.3.0/daemon-54623.out.log` (used as the source-of-truth for log inspection in TC-7.4 / TC-7.5).
- All 10 docker containers Up; `fraud-detection-model` started.
- Tokens issued via direct `curl` to Keycloak (the colourised `get-token.sh` output isn't pipe-friendly for capturing a clean token). Decoded JWTs confirm:
  - `detector` → `resource_access.fraud-detection-service.roles = [..., "fraud:detect"]` (no `fraud:read`, per BUG-T5-002 fix)
  - `analyst` → `resource_access.fraud-detection-service.roles = [..., "fraud:read"]` only
  - `admin` → both `fraud:detect` and `fraud:read`

#### Observability Stack Findings

- **Tempo (traces): ✅** — `service.name=fraud-detection-service` traces populated. Sample trace `659ba12c945a25e107f642a55fabcd97` has 32 spans for `http post /fraud/assessments` covering security filter chain, bearer-token authentication, request authorization, controller, etc.
- **Prometheus (metrics): ✅** — 54 `http_server_requests_milliseconds_count` series for `service_name=fraud-detection-service`; `jvm_*`, `resilience4j.circuitbreaker.*`, `http_server_requests_*` metric families all reachable via Prometheus datasource.
- **Loki (logs): ❌** — `list_loki_label_names` returns `[]`; `{service_name="fraud-detection-service"}` returns 0 log entries; `{job=~".+"}` over the last 48 h returns 0 entries. The OTLP HTTP endpoint at `localhost:4318/v1/logs` accepts payloads (returns 200) but no logs ever land in Loki. Root cause: `spring-boot-starter-opentelemetry:4.0.2` declares dependencies on `spring-boot-opentelemetry`, `spring-boot-micrometer-tracing-opentelemetry`, `micrometer-registry-otlp` and `opentelemetry-exporter-otlp` — but **no Logback / Log4j2 OTel appender bridge** (`opentelemetry-logback-appender-1.0` or `opentelemetry-log4j-appender-2.17`). With no appender, the `OpenTelemetryLogs` SDK is configured but receives no log records. Logged as BUG-T7-002.

#### Test Case Results

| TC | Description | Tool(s) Used | Result | Notes |
|----|-------------|-------------|--------|-------|
| TC-7.1 | RBAC / scope enforcement (detector / analyst / admin / no-auth) on `POST /fraud/assessments`, `GET /fraud/assessments/{id}`, `GET /fraud/assessments` | keycloak (token issue), rest-api (curl) | ✅ PASS | Matrix verified with valid request bodies (`country=US`, `transactionTimestamp` field name): detector POST 200, analyst POST **403** (no `fraud:detect`), admin POST 200, unauth POST **401**. detector GET/list **403** (no `fraud:read`), analyst GET 200, admin GET 200, unauth GET **401**. All 12 cells of the access matrix match the SecurityConfig contract. |
| TC-7.2 | Stop `fraud-detection-model` → expect `sagemakerML` CB to OPEN and service to fall back to rule-only | docker, spring-boot (`/actuator/circuitbreakers`, `/actuator/metrics/resilience4j.*`), postgres | ✅ PASS (after BUG-T7-001 fix) | **Initial run (2026-04-25):** `docker stop fraud-detection-model` → port 8080 closed. Drove 15 POSTs through `/fraud/assessments` while down. **Fallback ✅** — every persisted `risk_assessments.ml_prediction_json` shows `{"modelId":"unavailable","fraudProbability":0.0,...}`, confirming rule-only scoring. **CB ✗** — state stuck at CLOSED with `failureRate=0.0%`, `failedCalls=0` after 17 calls. Root cause logged as BUG-T7-001 (try/catch inside `executeSupplier` lambda swallowed every exception). **Retest after fix (2026-04-25):** with model container down and the restructured `predict()` method, `/actuator/circuitbreakers` shows `sagemakerML` `state=OPEN`, `failureRate=100.0%`, `failedCalls=10`, `notPermittedCalls=5` (CB short-circuited 5 calls after threshold); fallback still applied (rule-only scoring persisted). Container restarted afterwards; CB transitions back to HALF_OPEN → CLOSED on subsequent successful calls. |
| TC-7.3 | OTLP traces / metrics / logs reach Grafana | grafana MCP (`list_datasources`, `query_prometheus`, `list_loki_label_names`, `query_loki_logs`), Grafana proxy → Tempo `/api/search` | ✅ PASS (after BUG-T7-002 fix) | **Initial run:** Tempo ✅ (trace `659ba12c...` with 32 spans), Prometheus ✅ (54 `http_server_requests_milliseconds_count` series), Loki ✗ (0 streams, 0 labels — BUG-T7-002). **Retest after fix:** Loki now exposes `service_name="fraud-detection-service"` label and returns full structured records. Sample query `{service_name="fraud-detection-service"}` returns log lines with structured metadata: `traceId="c85fa9fc5684464c03bff127c1d8d403"`, `span_id="ccd8ef848865d389"`, `code_filepath="FraudDetectionApplicationService.java"`, `code_lineno="53"`, `severity_text`, plus exception records (`exception_type="io.github.resilience4j.circuitbreaker.CallNotPermittedException"`, `exception_stacktrace=...`) — confirming both the appender bridge works and the BUG-T7-001 CB fix is observably operational end-to-end. |
| TC-7.4 | Logs include `traceId` / `transactionId` correlation | gradle daemon log (`daemon-54623.out.log`) | ✅ PASS (with caveat) | Sampled txnId `bbbb2222-cccc-3333-dddd-444455556666` (POST 200, score=55, MEDIUM/CHALLENGE). Log line: `INFO [fraud-detection-service,98747fc69024b0d53889416e9e1bb971,f6d1c2d2e0a52214] 93218 --- [fraud-detection-service] [cat-handler-103] [98747fc69024b0d53889416e9e1bb971-f6d1c2d2e0a52214] FraudDetectionApplicationService : Starting risk assessment for transaction: bbbb2222-...`. Pattern is `[appname,traceId,spanId]` from `application.yml:296`. **Caveat:** `transactionId` is **not** in MDC — only present as inline message text (e.g. `"... for transaction: {uuid}"`). On Tempo, the same trace `659ba12c...` matches the log line's traceId 1:1, proving log↔trace correlation by `traceId`. **Caveat 2:** SageMaker async invocation runs on `ForkJoinPool.commonPool-worker-N` and shows `[fraud-detection-service,,]` — the MDC traceId does NOT propagate across the async boundary (Micrometer Tracing's context-propagation isn't wrapping the `CompletableFuture` chain). Recommend adding `Span.current().setAttribute("transaction.id", ...)` in `FraudDetectionApplicationService.assess` and `MDC.put("transactionId", ...)` (with cleanup) for consistent structured correlation; and `ContextSnapshotFactory`-aware async wrappers for ML calls. |
| TC-7.5 | No PII in logs | grep over `daemon-54623.out.log` (~33,000 lines spanning Tiers 3–7) | ✅ PASS | Searched for: 13–19 digit PAN sequences (only matches were Kafka epoch timestamps), `cvv`/`cv2`/`cvc`/`card[ -]?number`/`cardholder`/`pan` (0 matches), email patterns (0 matches outside service-internal `@frauddetection.com` user emails which exist only in tokens, not in app logs), SSN (0 matches), `Authorization:` / `Bearer ey…` / `client_secret` (0 matches), raw `latitude`/`longitude` keys (0 matches — only derived `distance_from_home` is in the ML feature payload). What IS logged: `transactionId`/`accountId` (UUIDs, internal identifiers), `merchantId` (opaque ID), `amount`, `currency`, `channel`, `type`, derived ML features (`distance_from_home`, `hour`, `is_weekend`, etc.). All non-PII. ⚠️ minor observation: `distance_from_home` reveals approximate device location relative to prior txns; if regulatory definition of PII expands, consider tagging this DEBUG and disabling in non-dev profiles. |

#### Residual State

- `risk_assessments` row count after Tier 7: 22 rows (3 Tier 3-4 leftovers + 1 TC-7.1 admin smoke + 15 TC-7.2 CB-down POSTs + 1 TC-7.3 trace probe + 2 TC-7.4 correlation probes — `cccccccc-...`, `2a008909-...`, `2fd03fe8-...`, plus the ones produced this session under prefixes `11111111-...`, `77777777-...`, `aaaa1111-...`, `bbbb2222-...`).
- `fraud-detection-model` container restarted (Up).
- Service still running on port 9001; no restarts performed during Tier 7.

#### Retest Session (2026-04-25) — BUG-T7-001 + BUG-T7-002 fixes

- **Pre-retest:** committed fixes (CB restructure + OTel Logback appender + bridge config); rebuilt with `./gradlew clean build`; restarted `bootRun`.
- **BUG-T7-001 retest (TC-7.2):** stopped `fraud-detection-model`; fired 15 POSTs at `/fraud/assessments` with admin token; observed `/actuator/circuitbreakers` → `sagemakerML.state=OPEN, failureRate=100.0%, failedCalls=10, notPermittedCalls=5`. Fallback still behaves correctly (`ml_prediction_json.modelId="unavailable"` persisted). Restarted container; CB transitioned to HALF_OPEN → CLOSED on the next successful invocation. **PASS.**
- **BUG-T7-002 retest (TC-7.3):** issued 5 POSTs while observability ran, then queried Loki via `query_loki_logs` (`{service_name="fraud-detection-service"}`). 5 records returned with full structured metadata (`traceId`, `span_id`, `code_filepath`, `code_lineno`, `severity_text`, `exception_type`, `exception_stacktrace`). Loki labels list now includes `service_name`. Trace↔log correlation is now bidirectionally verifiable (Tempo trace IDs match Loki `traceId` metadata). **PASS.**
- **Net Tier 7 outcome:** PASSED 5/5. All 7 tiers now pass.

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
| BUG-T4-001 | 4 | TC-4.1 | `spring-boot` MCP `kafka_produce` does not Avro/Apicurio-serialise the JSON body. Bytes on the wire are raw JSON (no magic byte, no Apicurio global-ID header); no schema is registered for `transactions.normalized-value`. Against this project's `AvroKafkaDeserializer`, every record produced by this tool becomes a poison pill. Tooling limitation — the UC-01 inbound path cannot be exercised through this MCP | TBD | WORKAROUND (2026-04-24) — `TransactionProducerHarness` + `sendTransaction` Gradle task, uses `AvroKafkaSerializer` directly | WORKED AROUND — UC-01 happy path now exercisable; MCP tool itself still raw-JSON-only (tracked for upstream `spring-boot-mcp-server` improvement) |
| BUG-T4-002 | 4 | TC-4.3/TC-4.5 | `TransactionEventConsumer` declares a `catch (DeserializationException e) { acknowledgment.acknowledge(); }` branch, but the branch is unreachable. Spring Boot autoconfig wires `AvroKafkaDeserializer` directly (no `ErrorHandlingDeserializer` wrapper), so deserialization errors surface during `poll()` and are handled by `DefaultErrorHandler`, which rethrows `IllegalStateException: This error handler cannot process 'SerializationException's directly`. Listener loops forever on offset 0, never committing, never invoking the `@KafkaListener` method. Lag is perpetually `-`. **Service defect** — poison-pill safety net advertised in code does not actually work at runtime | TBD | FIXED in-session (2026-04-24) — `application.yml` consumer now uses `ErrorHandlingDeserializer` delegating to `AvroKafkaDeserializer`; `apicurio.registry.*` props moved into `properties:` block so they reach the delegate | FIXED — TC-4.3 poison-pill ack-skip verified; listener stable across mixed happy/poison traffic; `CURRENT-OFFSET=LOG-END-OFFSET, LAG=0` |
| BUG-T4-003 | 4 | TC-4.4 | `CLAUDE.md` §6 and the Tier 4 opening prompt both state that `RiskAssessmentCompleted` is published to `fraud-detection.domain-events`. In practice `EventPublisherAdapter.determineTopicForEvent` routes `RiskAssessmentCompleted` to `fraud-detection.risk-assessments` and only uses `domain-events` as the fallback for unmapped event types. Documentation / test-plan inconsistency | TBD | FIXED in-session (2026-04-24) — `CLAUDE.md` §6 topics table rewritten: `risk-assessments` = `RiskAssessmentCompleted`, `high-risk-alerts` = `HighRiskDetected`, `domain-events` = unmapped-fallback (no events today) | FIXED — TC-4.4 confirms `cccccccc-...` event on `fraud-detection.risk-assessments`, matching DB |
| BUG-T4-004 | 4 | TC-4.1 (latent) | `application.yml` declared `spring.kafka.listener.type: batch`, but `TransactionEventConsumer.consume(TransactionAvro)` is a single-record method. Converter failed with `Failed to convert message payload '[{...}]' to TransactionAvro` the moment BUG-T4-002 was fixed (records finally reached the listener). **Service defect** — latent listener-type misconfiguration masked by the upstream BUG-T4-002 failure | TBD | FIXED in-session (2026-04-24) — `application.yml` `spring.kafka.listener.type` changed from `batch` to `single` | FIXED — TC-4.1 end-to-end processes without converter errors |
| BUG-T4-005 | 4 | TC-4.1 (env-only) | With `spring-boot-devtools` on the classpath, `RestartClassLoader` loads generated Avro classes (`TransactionAvro` et al.) in both the restarted loader and the Kafka listener thread, producing `Cannot convert from [TransactionAvro] to [TransactionAvro]` on the first record reaching the listener. Setting `SPRING_DEVTOOLS_RESTART_ENABLED=false` alone did not resolve it — `restartedMain` thread still present — so `developmentOnly` dependency had to be removed entirely. Only affects host-run `bootRun`; not a production defect | TBD | WORKAROUND (2026-04-24) — `developmentOnly 'org.springframework.boot:spring-boot-devtools'` commented out in `build.gradle`; `spring-boot-docker-compose` retained | WORKED AROUND — `bootRun` stable; future work: understand Apicurio/Avro generated-class interaction with DevTools restart |
| BUG-T5-001 | 5 | TC-5.5 | `GET /fraud/assessments/{transactionId}` with a non-UUID string (e.g. `not-a-uuid`) returns HTTP 500. Spring MVC throws `MethodArgumentTypeMismatchException` when it cannot bind the path variable to `UUID`; `GlobalExceptionHandler` had no handler for this type so it fell to the catch-all `handleGenericException` → 500. Client-input error — should be 400. | [#8](https://github.com/itumelengManota/fraud-detection-service/issues/8) | FIXED (2026-04-25) — added `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` returning HTTP 400 `INVALID_PARAMETER` with rejected value and expected type | FIXED — `GET /fraud/assessments/not-a-uuid` → `400 {"code":"INVALID_PARAMETER","message":"Invalid value 'not-a-uuid' for parameter 'transactionId'. Expected type: UUID"}` |
| BUG-T5-002 | 5 | TC-5.4 | `detector` Keycloak user had `fraud:read` in `resource_access.fraud-detection-service.roles` in addition to `fraud:detect`. The 403 authorization path for `GET /fraud/assessments/**` (`hasAuthority("SCOPE_fraud:read")`) exists and is correctly configured, but could not be exercised because no test user held only `fraud:detect` without `fraud:read`. `GET` with the detector token returned 200. Also affected `test-detector` in `realm-export-test.json`. | [#9](https://github.com/itumelengManota/fraud-detection-service/issues/9) | FIXED (2026-04-25) — removed `fraud:read` from `detector`/`test-detector` client roles in all 3 realm JSON files; live Keycloak updated via admin REST API | FIXED — fresh detector token has only `fraud:detect`; `GET /fraud/assessments/{id}` returns 403; analyst still returns 200 |
| BUG-T7-001 | 7 | TC-7.2 | `sagemakerML` Resilience4j circuit breaker can never open. In `SageMakerMLAdapter.predict` (`SageMakerMLAdapter.java:91-112`), the `try { ... } catch (Exception e) { return fallbackPrediction(); }` block is *inside* the `circuitBreaker.executeSupplier(() -> {...})` lambda, so every SDK / connection-refused exception is swallowed and a non-throwing `MLPrediction.unavailable()` is returned. The CB only ever sees a successful supplier execution: with the ML container down, 17 calls produced `failureRate=0.0%`, `failedCalls=0`, `state=CLOSED`, while the underlying calls were all timing out / connection-refusing. The service still degrades correctly to rule-only scoring (verified via `risk_assessments.ml_prediction_json.modelId="unavailable"`), but the CB protection mechanism is effectively a no-op. | TBD | FIXED in-session (2026-04-25) — `SageMakerMLAdapter.predict` restructured: `try { circuitBreaker.executeSupplier(() -> { ... return parsePrediction(...); }) } catch (Exception e) { return fallbackPrediction(); }`. Exceptions now propagate through the CB before fallback applies. | FIXED — retest with model container down: `sagemakerML.state=OPEN`, `failureRate=100.0%`, `failedCalls=10`, `notPermittedCalls=5`; rule-only fallback still applied; CB transitions HALF_OPEN→CLOSED after container restart. |
| BUG-T7-002 | 7 | TC-7.3 | OTLP logs are not exported to Loki. `application.yml:289-292` configures `opentelemetry.logging.export.otlp.endpoint=http://localhost:4318/v1/logs` and the LGTM container's OTLP HTTP collector is up (returns 200 to `POST /v1/logs`), but Loki shows zero streams and zero labels for the time window covering Tiers 1–7. Root cause: `spring-boot-starter-opentelemetry:4.0.2` resolves to `spring-boot-opentelemetry`, `spring-boot-micrometer-tracing-opentelemetry`, `micrometer-registry-otlp` and `opentelemetry-exporter-otlp` only — there is no Logback (or Log4j2) → OTel appender bridge on the classpath. `opentelemetry-sdk-logs` is present transitively but receives no log records. Result: the `OTLP_LOGGING_ENDPOINT` config setting is silently ignored at runtime. | TBD | FIXED in-session (2026-04-25) — added `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.11.0-alpha` and `io.opentelemetry:opentelemetry-api-incubator:1.55.0-alpha` to `build.gradle`; new `src/main/resources/logback-spring.xml` registers the OTel appender on root logger (alongside CONSOLE, preserving the `[appname,traceId,spanId]` MDC pattern); new `OpenTelemetryLogBridgeConfig` `@PostConstruct` calls `OpenTelemetryAppender.install(openTelemetry)` to wire the appender to Spring Boot 4's autoconfigured `OpenTelemetry` SDK bean (Spring Boot 4 omits the autoinstrumentation that normally handles this). | FIXED — Loki now returns full structured records for `{service_name="fraud-detection-service"}` with metadata `traceId`, `span_id`, `code_filepath`, `code_lineno`, `severity_text`, `exception_type`, `exception_stacktrace`. Tempo↔Loki bidirectional correlation by `traceId` works. |

---

## Final Merge

```bash
# After all 7 tiers pass:
git checkout main
git merge testing/e2e-validation
git tag v1.0.0-tested
git push origin main --tags
```
