# Contract and chaos testing

Replaces the single-pact scope of `06-contract-and-load-testing.md`. Contracts now cover every service-to-service
call (HTTP and Kafka); chaos tests cover the failure modes of the three dependencies a request can hit: the
database, Kafka and another service.

## 1. Contract tests (Pact JVM 4.6.17)

Pact files live in `pacts/` (HTTP) and `pacts/messages/` (Kafka). Consumers generate them, providers verify them.
They are regenerated on every consumer test run (`pact.writer.overwrite=true`), so the files in git always match
the consumer code.

### 1.1 Coverage map

| Consumer | Provider | Transport | Interactions | Consumer test | Provider verification |
|---|---|---|---|---|---|
| web-ui | auth-service | HTTP | 3 | `web-ui/.../contract/AuthServicePactTest` | `auth-service/.../contract/AuthProviderPactTest` |
| web-ui | dispatch-service | HTTP | 13 | `DispatchServicePactTest` | `dispatch-service/.../contract/DispatchProviderPactTest` |
| web-ui | notification-service | HTTP | 4 | `NotificationServicePactTest` | `notification-service/.../contract/NotificationHttpProviderPactTest` |
| web-ui | tracking-service | HTTP | 5 | `TrackingServicePactTest` | `tracking-service/.../contract/TrackingProviderPactTest` |
| web-ui | shipment-service | HTTP | 12 | `ShipmentServicePactTest` | `shipment-service/.../contract/ShipmentProviderPactTest` |
| tracking-service | shipment-service | HTTP | 4 | `ShipmentServicePactTest` (tracking) | `ShipmentProviderPactTest` |
| notification-service | shipment-service | Kafka | 2 | `ShipmentEventsPactTest` (notification) | `ShipmentMessageProviderPactTest` |
| tracking-service | shipment-service | Kafka | 2 | `ShipmentStatusEventPactTest` | `ShipmentMessageProviderPactTest` |
| dispatch-service | shipment-service | Kafka | 3 | `ShipmentEventsPactTest` (dispatch) | `ShipmentMessageProviderPactTest` |
| shipment-service | dispatch-service | Kafka | 3 | `DispatchEventsPactTest` | `dispatch-service/.../contract/DispatchMessageProviderPactTest` |

51 interactions in total. The Kafka consumer of tracking-service is registered as `tracking-service-events` because
its HTTP pact with shipment-service already owns the file name `tracking-service-shipment-service.json`.

What each side proves:

- Consumer tests call the **real** declarative clients (`@HttpExchange` interfaces) or the **real** Kafka listeners
  against the Pact mock server / recorded messages. A field the consumer needs but the contract does not pin, or
  a change in the client, fails here.
- Provider tests start the **real** application on a random port (HTTP) or produce the message with the **real**
  outbox (Kafka) and replay every consumer's expectations. Renaming a field, changing a status code or dropping
  an event field fails here.
- Negative paths are part of the contract: 403 for the wrong courier/sender, 404 for unknown parcel or user,
  422 for an unknown settlement, 400 for a non-positive user id, 204 for "no current offer".

Provider-side set-up:

| Provider | Database | What is stubbed |
|---|---|---|
| shipment-service | real Postgres (Testcontainers), seeded per state | routing (offline client) |
| auth-service | real Postgres, seeded per state | nothing |
| notification-service | real Postgres, seeded per state | nothing |
| tracking-service | real Postgres for location history | the shipment-service client and the location recorder |
| dispatch-service | Postgres only for start-up | the service layer (`CourierService`, `DispatchService`, `ShipmentCourierService`), so the test pins the wire format, not dispatch rules |

Why a real port instead of `MockMvcTestTarget`: the `au.com.dius.pact.provider:spring6` module is built against
Spring 6 and fails with `NoSuchMethodError` on Spring 7 / Spring Boot 4.1 (`MockHttpServletRequestBuilder.headers`),
so it is not used.

### 1.2 Not covered by Pact

- **gRPC** (web-ui, notification, dispatch -> tracking-service): Pact JVM 4.6 has no gRPC support here. The `.proto`
  files in `tracking-api` are the shared contract and already have integration tests per call model
  (`ShipmentTracking*IntegrationTest`).
- **Geoapify** (shipment-service -> external API): covered by `GeoapifyRoutingClientTest`.
- The `Idempotent-Replayed` response header and pagination parameters are not pinned.

## 2. Chaos tests

Each experiment states a steady state, injects one fault, and asserts the system degrades the way the design says it
should and recovers without a restart.

### 2.1 In-JVM experiments (run with `mvn test`, need Docker)

