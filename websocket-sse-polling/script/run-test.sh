#!/bin/bash

# WebSocket Load Test Runner Script

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
BASE_URL="${BASE_URL:-localhost:8080}"
TEST_SCRIPT="${1:-websocket-load-test.js}"
OUTPUT_DIR="results"

echo -e "${GREEN}🚀 WebSocket Load Test${NC}"
echo "================================"
echo "Base URL: $BASE_URL"
echo "Script: $TEST_SCRIPT"
echo ""

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}❌ k6 is not installed${NC}"
    echo "Please install k6 first:"
    echo "  macOS: brew install k6"
    echo "  Linux: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# Check if server is running
echo -e "${YELLOW}🔍 Checking server health...${NC}"
if curl -f -s "http://${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Server is running${NC}"
else
    echo -e "${RED}❌ Server is not running on http://${BASE_URL}${NC}"
    echo "Please start the server first:"
    echo "  ./gradlew :websocket-sse-polling:bootRun"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Run test
echo ""
echo -e "${GREEN}🏃 Running test...${NC}"
echo ""

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_FILE="$OUTPUT_DIR/results_${TIMESTAMP}.json"

k6 run \
  -e BASE_URL="$BASE_URL" \
  --out "json=$OUTPUT_FILE" \
  "$TEST_SCRIPT"

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ Test completed successfully${NC}"
    echo "Results saved to: $OUTPUT_FILE"
else
    echo ""
    echo -e "${RED}❌ Test failed with exit code $EXIT_CODE${NC}"
fi

exit $EXIT_CODE
