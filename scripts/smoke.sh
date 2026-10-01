#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_ID="${USER_ID:-11111111-1111-1111-1111-111111111111}"
OTHER_USER_ID="${OTHER_USER_ID:-22222222-2222-2222-2222-222222222222}"

body_file="$(mktemp)"
trap 'rm -f "${body_file}"' EXIT

json_field() {
	python3 -c "import sys,json;print(json.load(sys.stdin)['$1'])"
}

location_of() {
	tr -d '\r' | awk -F': ' 'tolower($1) == "location" { print $2 }'
}

echo "==> GET ${BASE_URL}/actuator/health"
curl -fsS "${BASE_URL}/actuator/health"
echo

echo "==> POST ${BASE_URL}/events (capacity 2)"
event_id="$(
	curl -fsS -X POST "${BASE_URL}/events" \
		-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
		-d '{"name":"Smoke Event","startsAt":"2026-10-15T20:00:00Z","capacity":2}' | json_field id
)"
echo "event ${event_id}"

echo "==> POST ${BASE_URL}/events/${event_id}/reservations (quantity 2)"
reservation_location="$(
	curl -fsS -D - -o "${body_file}" -X POST "${BASE_URL}/events/${event_id}/reservations" \
		-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
		-d '{"quantity":2}' | location_of
)"
cat "${body_file}"
echo
echo "reservation ${reservation_location}"

echo "==> POST again (expect 409 EVENT_SOLD_OUT)"
curl -s -o "${body_file}" -w '%{http_code}\n' -X POST "${BASE_URL}/events/${event_id}/reservations" \
	-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' -d '{"quantity":1}'
cat "${body_file}"
echo

echo "==> GET ${BASE_URL}${reservation_location} as owner (expect 200)"
curl -s -o "${body_file}" -w '%{http_code}\n' -H "X-User-Id: ${USER_ID}" "${BASE_URL}${reservation_location}"
cat "${body_file}"
echo

echo "==> GET ${BASE_URL}${reservation_location} as another user (expect 404 RESERVATION_NOT_FOUND)"
curl -s -o "${body_file}" -w '%{http_code}\n' -H "X-User-Id: ${OTHER_USER_ID}" "${BASE_URL}${reservation_location}"
cat "${body_file}"
echo

echo "==> POST reservation with quantity above the maximum (expect 400 INVALID_QUANTITY)"
curl -s -o "${body_file}" -w '%{http_code}\n' -X POST "${BASE_URL}/events/${event_id}/reservations" \
	-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' -d '{"quantity":11}'
cat "${body_file}"
echo
