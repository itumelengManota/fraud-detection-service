#!/bin/bash

echo "=== Testcontainers Diagnostic Script ==="
echo ""

# Check if Docker is running
echo "1. Checking Docker status..."
if docker ps > /dev/null 2>&1; then
    echo "   ✅ Docker is running"
    docker version | grep "Version:" | head -2
else
    echo "   ❌ Docker is not running or not accessible"
    exit 1
fi

echo ""

# Check for existing testcontainers
echo "2. Checking for existing Testcontainers..."
CONTAINERS=$(docker ps -a --filter "label=org.testcontainers=true" --format "{{.Names}}" 2>/dev/null)
if [ -z "$CONTAINERS" ]; then
    echo "   ℹ️  No existing Testcontainers found"
else
    echo "   Found existing containers:"
    docker ps -a --filter "label=org.testcontainers=true" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
fi

echo ""

# Check testcontainers.properties
echo "3. Checking testcontainers.properties..."
PROPS_FILE="src/test/resources/testcontainers.properties"
if [ -f "$PROPS_FILE" ]; then
    echo "   ✅ File exists at: $PROPS_FILE"
    echo "   Content:"
    cat "$PROPS_FILE" | sed 's/^/      /'
else
    echo "   ❌ File not found at: $PROPS_FILE"
fi

echo ""

# Check for port conflicts
echo "4. Checking for port conflicts..."
PORTS="5432 6379 9092 8080"
for PORT in $PORTS; do
    if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "   ⚠️  Port $PORT is in use"
        lsof -Pi :$PORT -sTCP:LISTEN | grep -v "COMMAND"
    else
        echo "   ✅ Port $PORT is available"
    fi
done

echo ""

# Check Java version
echo "5. Checking Java version..."
java -version 2>&1 | head -1

echo ""

# Check Gradle
echo "6. Checking Gradle..."
if command -v gradle > /dev/null 2>&1; then
    gradle -version | head -1
else
    echo "   ⚠️  Gradle not found in PATH"
fi

echo ""
echo "=== Diagnostic Complete ==="