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

expect() {
	local expected="$1"
	shift
	local description="$1"
	shift
	local code
	code="$(curl -s -o "${body_file}" -w '%{http_code}' "$@")"
	if [[ "${code}" != "${expected}" ]]; then
		printf 'FAIL %s: expected %s, got %s\n' "${description}" "${expected}" "${code}" >&2
		cat "${body_file}" >&2
		printf '\n' >&2
		exit 1
	fi
	printf 'ok   %-52s -> %s\n' "${description}" "${code}"
}

echo "==> ${BASE_URL}"

expect 200 "GET /actuator/health" "${BASE_URL}/actuator/health"

event_id="$(
	curl -fsS -X POST "${BASE_URL}/events" \
		-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
		-d '{"name":"Smoke Event","startsAt":"2026-10-15T20:00:00Z","capacity":3}' | json_field id
)"
echo "    event ${event_id}"

expect 200 "GET /events/{id}" \
	-H "X-User-Id: ${USER_ID}" "${BASE_URL}/events/${event_id}"

reservation_location="$(
	curl -fsS -D - -o "${body_file}" -X POST "${BASE_URL}/events/${event_id}/reservations" \
		-H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
		-d '{"quantity":2}' | location_of
)"
echo "    reservation ${reservation_location}"

expect 200 "GET /reservations/{id} (owner)" \
	-H "X-User-Id: ${USER_ID}" "${BASE_URL}${reservation_location}"
expect 404 "GET /reservations/{id} (another user)" \
	-H "X-User-Id: ${OTHER_USER_ID}" "${BASE_URL}${reservation_location}"

expect 204 "POST /reservations/{id}/confirmation" \
	-X POST -H "X-User-Id: ${USER_ID}" "${BASE_URL}${reservation_location}/confirmation"
expect 204 "POST /reservations/{id}/confirmation (idempotent)" \
	-X POST -H "X-User-Id: ${USER_ID}" "${BASE_URL}${reservation_location}/confirmation"

expect 204 "DELETE /reservations/{id}" \
	-X DELETE -H "X-User-Id: ${USER_ID}" "${BASE_URL}${reservation_location}"
expect 204 "DELETE /reservations/{id} (idempotent)" \
	-X DELETE -H "X-User-Id: ${USER_ID}" "${BASE_URL}${reservation_location}"

expect 201 "POST /events/{id}/reservations (refill)" \
	-X POST -H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
	-d '{"quantity":3}' "${BASE_URL}/events/${event_id}/reservations"
expect 409 "POST /events/{id}/reservations (sold out)" \
	-X POST -H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
	-d '{"quantity":1}' "${BASE_URL}/events/${event_id}/reservations"
expect 400 "POST /events/{id}/reservations (quantity above max)" \
	-X POST -H "X-User-Id: ${USER_ID}" -H 'Content-Type: application/json' \
	-d '{"quantity":11}' "${BASE_URL}/events/${event_id}/reservations"

echo "smoke OK"
