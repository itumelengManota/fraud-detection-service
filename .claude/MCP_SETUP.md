# .claude/MCP_SETUP.md — MCP Server Setup for Fraud Detection Service Testing

> **Scope.** Install, configure and verify the 7 MCP servers referenced by `.mcp.json` at the project root. The stack is a targeted subset of the 11-server GovSight testing stack — only servers applicable to a single Java/Spring Boot bounded context are kept.
>
> **Prerequisites on the host:** Homebrew, Python 3.11+ with `uv`, Node.js 22+, Docker Desktop, Java 25 on `PATH`.

---

## Overview of the 7 servers

| # | Name in `.mcp.json` | Package / source | Purpose |
|---|---------------------|------------------|---------|
| 1 | `postgres` | `crystaldba/postgres-mcp` (via `uvx postgres-mcp`) | Query `fraud_detection` DB directly |
| 2 | `spring-boot` | Custom — `~/git/spring-boot-mcp-server` (86 tools) | Spring Boot introspection: actuator, beans, flyway, HTTP any URL, project-file read, **Kafka** (`kafka_topics`, `kafka_produce`, `kafka_consume`, `kafka_consumer_groups` — schema-aware via Apicurio) |
| 3 | `docker` | `ofershap/mcp-server-docker` (via `npx -y mcp-server-docker`) | Container inspection / logs / start / stop |
| 4 | `keycloak` | `keycloak-mcp` (via `npx -y keycloak-mcp`) | Realm / user / client inspection; token issuance |
| 5 | `grafana` | `mcp-grafana` (via `uvx mcp-grafana`) | Query Prometheus metrics, Loki logs, Tempo traces |
| 6 | `github` | GitHub Copilot MCP (HTTP endpoint) | Issues for bug tracking; **commits attributed to the PAT's owner** |
| 7 | `rest-api` | `dkmaker-mcp-rest-api` (via `npx -y ...`) | Fallback generic HTTP calls to the service |

---

## Why no standalone Kafka MCP server

An earlier draft of the stack included `tuannvm/kafka-mcp-server` as server #3. It has been **removed** based on GovSight's **BUG-015** experience: the server hung mid-session during Tier 3 testing with tool calls that never returned, blocking test progress.

Kafka verification now routes through `spring-boot-mcp-server`'s built-in Kafka tools, which:

- Use the project's Apicurio config, giving native Avro serialisation/deserialisation.
- Auto-register whenever spring-boot-mcp-server is pointed at this project (spring-kafka is in `build.gradle`).
- Need no separate process or binary on `$PATH`.

Use `kafka_topics` to list and manage topics, `kafka_produce` to publish Avro events, `kafka_consume` to read from topics, and `kafka_consumer_groups` to inspect consumer-group state.

---

## 1. postgres — `crystaldba/postgres-mcp`

```bash
# One-time verify uv/uvx is on PATH
uv --version && uvx --version

# Pre-warm the package (optional — uvx will fetch on first run otherwise)
uvx postgres-mcp --help
```

The server is launched by Claude Code with `DATABASE_URI=postgresql://postgres:postgres@localhost:5432/fraud_detection` (already in `.mcp.json`).

**Verify:** Start the infra (`docker compose -f docker-compose/compose.yml up -d postgres`), then in Claude Code ask:
> Use the postgres MCP server to list tables in the public schema.

Expected tables after Flyway runs: `risk_assessment`, `transaction`, `location`, `merchant`, `rule_evaluation`, `flyway_schema_history`.

---

## 2. spring-boot — Custom MCP (`~/git/spring-boot-mcp-server`)

Built once from source, then launched as a JAR (same pattern used for GovSight).

```bash
cd ~/git/spring-boot-mcp-server
./gradlew clean shadowJar

# Confirm the fat JAR exists at the path referenced by .mcp.json
ls -la build/libs/spring-boot-mcp-server-0.0.1-SNAPSHOT-all.jar
```

The server is launched with:

```
--project-dir  /Users/ignatiusitumelengmanota/git/fraud-detection-service
--actuator-url http://localhost:9001/actuator
```

**Requires the service to be running** (`./gradlew bootRun` from the fraud-detection-service directory) for the actuator-backed tools. Project-file tools work without the service.

**Verify (service introspection):**
> Use the spring-boot MCP server to list all Spring beans matching `RiskAssess*`.

