#!/bin/bash

# Script to test Fraud Detection API endpoints with authentication
# Usage: ./scripts/test-api.sh [command] [args...]
#   Commands:
#     assess <user>     - Assess a sample transaction
#     get <txn-id>      - Get assessment by transaction ID
#     list              - List assessments

set -e

# Configuration
API_URL="${API_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Function to get token
get_token() {
  local user="${1:-detector}"
  
  if [ -f "/tmp/fraud-detection-token.txt" ]; then
    TOKEN=$(cat /tmp/fraud-detection-token.txt)
  else
    echo -e "${YELLOW}No cached token found. Obtaining new token...${NC}"
    source "${SCRIPT_DIR}/get-token.sh" "$user" > /dev/null
    TOKEN=$(cat /tmp/fraud-detection-token.txt)
  fi
}

# Function to assess transaction
assess_transaction() {
  local user="${1:-detector}"
  
  echo -e "${BLUE}Assessing transaction as user: ${user}${NC}"
  get_token "$user"
  
  TRANSACTION_ID=$(uuidgen)
  
  REQUEST_BODY=$(cat <<EOF
{
  "transactionId": "$TRANSACTION_ID",
  "accountId": "ACC-$(date +%s)",
  "amount": 1500.00,
  "currency": "USD",
  "type": "PURCHASE",
  "channel": "ONLINE",
  "merchantId": "MERCHANT-001",
  "merchantName": "Test Merchant",
  "merchantCategory": "RETAIL",
  "location": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "country": "US",
    "city": "New York",
    "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  },
  "deviceId": "DEVICE-001",
  "transactionTimestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
)
  
  echo ""
  echo -e "${YELLOW}Request:${NC}"
  echo "$REQUEST_BODY" | jq '.'
  echo ""
  
  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_URL}/fraud/assessments" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$REQUEST_BODY")
  
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Success (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Response:${NC}"
    echo "$BODY" | jq '.'
    
    # Save transaction ID for later
    echo "$TRANSACTION_ID" > /tmp/last-transaction-id.txt
    echo ""
    echo -e "${BLUE}Transaction ID saved to: /tmp/last-transaction-id.txt${NC}"
  else
    echo -e "${RED}✗ Failed (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Response:${NC}"
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
  fi
}

# Function to get assessment
get_assessment() {
  local txn_id="$1"
  
  if [ -z "$txn_id" ]; then
    if [ -f "/tmp/last-transaction-id.txt" ]; then
      txn_id=$(cat /tmp/last-transaction-id.txt)
      echo -e "${YELLOW}Using last transaction ID: ${txn_id}${NC}"
    else
      echo -e "${RED}Error: No transaction ID provided and no cached ID found${NC}"
      exit 1
    fi
  fi
  
  echo -e "${BLUE}Getting assessment for transaction: ${txn_id}${NC}"
  get_token "analyst"  # Use analyst (read-only) for this test
  
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_URL}/fraud/assessments/${txn_id}" \
    -H "Authorization: Bearer ${TOKEN}")
  
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Success (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Response:${NC}"
    echo "$BODY" | jq '.'
  else
    echo -e "${RED}✗ Failed (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Response:${NC}"
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
  fi
}

# Function to list assessments
list_assessments() {
  echo -e "${BLUE}Listing assessments${NC}"
  get_token "analyst"
  
  FROM_DATE=$(date -u -d '1 day ago' +"%Y-%m-%dT%H:%M:%SZ")
  
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET \
    "${API_URL}/fraud/assessments?transactionRiskLevels=HIGH,CRITICAL&from=${FROM_DATE}&page=0&size=10" \
    -H "Authorization: Bearer ${TOKEN}")
  
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Success (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Response:${NC}"
    echo "$BODY" | jq '.'
  else
    echo -e "${RED}✗ Failed (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Response:${NC}"
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
  fi
}

# Function to test health endpoint
test_health() {
  echo -e "${BLUE}Testing health endpoint (no auth required)${NC}"
  
  RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "${API_URL}/actuator/health")
  
  HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Success (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo "$BODY" | jq '.'
  else
    echo -e "${RED}✗ Failed (HTTP $HTTP_CODE)${NC}"
    echo ""
    echo "$BODY"
  fi
}

# Main command router
COMMAND="${1:-help}"

case "$COMMAND" in
  assess)
    assess_transaction "${2:-detector}"
    ;;
  get)
    get_assessment "$2"
    ;;
  list)
    list_assessments
    ;;
  health)
    test_health
    ;;
  help|*)
    echo "Usage: $0 [command] [args...]"
    echo ""
    echo "Commands:"
    echo "  assess [user]     - Assess a sample transaction (default user: detector)"
    echo "  get [txn-id]      - Get assessment by transaction ID (uses last if not provided)"
    echo "  list              - List high/critical risk assessments"
    echo "  health            - Test health endpoint"
    echo ""
    echo "Users: detector, analyst, admin"
    echo ""
    echo "Examples:"
    echo "  $0 assess detector    # Assess transaction as detector"
    echo "  $0 get               # Get last assessed transaction"
    echo "  $0 list              # List assessments"
    ;;
esac
