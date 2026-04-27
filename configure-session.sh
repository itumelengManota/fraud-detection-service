#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# configure-session.sh — Fraud Detection Service, pre-testing-session setup
#
# Run once BEFORE starting each Claude Code testing session.
#   ./configure-session.sh
#
# It will:
#   1) Sanity-check required tools for the 7 MCP servers
#   2) Verify git committer identity (Ignatius Itumeleng Manota)
#   3) Verify the spring-boot-mcp-server fat JAR exists
#   4) Bring up docker-compose infrastructure
#   5) Wait for Keycloak to finish realm import
#   6) Warm a JWT for the `detector` user and cache it at /tmp/fraud-detection-token.txt
#
# It does NOT start the fraud-detection-service itself. Start it in another
# terminal with `./gradlew bootRun` once this script reports "READY".
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SBMCP_JAR="$HOME/git/spring-boot-mcp-server/build/libs/spring-boot-mcp-server-0.0.1-SNAPSHOT-all.jar"
COMPOSE_FILE="$REPO_DIR/docker-compose/compose.yml"

echo -e "${BLUE}═══ Fraud Detection Service — testing session setup ═══${NC}"
echo ""

# ── 1. Required tools ────────────────────────────────────────────────────
#    No kafka-mcp-server — Kafka tools now come from spring-boot-mcp-server
#    (see TESTING.md "Note on MCP Server Stack", and GovSight BUG-015).
echo -e "${BLUE}[1/6] Checking required tools on PATH...${NC}"
missing=0
for cmd in docker uv uvx npx java curl jq uuidgen git; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "  ${RED}✗${NC} $cmd not found"
    missing=$((missing + 1))
  else
    echo -e "  ${GREEN}✓${NC} $cmd"
  fi
done

# docker compose as plugin (replaces legacy docker-compose binary)
if docker compose version >/dev/null 2>&1; then
  echo -e "  ${GREEN}✓${NC} docker compose"
else
  echo -e "  ${RED}✗${NC} 'docker compose' plugin not available"
  missing=$((missing + 1))
fi

if [ "$missing" -gt 0 ]; then
  echo -e "${RED}Fix the missing tools above, then re-run.${NC}"
  exit 1
fi

# ── 2. Git committer identity ────────────────────────────────────────────
#    Ensures any `git commit` / `gh` / MCP-driven commit is attributed
#    to Ignatius (not root, not an anonymous identity).
echo ""
echo -e "${BLUE}[2/6] Checking git committer identity...${NC}"
GIT_NAME="$(git -C "$REPO_DIR" config user.name || true)"
GIT_EMAIL="$(git -C "$REPO_DIR" config user.email || true)"

if [ -z "$GIT_NAME" ] || [ -z "$GIT_EMAIL" ]; then
  echo -e "  ${YELLOW}!${NC} git user.name or user.email not set in this repo."
  echo "     Commits from bash_tool will attribute to whatever global identity is configured."
  echo "     To pin the identity for this repo, run:"
  echo -e "       ${BLUE}git -C \"$REPO_DIR\" config user.name  \"Ignatius Itumeleng Manota\"${NC}"
  echo -e "       ${BLUE}git -C \"$REPO_DIR\" config user.email \"<your-github-no-reply-or-personal-email>\"${NC}"
  echo "     The GitHub MCP server uses your PAT (.mcp.json) and is independent of this."
else
  echo -e "  ${GREEN}✓${NC} user.name  = $GIT_NAME"
  echo -e "  ${GREEN}✓${NC} user.email = $GIT_EMAIL"
  if [ "$GIT_NAME" != "Ignatius Itumeleng Manota" ]; then
    echo -e "  ${YELLOW}!${NC} user.name is not 'Ignatius Itumeleng Manota' — commits will show '$GIT_NAME'."
    echo "     If that's intentional (e.g. separate GitHub persona), ignore this warning."
  fi
fi

