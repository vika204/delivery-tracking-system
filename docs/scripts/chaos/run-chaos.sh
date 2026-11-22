#!/usr/bin/env bash
# Black-box chaos experiments against the running Docker Compose stack.
#
# Every experiment follows the same loop: check the steady state -> inject a fault -> check the hypothesis ->
# remove the fault -> check recovery. Faults are `docker compose stop/start`, so nothing is deleted and the
# stack is restored even if the script is interrupted (EXIT trap).
#
# Only the services an experiment breaks plus their neighbours are needed. The experiments that create
# shipments need a working quote step (GEOAPIFY_API_KEY set, or the offline routing client); if the preflight
# quote fails those experiments are skipped, not failed.
#
#   docs/scripts/chaos/run-chaos.sh --list
#   docs/scripts/chaos/run-chaos.sh --yes                 # run everything
#   docs/scripts/chaos/run-chaos.sh --yes --only E2,E5    # run a subset
#
# Without --yes the script only runs the preflight (steady state) and prints what it would do.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/docker-compose.yml")

AUTH_URL="${AUTH_URL:-http://localhost:8081}"
SHIPMENT_URL="${SHIPMENT_URL:-http://localhost:8082}"
DISPATCH_URL="${DISPATCH_URL:-http://localhost:8083}"
TRACKING_URL="${TRACKING_URL:-http://localhost:8084}"
NOTIFICATION_URL="${NOTIFICATION_URL:-http://localhost:8085}"
USER_ID="${CHAOS_USER_ID:-424242}"
RECOVERY_TIMEOUT="${RECOVERY_TIMEOUT:-120}"
FAST_FAIL_SECONDS="${FAST_FAIL_SECONDS:-15}"

KYIV="UA80000000000093317"
LVIV="UA46060250010015970"

ALL_EXPERIMENTS=(E1 E2 E3 E4 E5 E6)
title() { # portable (macOS ships bash 3.2, which has no associative arrays)
  case "$1" in
    E1) echo "shipment-service down: tracking degrades instead of hanging, then heals" ;;
    E2) echo "Kafka down: shipments are still created (outbox) and the notification arrives after Kafka returns" ;;
    E3) echo "notification-service down: shipment creation is unaffected, the missed notification is delivered later" ;;
    E4) echo "dispatch-service down: shipment creation is unaffected" ;;
    E5) echo "shipment database down: shipment-service fails fast and recovers without a restart" ;;
    E6) echo "application-level fault injection (/internal/chaos) on shipment-service" ;;
  esac
}

RUN=false
ONLY=""
STOPPED=()
PASSED=0
FAILED=0
SKIPPED=0
CAN_CREATE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes) RUN=true ;;
    --only) ONLY="${2:-}"; shift ;;
    --list) for e in "${ALL_EXPERIMENTS[@]}"; do echo "$e  $(title "$e")"; done; exit 0 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

