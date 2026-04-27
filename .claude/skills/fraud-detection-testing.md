---
name: fraud-detection-testing
description: Activate whenever a Claude Code session is executing a tier of the Fraud Detection Service test plan (see TESTING.md and docs/FraudDetection_UseCases_Testing_Plan_v1.1.docx). Covers the risk-scoring invariants that every assertion must respect, the MCP-server routing rules for test evidence, and the standard result-recording format for TESTING.md.
---

# Fraud Detection Testing Skill

## When this skill applies

Any time the user asks Claude Code to:

- Execute a tier of the testing plan (Tier 1..Tier 7)
- Verify a specific use case (UC-01..UC-04)
- Reproduce or debug a defect logged in TESTING.md / GitHub issues
- Extend the test plan with new test cases for the fraud-detection-service

## Non-negotiable domain invariants (assertions must respect these)

1. **Risk score banding** — LOW 0..40, MEDIUM 41..70, HIGH 71..90, CRITICAL 91..100. See `RiskAssessment.determineRiskLevel`.
2. **Decision mapping** — LOW→ALLOW, MEDIUM→CHALLENGE, HIGH→REVIEW, CRITICAL→BLOCK. Provided by the four `DecisionStrategy` beans.
3. **Aggregate invariants** — CRITICAL MUST BLOCK; LOW MUST NOT BLOCK. Anything else in `RiskAssessment.completeAssessment` throws `InvariantViolationException` and the transaction rolls back.
4. **Scoring weights** — `mlWeight + ruleWeight == 1.0`. With ML service null, service degrades to `mlWeight=0.0, ruleWeight=1.0` automatically; the assessment still completes.
5. **Impossible travel** — required speed > 965 km/h between the current and previous transaction for the same account triggers `IMPOSSIBLE_TRAVEL`.
6. **Idempotency** — `TransactionEventConsumer` short-circuits on repeat `transactionId` via `SeenMessageCache`.

If a test result appears to violate any of these invariants, **treat it as a defect, not a test-data problem**, and log it.

## MCP-server routing rules (pick the right tool for the evidence)

| If verifying... | Use this MCP server |
|-----------------|---------------------|
| HTTP status code, response body shape, endpoint reachability | rest-api (simple calls) OR spring-boot `http_request` (if token not needed) |
| A bean exists, circuit-breaker state, actuator metric, flyway migrations applied | spring-boot |
| A row was persisted / updated / soft-deleted | postgres |
| A domain event was published (topic, key, payload) | **spring-boot's Kafka tools** (`kafka_topics`, `kafka_produce`, `kafka_consume`, `kafka_consumer_groups`) — schema-aware via Apicurio |
| A container is healthy / needs restart / needs logs | docker |
| A realm, user, scope or token claim | keycloak (admin) + rest-api (for token fetch) |
| P99 latency, error rate, trace with traceId, log correlation | grafana |
| Logging a defect for fix-forward | github (issues — commits attributed to Ignatius's PAT) |

Prefer `spring-boot` over `rest-api` when both would work, for richer introspection (beans, metrics, any-URL http call with Spring's reactive client). There is **no standalone Kafka MCP server** — use `spring-boot`'s Kafka tools. See `~/git/govsight/TESTING.md` BUG-015 for the rationale.

## Result-recording protocol

After each test case, update `TESTING.md`:

- Flip its row in the Progress Summary (`NOT_TESTED` → `IN_PROGRESS` at session start, `PASSED` / `FAILED` / `BLOCKED` at the end).
- Add bullets under the relevant "Session Log" entry: test case ID, result, evidence (one-line summary of the MCP observations), and any bug ID.
- For any `FAILED` case, create a GitHub issue via the github MCP server with label `testing/e2e-validation`, and append a row in the Bug Registry table with the issue link.

## Standard test-data conventions

- **Account IDs** used by test cases follow the pattern `ACC-TEST-<scenario>-<n>` (e.g., `ACC-TEST-IMPOSSIBLE-TRAVEL-1`).
- **Transaction IDs** are fresh UUIDv4 per test case (use `uuidgen`).
- **Currency** defaults to `ZAR` unless the test case targets a cross-border scenario, in which case use `USD`.
- **Timestamps** are `Instant.now()` unless the case is specifically testing back-dated or future-dated inputs.
- Test data must not introduce realistic PII; synthetic values only.

## Common pitfalls (learned the hard way)

- **Do not call bash to operate on `/Users/...`** — that path exists on the user's Mac, not Claude's container. Use `Filesystem:*` tools.
- **Do not disable AOT settings** encountered in `build.gradle` — they were added deliberately; breaking them breaks the build.
- **Do not hit Keycloak repeatedly to re-fetch tokens** — cache the token from `./scripts/get-token.sh` at `/tmp/fraud-detection-token.txt` and reuse until it expires (usually 5 minutes).
- **Do not merge `testing/e2e-validation` until all 7 tiers pass.**