| Class | Fault injected | Steady state asserted |
|---|---|---|
| `shipment-service/.../chaos/ShipmentChaosIntegrationTest` | Postgres behind **Toxiproxy**: 600 ms latency, connection cut, half-open hang (no answer); plus `/internal/chaos` failure rate 1, 0.5 and 700 ms latency | reads stay correct but slower; a dead DB fails fast (5xx, no stack trace) and the service heals by itself; a hung connection is released by the socket timeout; **a shipment is created completely (row + outbox event + consumed quote) or not at all**, and a retry with the same idempotency key succeeds |
| `shipment-service/.../chaos/OutboxKafkaChaosIntegrationTest` | Kafka down, Kafka never answers (metadata timeout), Kafka flapping every second call | events wait in the outbox, are delivered after recovery, none lost, none delivered twice once acknowledged |
| `tracking-service/.../client/ShipmentClientChaosIntegrationTest` | WireMock faults: connection reset, empty response, truncated chunk, random bytes, non-JSON body, 4.5 s delay (read timeout is 3 s), burst of 40 callers | single lookup degrades to an explicit `UNAVAILABLE` answer; batch/create answer a clean 503; 4xx is never retried; the breaker opens, stops calling the sick service, half-opens and closes by itself; the bulkhead sheds load with 503 |
| `shipment-service/.../chaos/ChaosControllerTest` | the fault-injection switch itself | validated input, observable state, full reset |

Existing consumer-side chaos coverage (poison messages, duplicate delivery, dead-letter topics) already lives in
`ShipmentEventListenerDltIntegrationTest`, `IdempotentShipmentEventHandlerTest` and
`DispatchFlowIntegrationTest#duplicateShipmentEventIsProcessedOnce`, so it was not duplicated.

### 2.2 Black-box experiments on the running stack

`docs/scripts/chaos/run-chaos.sh` stops and restarts real containers (`docker compose stop/start`, nothing is
removed; an `EXIT` trap restores anything left stopped).

| Id | Fault | Hypothesis |
|---|---|---|
| E1 | stop shipment-service | tracking answers within 15 s with the fallback or 503, no stack trace; heals after restart |
| E2 | stop Kafka | shipments are still created; the notification arrives after Kafka returns |
| E3 | stop notification-service | shipment creation unaffected; the missed notification is delivered later |
| E4 | stop dispatch-service | shipment creation unaffected |
| E5 | stop shipment database | shipment-service fails fast, health is DOWN, recovers without restart |
| E6 | `/internal/chaos` (needs `APP_CHAOS_ENABLED=true`) | creation fails at rate 1, works after reset |

```bash
docs/scripts/chaos/run-chaos.sh            # preflight + dry run, stops nothing
docs/scripts/chaos/run-chaos.sh --yes      # run all experiments
docs/scripts/chaos/run-chaos.sh --yes --only E2,E5
```

The preflight refuses to run against images older than the code (it probes `/notifications/mine/unread`). The
stack that was running when these tests were written was **stale** (notification-service answered 404 for that
endpoint), so E1-E6 were not executed; rebuild first with `docker compose up -d --build`.

### 2.3 Do chaos tests need the whole architecture?

No. Section 2.1 needs only the one service under test plus the dependency being broken (Postgres + Toxiproxy,
WireMock, or a fault-injecting Kafka template). The full compose stack is only needed for section 2.2, which checks
end-to-end behaviour across several real services.

## 3. Findings

1. **Outbox relay reordered events of the same shipment after a transient Kafka failure (fixed).**
   `OutboxRelay.publishBatch` fired all sends without waiting, so when an earlier event failed the later ones were
   already out, and the retry reached Kafka after them (a stale `SHIPMENT_DETAILS_UPDATED` could overwrite a newer
   one). Both `shipment-service` and `dispatch-service` relays now await each send and hold back the rest of that
   key's events in the batch after a failure; other keys keep flowing. Regression tests:
   `OutboxKafkaChaosIntegrationTest` in shipment-service and dispatch-service. Trade-off: sends are sequential
   (10 s timeout each), so throughput per cycle is lower than with fire-and-forget.
2. A 200 response from shipment-service with a non-JSON body makes tracking-service answer a generic 500
   (`urn:delivery:problem:internal-error`) instead of the 503 / fallback used for every other transport fault
   (asserted only as "no stack trace leaks").
3. `GET /shipments/{id}` on tracking-service returns **200** with `"status":"UNAVAILABLE"` when shipment-service is
   down. This is the designed fallback; callers must check the status field.
4. Provider contract fix found while verifying: `POST /shipments/quotes` answers **201**, not 200.

## 4. How to run

```bash
# consumers first (they write the pact files), then providers
for m in web-ui notification-service tracking-service dispatch-service shipment-service auth-service; do
  (cd $m && mvn test)
done

# only contracts / only chaos in one module
cd shipment-service && mvn test -Dtest='*PactTest,*ProviderPactTest'
cd shipment-service && mvn test -Dtest='*Chaos*'
```