log()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok()   { printf '  \033[32mPASS\033[0m %s\n' "$*"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$*"; EXPERIMENT_OK=false; }
info() { printf '  .. %s\n' "$*"; }

http_code() { curl -s -o /dev/null -m "${2:-10}" -w '%{http_code}' "$1" 2>/dev/null || echo 000; }

wait_until() { # wait_until <seconds> <description> <command...>
  local limit="$1" what="$2"; shift 2
  local start=$SECONDS
  until "$@" >/dev/null 2>&1; do
    if (( SECONDS - start >= limit )); then return 1; fi
    sleep 2
  done
  info "$what after $((SECONDS - start))s"
}

healthy() { [[ "$(http_code "$1/actuator/health")" == "200" ]]; }

stop_service()  { info "docker compose stop $1"; "${COMPOSE[@]}" stop "$1" >/dev/null 2>&1; STOPPED+=("$1"); }
start_service() {
  info "docker compose start $1"
  "${COMPOSE[@]}" start "$1" >/dev/null 2>&1
  local rest=() s; for s in "${STOPPED[@]:-}"; do [[ "$s" != "$1" ]] && rest+=("$s"); done
  STOPPED=("${rest[@]:-}")
}

restore_all() {
  local s
  for s in "${STOPPED[@]:-}"; do
    [[ -n "$s" ]] && { echo "restoring $s"; "${COMPOSE[@]}" start "$s" >/dev/null 2>&1; }
  done
}
trap restore_all EXIT

create_shipment() { # prints "<id>" on success, nothing otherwise
  local quote quote_id body
  quote="$(curl -s -m 20 -X POST "$SHIPMENT_URL/shipments/quotes" -H "X-User-Id: $USER_ID" -H 'Content-Type: application/json' \
    -d "{\"pickupAddress\":\"Khreshchatyk 1\",\"pickupCityCode\":\"$KYIV\",\"deliveryAddress\":\"Svobody 10\",\"deliveryCityCode\":\"$LVIV\",\"weight\":2.5}")" || return 1
  quote_id="$(jq -r '.quoteId // empty' <<<"$quote" 2>/dev/null)"
  [[ -n "$quote_id" ]] || return 1
  body="$(curl -s -m 20 -w '\n%{http_code}' -X POST "$SHIPMENT_URL/shipments" -H "X-User-Id: $USER_ID" \
    -H "Idempotency-Key: chaos-$(date +%s%N)-$RANDOM" -H 'Content-Type: application/json' \
    -d "{\"quoteId\":\"$quote_id\",\"senderName\":\"Chaos Sender\",\"senderPhone\":\"+380501234567\",\"recipientName\":\"Chaos Recipient\",\"recipientPhone\":\"+380671234567\",\"length\":30,\"width\":20,\"height\":15}")" || return 1
  [[ "$(tail -n1 <<<"$body")" == "200" ]] || return 1
  jq -r '.shipmentId' <<<"$(sed '$d' <<<"$body")"
}

notification_count() { # notifications of the chaos user that mention the shipment
  curl -s -m 10 "$NOTIFICATION_URL/notifications/mine?limit=100" -H "X-User-Id: $USER_ID" \
    | jq --argjson id "$1" '[.[] | select(.shipmentId == $id)] | length' 2>/dev/null || echo 0
}

tracking_status() { curl -s -m 15 "$TRACKING_URL/tracking/shipments/$1" | jq -r '.status // empty' 2>/dev/null; }

# ------------------------------------------------------------------------------------------------------------

preflight() {
  log "Preflight: steady state"
  local url name missing=false
  for pair in "auth|$AUTH_URL" "shipment|$SHIPMENT_URL" "dispatch|$DISPATCH_URL" "tracking|$TRACKING_URL" "notification|$NOTIFICATION_URL"; do
    name="${pair%%|*}"; url="${pair#*|}"
    if healthy "$url"; then ok "$name-service is UP ($url)"; else printf '  \033[31mDOWN\033[0m %s-service (%s)\n' "$name" "$url"; missing=true; fi
  done
  $missing && { echo "The stack is not fully up: docker compose up -d" >&2; exit 1; }
  # The experiments assert on current behaviour, so refuse to run against images built from older code.
  local probe; probe="$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$NOTIFICATION_URL/notifications/mine/unread" -H "X-User-Id: $USER_ID")"
  if [[ "$probe" != "200" ]]; then
    printf '  \033[31mSTALE\033[0m notification-service does not serve /notifications/mine/unread (HTTP %s): the running images are older than the code.\n' "$probe" >&2
    echo "Rebuild first: docker compose up -d --build" >&2
    exit 1
  fi
  ok "running images serve the current API"
  local id; id="$(create_shipment || true)"
  if [[ -n "$id" ]]; then
    CAN_CREATE=true; ok "a shipment can be created end to end (id $id)"
  else
    info "cannot create a shipment (quote step unavailable): experiments that create shipments will be skipped"
  fi
}

