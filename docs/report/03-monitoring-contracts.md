# Monitoring: shared agreements for the team

This document fixes the contracts between the three tracks so that each one can work independently. Changing any item must be agreed with everyone.

## Tracks

| Track | Responsibility | Where it works |
|---|---|---|
| A | Metrics in the services | `*-service/` |
| B | Prometheus, alert rules, Alertmanager | `k8s/monitoring/`, annotations in `k8s/*/app.yml` |
| C | Telegram bridge, load scenarios, report, archive | `telegram-alert-bridge/`, `docs/` |

## Contracts

### Cluster

- Namespace: `delivery` (`k8s/namespace.yml`). All monitoring components live in it too.
- All five services (`auth`, `dispatch`, `notification`, `shipment`, `tracking`) listen on port `8080`; the Service port is named `http`.

### Metrics endpoint

- Path: `/actuator/prometheus` on port `8080`.
- For this, every service (track A) needs the `io.micrometer:micrometer-registry-prometheus` dependency and `prometheus` in `management.endpoints.web.exposure.include`.
- Common tag in every service: `management.metrics.tags.application=<service name>`, where the name matches the one in `k8s` (`shipment-service`, etc.).

### Target discovery (B)

Prometheus finds pods by the annotations in the pod template in `k8s/*/app.yml`:

```yaml
template:
  metadata:
    annotations:
      prometheus.io/scrape: "true"
      prometheus.io/path: "/actuator/prometheus"
      prometheus.io/port: "8080"
```

Databases (`*-db`) are not scraped and have no annotations.

### Metric naming

- Prefix `delivery_`, then the domain, then the subject: `delivery_<domain>_<subject>`. Micrometer adds suffixes such as `_total`, `_seconds`, `_bytes` itself.
- Tags must have low cardinality (status, event type). Do not use `shipmentId`, `userId` or other unique values.

Metrics the alerts rely on (track A must create them under exactly these names):

| Prometheus name | Type | Service | Purpose |
|---|---|---|---|
| `delivery_shipments_failed_total` | Counter | shipment-service | failed shipments, for `HighFailedShipments` |
| `delivery_http_request_seconds` | Timer (with `publishPercentileHistogram`) | shipment-service | latency, for `HighLatency` (p95 via `_bucket`) |

Track A names the remaining metrics (Gauge, LongTaskTimer, DistributionSummary, etc.) itself and adds them to the table below.

| Prometheus name | Type | Service | Description |
|---|---|---|---|
| `delivery_shipments_registered_total` | Counter | shipment-service | shipments created (replays excluded) |
| `delivery_outbox_pending` | Gauge | shipment-service | outbox events not yet published to Kafka |
| `delivery_outbox_relay_batch_seconds_*` | LongTaskTimer | shipment-service | outbox relay cycles in progress |
| `delivery_shipments_weight_kilograms` | DistributionSummary | shipment-service | weight of created shipments |
| `delivery_notifications_dlt_total` | Counter | notification-service | shipment events moved to the DLT |
| `delivery_notifications_event_processing_seconds` | Timer | notification-service | time to process one shipment event, per attempt |
| `delivery_tracking_fallbacks_total` | Counter | tracking-service | requests answered with the fallback |
| `delivery_tracking_shipment_client_seconds` | Timer | tracking-service | calls to shipment-service per attempt, tag `operation` (get, batch, create) |
| `delivery_tracking_batch_size_ids` | DistributionSummary | tracking-service | shipment ids per batch lookup |
| `delivery_tracking_locations_stored` | Gauge | tracking-service | locations stored in the database |
| `delivery_couriers_registered_total` | Counter | dispatch-service | couriers registered |
| `delivery_couriers_capacity_kilograms` | DistributionSummary | dispatch-service | max package weight of registered couriers |
| `delivery_couriers_available` | Gauge | dispatch-service | couriers marked as available |

Details, sample output and the chaos endpoints are in `04-track-a-metrics.md`.

### Alerts

Rules are written in `k8s/monitoring/`. Agreed names that track C relies on in its test scenarios:

| Alert | Condition (approximate) | `for` | `severity` | How to trigger |
|---|---|---|---|---|
| `ServiceDown` | `up{namespace="delivery"} == 0` | 1m | critical | `kubectl scale deploy/<service> --replicas=0` |
| `HighFailedShipments` | `rate(delivery_shipments_failed_total[1m]) > 0.5` | 1m | warning | track A chaos endpoint |
| `HighLatency` | `histogram_quantile(0.95, sum by (le, application) (rate(delivery_http_request_seconds_bucket[1m]))) > 1` | 2m | warning | load (hey or k6) |

Every alert must have a `severity` label and `summary` and `description` annotations: the bridge prints them in the message.

### Telegram delivery

- Alertmanager sends a webhook to `http://telegram-alert-bridge.delivery.svc:8080/alert` in the standard format (version 4).
- The bridge reads the `BOT_TOKEN` and `CHAT_ID` variables from the `alertmanager-telegram` Secret. Template: `k8s/monitoring/alertmanager-telegram.secret.yml.example`; the real `secret.yml` is not committed (it is in `.gitignore`).
- `chat_id` can be negative (groups), so in code it is a string, not a number.
- The bridge responds `200` on successful delivery and `5xx` on a Telegram API error, so that Alertmanager retries.

### Git

- Branches: `feature/metrics`, `feature/prometheus`, `feature/telegram-alerts` (A, B, C respectively).
- No conflicts are expected: the tracks touch different files. The only shared places are `application.properties` (A only) and `k8s/*/app.yml` (B only).

## Integration point

When all three tracks are ready:

1. Apply the manifests: `kubectl apply -f k8s/monitoring/` (after creating `secret.yml` with real values).
2. Check that all services are `UP` on the Prometheus `Status → Targets` page.
3. Run the load and failure scenarios from `docs/scripts/`.
4. Watch the alert go `inactive → pending → firing` in Prometheus and the message arrive in Telegram.
5. Put the results into `docs/report/03-monitoring.md` and build the archive.

## Readiness checklist

- [x] A: `/actuator/prometheus` responds in all 5 services; Counter, Gauge, Timer, LongTaskTimer and DistributionSummary exist; the metrics table above is filled in.
- [ ] B: `promtool check rules` and `amtool check-config` pass; targets are `UP`.
- [ ] C: a test JSON sent via `curl` reaches Telegram; test scenarios and the report skeleton are ready.
