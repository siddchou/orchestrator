#!/bin/bash
# Run a single step via the orchestrator API
# Usage: ./scripts/run-step.sh <step-id>

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/auth.sh"

if [ -z "$1" ]; then
    echo "Usage: $0 <step-id>"
    exit 1
fi

STEP_ID="$1"

# Login
login
if [ $? -ne 0 ]; then
    exit 1
fi

# Trigger the step
echo "[run-step] Triggering step #${STEP_ID}..."
RESPONSE=$(api_req POST "/api/steps/${STEP_ID}/run")

# Check for errors
ERR=$(echo "$RESPONSE" | grep -o '"error":"[^"]*"')
if [ -n "$ERR" ]; then
    echo "[run-step] Error: ${ERR}" >&2
    exit 1
fi

# Extract run_id
RUN_ID=$(echo "$RESPONSE" | grep -o '"runId":[0-9]*' | head -1 | cut -d: -f2)

if [ -z "$RUN_ID" ]; then
    echo "[run-step] Failed to get run ID from response" >&2
    exit 1
fi

echo "[run-step] Run #${RUN_ID} created, waiting for completion..."

# Poll until complete
RESULT=$(poll_run "$RUN_ID")
if [ $? -ne 0 ]; then
    exit 1
fi

# Extract final status
FINAL_STATUS=$(echo "$RESULT" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

echo ""
echo "[run-step] Step #${STEP_ID} finished — Status: ${FINAL_STATUS}"

if [ "$FINAL_STATUS" = "SUCCESS" ]; then
    exit 0
else
    exit 1
fi
