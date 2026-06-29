#!/bin/bash
# Run a full job via the orchestrator API
# Usage: ./scripts/run-job.sh <job-name>

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/auth.sh"

if [ -z "$1" ]; then
    echo "Usage: $0 <job-name>"
    exit 1
fi

JOB_NAME="$1"

# Login
login
if [ $? -ne 0 ]; then
    exit 1
fi

# Trigger the job
echo "[run-job] Triggering job '${JOB_NAME}'..."
RESPONSE=$(api_req POST "/api/jobs/name/${JOB_NAME}/run")

# Check for errors
ERR=$(echo "$RESPONSE" | grep -o '"error":"[^"]*"')
if [ -n "$ERR" ]; then
    echo "[run-job] Error: ${ERR}" >&2
    exit 1
fi

# Extract run_id
RUN_ID=$(echo "$RESPONSE" | grep -o '"runId":[0-9]*' | head -1 | cut -d: -f2)

if [ -z "$RUN_ID" ]; then
    echo "[run-job] Failed to get run ID from response" >&2
    exit 1
fi

echo "[run-job] Run #${RUN_ID} created, waiting for completion..."

# Poll until complete
RESULT=$(poll_run "$RUN_ID")
if [ $? -ne 0 ]; then
    exit 1
fi

# Extract final status
FINAL_STATUS=$(echo "$RESULT" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

# Print step details if available
if echo "$RESULT" | grep -q '"steps"'; then
    echo ""
    echo "[run-job] Step results:"

    STEP_COUNT=$(echo "$RESULT" | grep -o '"stepName"' | wc -l)
    if [ "$STEP_COUNT" -gt 0 ]; then
        echo "$RESULT" | grep -oE '"stepName":"[^"]*"' | while read -r sname; do
            sname="${sname#\"stepName\":\"}"
            sname="${sname%\"}"
            echo "  - ${sname}"
        done
    fi
fi

echo ""
echo "[run-job] Job '${JOB_NAME}' finished — Status: ${FINAL_STATUS}"

if [ "$FINAL_STATUS" = "SUCCESS" ]; then
    exit 0
else
    exit 1
fi
