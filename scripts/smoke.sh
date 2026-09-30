#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "==> GET ${BASE_URL}/actuator/health"
curl -fsS "${BASE_URL}/actuator/health"
echo