e1() {
  local id; id="$(create_shipment || true)"
  [[ -n "$id" ]] || { SKIP_REASON="no shipment can be created"; return 2; }
  [[ "$(tracking_status "$id")" == "WAITING_FOR_COURIER" ]] && ok "steady state: tracking sees shipment $id" || bad "steady state: tracking cannot see shipment $id"
  stop_service shipment-service
  local start=$SECONDS code body
  body="$(curl -s -m "$FAST_FAIL_SECONDS" -w '\n%{http_code}' "$TRACKING_URL/tracking/shipments/$id")"; code="$(tail -n1 <<<"$body")"
  local took=$((SECONDS - start))
  (( took < FAST_FAIL_SECONDS )) && ok "tracking answered in ${took}s (no hang)" || bad "tracking took ${took}s"
  if [[ "$code" == "200" && "$(jq -r '.status' <<<"$(sed '$d' <<<"$body")" 2>/dev/null)" == "UNAVAILABLE" ]]; then ok "tracking degraded to the UNAVAILABLE fallback"
  elif [[ "$code" == "503" ]]; then ok "tracking answered 503 shipment-unavailable"
  else bad "unexpected tracking answer: HTTP $code"; fi
  grep -qiE 'exception|at com\.|org\.springframework' <<<"$body" && bad "stack trace leaked to the caller" || ok "no stack trace leaked"
  start_service shipment-service
  wait_until "$RECOVERY_TIMEOUT" "shipment-service healthy again" healthy "$SHIPMENT_URL" || bad "shipment-service did not come back"
  wait_until "$RECOVERY_TIMEOUT" "tracking serves real data again" \
    bash -c "[[ \"\$(curl -s -m 10 $TRACKING_URL/tracking/shipments/$id | jq -r .status)\" == WAITING_FOR_COURIER ]]" \
    && ok "tracking healed without a restart" || bad "tracking did not heal"
}

e2() {
  $CAN_CREATE || { SKIP_REASON="no shipment can be created"; return 2; }
  stop_service kafka
  local id; id="$(create_shipment || true)"
  [[ -n "$id" ]] && ok "shipment $id created while Kafka is down (event parked in the outbox)" || bad "shipment creation failed while Kafka is down"
  [[ "$(http_code "$SHIPMENT_URL/shipments/${id:-0}")" == "200" ]] && ok "the shipment is readable" || bad "the shipment is not readable"
  info "notifications while Kafka is down: $(notification_count "${id:-0}")"
  start_service kafka
  wait_until "$RECOVERY_TIMEOUT" "Kafka healthy" bash -c "[[ \"\$(docker inspect -f '{{.State.Health.Status}}' delivery-kafka 2>/dev/null)\" == healthy ]]" || info "Kafka health not reported, continuing"
  wait_until "$RECOVERY_TIMEOUT" "notification for shipment $id delivered" bash -c "[[ \"\$($(declare -f notification_count) ; NOTIFICATION_URL=$NOTIFICATION_URL USER_ID=$USER_ID notification_count ${id:-0})\" -ge 1 ]]" \
    && ok "the parked event reached notification-service after Kafka returned" || bad "no notification after Kafka returned (event lost?)"
}

e3() {
  $CAN_CREATE || { SKIP_REASON="no shipment can be created"; return 2; }
  stop_service notification-service
  local id; id="$(create_shipment || true)"
  [[ -n "$id" ]] && ok "shipment $id created while notification-service is down" || bad "shipment creation depends on notification-service"
  start_service notification-service
  wait_until "$RECOVERY_TIMEOUT" "notification-service healthy" healthy "$NOTIFICATION_URL" || bad "notification-service did not come back"
  wait_until "$RECOVERY_TIMEOUT" "missed notification delivered" bash -c "[[ \"\$($(declare -f notification_count) ; NOTIFICATION_URL=$NOTIFICATION_URL USER_ID=$USER_ID notification_count ${id:-0})\" -ge 1 ]]" \
    && ok "the notification was not lost (Kafka retained the event)" || bad "the notification was lost"
}