**Verify (Kafka tools):**
> Use spring-boot's `kafka_topics` tool to list topics on `localhost:9092`.

Expected: all five configured topics appear (`transactions.normalized`, `fraud.alerts.critical/high/medium`, `fraud-detection.domain-events`).

---

## 3. docker — `ofershap/mcp-server-docker`

Runs via `npx`. Constrained to the fraud-detection container set via `ALLOWED_CONTAINERS` in `.mcp.json`:

```
fraud-detection-postgres, fraud-detection-redis, fraud-detection-kafka,
fraud-detection-keycloak, fraud-detection-apicurio-registry,
fraud-detection-apicurio-registry-ui, fraud-detection-kafka-ui,
fraud-detection-mockoon, fraud-detection-model, fraud-detection-grafana-lgtm
```

**Verify:**
> Use the docker MCP server to list the status of all allowed containers.

All should be `running`. Any in `exited` state need to be restarted before running Tier 1.

---

## 4. keycloak — `keycloak-mcp`

Runs via `npx`. Uses the bootstrap admin credentials declared in `docker-compose/compose.yml` (admin / admin).

```bash
# No install step needed — npx will fetch keycloak-mcp on first run.
```

**Verify:**
> Use the keycloak MCP server to list realms.

Should include `master` and `fraud-detection`.

---

## 5. grafana — `mcp-grafana`

```bash
uvx mcp-grafana --help
```

**Service-account token.** The token is embedded directly in `.mcp.json` (same pattern as GovSight's `.mcp.json`). To rotate it:

1. Log in at `http://localhost:3000` (admin / admin on first run; change the admin password after).
2. Navigate to Administration → Service accounts → Add service account (role: Viewer) → Add token → Generate.
3. Replace `GRAFANA_SERVICE_ACCOUNT_TOKEN` in `.mcp.json` with the new value.

**Verify:**
> Use the grafana MCP server to list datasources.

Expected: `prometheus`, `loki`, `tempo` (all built into the LGTM image).

---

## 6. github — Copilot MCP endpoint

HTTP-based, no local install required. The PAT embedded in `.mcp.json`'s `Authorization` header is **your own personal access token**, which guarantees:

- All GitHub API operations performed by the MCP (creating issues, pushing commits via any commit-creation tool, opening PRs) are attributed to **you** as the GitHub author/committer.
- No bot identity or shared service account is used.

To rotate the PAT:

1. Create a replacement PAT at `https://github.com/settings/tokens` — fine-grained or classic, with scopes `repo` (full) and `read:org`.
2. Replace the `Authorization: Bearer ...` value in `.mcp.json`.
3. Restart Claude Code so the MCP re-reads its config.

**Committer identity for local git commits.** When Claude Code uses bash `git commit` (as opposed to calling the GitHub MCP directly), the commit author/committer comes from the repo's local git config (or your global `~/.gitconfig`). `configure-session.sh` checks this on every run and warns if your identity is not set — see the output when you run it.

**Verify:**
> Use the github MCP server to get the authenticated user. Confirm the `login` field matches my GitHub handle.

---

## 7. rest-api — `dkmaker-mcp-rest-api`

Generic authenticated HTTP fallback. Pointed at `http://localhost:9001` (the fraud-detection-service itself). Tokens are passed per-request — the MCP server does not hold them.

**Verify:**
> Use the rest-api MCP server to GET /actuator/health.

---

## Quick full-stack verification

After configuring everything, run from a Claude Code session in `~/git/fraud-detection-service`:

> List every MCP server you have available and for each one, perform the simplest possible check:
> - postgres: `SELECT 1`
> - spring-boot: get actuator health; then list Kafka topics via `kafka_topics`
> - docker: list allowed containers
> - keycloak: list realms
> - grafana: list datasources
> - github: get authenticated user (confirm my identity)
> - rest-api: GET /actuator/health

Report any failures in `TESTING.md` (Tier 1).

---

## Secrets hygiene

`.mcp.json` contains two embedded secrets: the Grafana service-account token and the GitHub PAT. This matches GovSight's pattern. Treat the file as sensitive:

- It is committed to the repo on `testing/e2e-validation` for convenience but **must not** be pushed to a public remote.
- If `.mcp.json` is ever exposed, rotate both secrets immediately at their respective providers.
- The recommended long-term option is to move secrets to `1Password CLI` or `direnv` via `${ENV_VAR}` substitution; for now, the direct-embed pattern is accepted.
