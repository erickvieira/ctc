#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_ID="${USER_ID:-11111111-1111-1111-1111-111111111111}"

body_file="$(mktemp)"
trap 'rm -f "${body_file}"' EXIT

echo "==> GET ${BASE_URL}/actuator/health"
curl -fsS "${BASE_URL}/actuator/health"
echo

echo "==> POST ${BASE_URL}/events"
location="$(
	curl -fsS -D - -o "${body_file}" -X POST "${BASE_URL}/events" \
		-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
		-d '{"name":"Smoke Event","startsAt":"2026-10-15T20:00:00Z","capacity":10}' \
		| tr -d '\r' | awk -F': ' 'tolower($1) == "location" { print $2 }'
)"
echo "created at ${location}"
cat "${body_file}"
echo

echo "==> GET ${BASE_URL}${location}"
curl -fsS -H "X-User-Id: ${USER_ID}" "${BASE_URL}${location}"
echo

echo "==> GET unknown event (expect 404)"
curl -s -o "${body_file}" -w '%{http_code}\n' -H "X-User-Id: ${USER_ID}" \
	"${BASE_URL}/events/00000000-0000-0000-0000-000000000000"
cat "${body_file}"
echo

echo "==> POST event with capacity 0 (expect 400)"
curl -s -o "${body_file}" -w '%{http_code}\n' -X POST "${BASE_URL}/events" \
	-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
	-d '{"name":"Smoke Event","startsAt":"2026-10-15T20:00:00Z","capacity":0}'
cat "${body_file}"
echo
