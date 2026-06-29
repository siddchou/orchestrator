#!/bin/bash
# Shared auth helper for orchestrator CLI scripts
# Source this file: source scripts/auth.sh

# Configuration via environment variables
ORCHESTRATOR_URL="${ORCHESTRATOR_URL:-http://localhost:8080}"
ORCHESTRATOR_USER="${ORCHESTRATOR_USER:-admin}"
ORCHESTRATOR_PASS="${ORCHESTRATOR_PASS:-changeme}"

# Login and obtain JWT token
login() {
    if [ -n "$ORCHESTRATOR_TOKEN" ]; then
        JWT_TOKEN="$ORCHESTRATOR_TOKEN"
        echo "[auth] Using pre-existing token"
        return 0
    fi

    local response
    response=$(curl -s -X POST "${ORCHESTRATOR_URL}/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${ORCHESTRATOR_USER}\",\"password\":\"${ORCHESTRATOR_PASS}\"}")

    local status
    status=$(echo "$response" | grep -o '"status":"[^"]*"' | head -1)

    if echo "$status" | grep -q "ERROR"; then
        echo "[auth] Login failed: $(echo "$response" | grep -o '"error":"[^"]*"' | head -1)" >&2
        return 1
    fi

    JWT_TOKEN=$(echo "$response" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [ -z "$JWT_TOKEN" ]; then
        echo "[auth] Failed to extract token" >&2
        return 1
    fi

    echo "[auth] Logged in successfully"
    return 0
}

# Make an authenticated API request
# Usage: api_req METHOD PATH
api_req() {
    local method="$1"
    local path="$2"

    curl -s -X "$method" "${ORCHESTRATOR_URL}${path}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${JWT_TOKEN}"
}

# Poll a run until it reaches a terminal status
# Returns the final run detail JSON
poll_run() {
    local run_id="$1"
    local max_wait="${2:-300}"  # default 5 minutes
    local elapsed=0

    echo "[poll] Waiting for run #${run_id} to complete..."

    while [ "$elapsed" -lt "$max_wait" ]; do
        local response
        response=$(api_req GET "/api/runs/${run_id}")

        local status
        status=$(echo "$response" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

        case "$status" in
            SUCCESS|FAILED|PARTIAL|CANCELLED)
                echo "[poll] Run completed with status: ${status}"
                echo "$response"
                return 0
                ;;
            PENDING|RUNNING)
                sleep 3
                elapsed=$((elapsed + 3))
                ;;
            *)
                echo "[poll] Unknown status: ${status}" >&2
                return 1
                ;;
        esac
    done

    echo "[poll] Timed out waiting for run #${run_id}" >&2
    return 1
}