# Confirm .mcp.json is configured (github PAT embedded)
MCP_JSON="$REPO_DIR/.mcp.json"
if [ -f "$MCP_JSON" ] && grep -q '"github"' "$MCP_JSON"; then
  if grep -q 'github_pat_' "$MCP_JSON"; then
    echo -e "  ${GREEN}✓${NC} GitHub MCP PAT embedded in .mcp.json (commits via MCP will attribute to PAT owner)"
  else
    echo -e "  ${YELLOW}!${NC} GitHub MCP present in .mcp.json but no github_pat_ token detected — verify manually."
  fi
fi

# ── 3. Spring Boot MCP fat JAR ───────────────────────────────────────────
echo ""
echo -e "${BLUE}[3/6] Checking spring-boot-mcp-server fat JAR...${NC}"
if [ ! -f "$SBMCP_JAR" ]; then
  echo -e "  ${RED}✗${NC} Not found: $SBMCP_JAR"
  echo "     Build it with:"
  echo "       cd ~/git/spring-boot-mcp-server && ./gradlew clean shadowJar"
  exit 1
fi
echo -e "  ${GREEN}✓${NC} $SBMCP_JAR"
echo -e "       (provides actuator + Kafka tools — no standalone kafka-mcp-server needed)"

# ── 4. Bring up infra ────────────────────────────────────────────────────
echo ""
echo -e "${BLUE}[4/6] Bringing up docker-compose infrastructure...${NC}"
if [ ! -f "$COMPOSE_FILE" ]; then
  echo -e "  ${RED}✗${NC} compose file missing at $COMPOSE_FILE"
  exit 1
fi
docker compose -f "$COMPOSE_FILE" up -d
echo -e "  ${GREEN}✓${NC} docker compose up -d issued"

# ── 5. Wait for Keycloak ─────────────────────────────────────────────────
echo ""
echo -e "${BLUE}[5/6] Waiting for Keycloak to accept traffic (up to 90s)...${NC}"
for i in $(seq 1 45); do
  if curl -s -o /dev/null -w '%{http_code}' http://localhost:8180/realms/fraud-detection/.well-known/openid-configuration 2>/dev/null | grep -q '^200$'; then
    echo -e "  ${GREEN}✓${NC} Keycloak realm 'fraud-detection' is live"
    break
  fi
  sleep 2
  if [ "$i" -eq 45 ]; then
    echo -e "  ${YELLOW}!${NC} Keycloak not ready after 90s. Check: docker logs fraud-detection-keycloak"
  fi
done

# ── 6. Warm a token ──────────────────────────────────────────────────────
echo ""
echo -e "${BLUE}[6/6] Warming a JWT for the 'detector' user...${NC}"
if [ -x "$REPO_DIR/scripts/get-token.sh" ]; then
  if "$REPO_DIR/scripts/get-token.sh" detector >/dev/null 2>&1; then
    if [ -f /tmp/fraud-detection-token.txt ]; then
      echo -e "  ${GREEN}✓${NC} token cached at /tmp/fraud-detection-token.txt"
    fi
  else
    echo -e "  ${YELLOW}!${NC} token fetch failed — Keycloak may still be importing. Re-run ./scripts/get-token.sh detector in ~30s."
  fi
else
  echo -e "  ${YELLOW}!${NC} ./scripts/get-token.sh not executable — run: chmod +x scripts/get-token.sh"
fi

echo ""
echo -e "${GREEN}════════════════════════ READY ════════════════════════${NC}"
echo ""
echo "Next steps:"
echo "  1) In a separate terminal, start the service:"
echo -e "       ${BLUE}cd $REPO_DIR && ./gradlew bootRun${NC}"
echo ""
echo "  2) Once the service logs 'Started FraudDetectionServiceApplication',"
echo "     start a Claude Code testing session. Example for Tier 1:"
echo ""
echo -e "       ${BLUE}claude -n \"test-infra\" --model claude-sonnet-4-6${NC}"
echo -e "       ${BLUE}> Execute Tier 1 of the test plan following TESTING.md and docs/FraudDetection_UseCases_Testing_Plan_v1.1.docx${NC}"
echo ""
echo "  3) After each session, commit TESTING.md updates on the testing branch:"
echo -e "       ${BLUE}git checkout -b testing/e2e-validation  # first time only${NC}"
echo -e "       ${BLUE}git add TESTING.md && git commit -m \"test: tier N — N/M cases passed\"${NC}"
echo ""
