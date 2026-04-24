#!/bin/bash

# Script to obtain JWT tokens from Keycloak for local testing
# Usage: ./scripts/get-token.sh [username]
#   username: detector (default), analyst, or admin

set -e

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="${REALM:-fraud-detection}"
CLIENT_ID="${CLIENT_ID:-fraud-detection-web}"
CLIENT_SECRET="${CLIENT_SECRET:-v7tcA1Ku4mARrZwO6tuC3g36CmqZn8xp}"

# User credentials
USERNAME="${1:-detector}"

case "$USERNAME" in
  detector)
    PASSWORD="detector123"
    ;;
  analyst)
    PASSWORD="analyst123"
    ;;
  admin)
    PASSWORD="admin123"
    ;;
  *)
    echo "Unknown user: $USERNAME"
    echo "Valid users: detector, analyst, admin"
    exit 1
    ;;
esac

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}Obtaining token for user: ${USERNAME}${NC}"
echo ""

# Get token
TOKEN_URL="${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token"

RESPONSE=$(curl -s -X POST "$TOKEN_URL" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}" \
  -d "scope=openid profile email")

# Check if request was successful
if echo "$RESPONSE" | grep -q "access_token"; then
  ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r '.access_token')
  EXPIRES_IN=$(echo "$RESPONSE" | jq -r '.expires_in')
  
  echo -e "${GREEN}✓ Token obtained successfully${NC}"
  echo ""
  echo -e "${YELLOW}Access Token:${NC}"
  echo "$ACCESS_TOKEN"
  echo ""
  echo -e "${YELLOW}Expires in:${NC} ${EXPIRES_IN} seconds"
  echo ""
  echo -e "${YELLOW}Bearer Token (for Authorization header):${NC}"
  echo "Bearer $ACCESS_TOKEN"
  echo ""
  
  # Decode token (first part only to show claims)
  echo -e "${YELLOW}Token Claims:${NC}"
  echo "$ACCESS_TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq '.' || echo "Unable to decode token"
  echo ""
  
  # Export for use in other scripts
  export FRAUD_DETECTION_TOKEN="$ACCESS_TOKEN"
  echo -e "${BLUE}Token exported as: \$FRAUD_DETECTION_TOKEN${NC}"
  
  # Save to file for convenience
  echo "$ACCESS_TOKEN" > /tmp/fraud-detection-token.txt
  echo -e "${BLUE}Token saved to: /tmp/fraud-detection-token.txt${NC}"
  
else
  echo -e "${RED}✗ Failed to obtain token${NC}"
  echo "$RESPONSE" | jq '.' || echo "$RESPONSE"
  exit 1
fi