e4() {
  $CAN_CREATE || { SKIP_REASON="no shipment can be created"; return 2; }
  stop_service dispatch-service
  local id; id="$(create_shipment || true)"
  [[ -n "$id" ]] && ok "shipment $id created while dispatch-service is down" || bad "shipment creation depends on dispatch-service"
  [[ "$(http_code "$SHIPMENT_URL/shipments/${id:-0}")" == "200" ]] && ok "shipment still readable" || bad "shipment not readable"
  start_service dispatch-service
  wait_until "$RECOVERY_TIMEOUT" "dispatch-service healthy" healthy "$DISPATCH_URL" && ok "dispatch-service recovered" || bad "dispatch-service did not come back"
}

e5() {
  local id; id="$(create_shipment || true)"
  [[ -n "$id" ]] || id=1
  stop_service shipment-db
  local start=$SECONDS code
  code="$(http_code "$SHIPMENT_URL/shipments/$id" "$FAST_FAIL_SECONDS")"
  local took=$((SECONDS - start))
  [[ "$code" =~ ^5 ]] && ok "dead database -> HTTP $code" || bad "dead database -> HTTP $code (expected 5xx)"
  (( took < FAST_FAIL_SECONDS )) && ok "failed in ${took}s (no hang)" || bad "took ${took}s"
  [[ "$(http_code "$SHIPMENT_URL/actuator/health")" == "503" ]] && ok "health endpoint reports DOWN" || info "health endpoint did not report DOWN"
  start_service shipment-db
  wait_until "$RECOVERY_TIMEOUT" "shipment-service answers again" \
    bash -c "[[ \"\$(curl -s -o /dev/null -m 10 -w '%{http_code}' $SHIPMENT_URL/shipments/$id)\" =~ ^(200|404)$ ]]" \
    && ok "recovered without restarting shipment-service" || bad "shipment-service did not recover from the database outage"
}

e6() {
  local state; state="$(http_code "$SHIPMENT_URL/internal/chaos")"
  if [[ "$state" != "200" ]]; then SKIP_REASON="/internal/chaos is disabled (set APP_CHAOS_ENABLED=true for shipment-service)"; return 2; fi
  $CAN_CREATE || { SKIP_REASON="no shipment can be created"; curl -s -X DELETE "$SHIPMENT_URL/internal/chaos" >/dev/null; return 2; }
  curl -s -X POST "$SHIPMENT_URL/internal/chaos/fail?rate=1" >/dev/null
  local id; id="$(create_shipment || true)"
  [[ -z "$id" ]] && ok "creation fails while the injected failure rate is 1" || bad "creation succeeded despite rate=1"
  curl -s -X DELETE "$SHIPMENT_URL/internal/chaos" >/dev/null
  id="$(create_shipment || true)"
  [[ -n "$id" ]] && ok "creation works again after reset (shipment $id)" || bad "creation still failing after reset"
}

# ------------------------------------------------------------------------------------------------------------

preflight

if ! $RUN; then
  log "Dry run"
  for e in "${ALL_EXPERIMENTS[@]}"; do echo "  $e  $(title "$e")"; done
  echo; echo "Nothing was stopped. Re-run with --yes to execute (containers are stopped and restarted, never removed)."
  exit 0
fi

for e in "${ALL_EXPERIMENTS[@]}"; do
  [[ -n "$ONLY" && ",$ONLY," != *",$e,"* ]] && continue
  log "$e: $(title "$e")"
  EXPERIMENT_OK=true; SKIP_REASON=""
  "$(tr 'A-Z' 'a-z' <<<"$e")"; rc=$?
  if (( rc == 2 )); then printf '  \033[33mSKIP\033[0m %s\n' "$SKIP_REASON"; SKIPPED=$((SKIPPED + 1))
  elif $EXPERIMENT_OK; then PASSED=$((PASSED + 1)); else FAILED=$((FAILED + 1)); fi
done

log "Summary: $PASSED passed, $FAILED failed, $SKIPPED skipped"
(( FAILED == 0 ))
