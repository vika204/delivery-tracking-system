# OpenTelemetry Tracing & JSON Logging

## What's already done

OpenTelemetry is configured in all 5 services:

- `auth-service` — port 8081
- `shipment-service` — port 8082
- `dispatch-service` — port 8083
- `tracking-service` — port 8084
- `notification-service` — port 8085

Each service has:
- `spring-boot-starter-opentelemetry`
- `logstash-logback-encoder` (8.1)
- `logback-spring.xml` for JSON logs to stdout
- W3C Trace Context propagation
- 100% tracing sampling
- Configurable OTLP trace export

Existing Prometheus metrics and `X-Correlation-Id` handling are preserved.

## Tracing configuration

These properties are already added to each service's `application.properties`:

```properties
management.tracing.sampling.probability=1.0
management.tracing.propagation.type=w3c

management.tracing.export.otlp.enabled=${OTLP_TRACES_ENABLED:false}
management.opentelemetry.tracing.export.otlp.endpoint=${OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}

management.otlp.metrics.export.enabled=false
```

**OTLP export is disabled by default** so the services can run locally without a Collector.

To enable it in Kubernetes, set these environment variables for all 5 services:

```env
OTLP_TRACES_ENABLED=true
OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces
```

Replace `otel-collector` with the actual Kubernetes Service DNS name if needed.

The Collector must accept OTLP/HTTP on port `4318`, with traces sent to `/v1/traces`.

## HTTP tracing

HTTP propagation was tested between:

```text
tracking-service
    |
    | RestClient + W3C traceparent
    v
shipment-service
```

`tracking-service` already uses an injected, auto-configured `RestClient.Builder`, so no manual `traceparent` handling is needed for this HTTP call.

Test request:

```powershell
curl.exe -i -H "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" http://localhost:8084/tracking/shipments/1
```

Verified results:

| Field | tracking-service | shipment-service |
|---|---|---|
| traceId | `4bf92f3577b34da6a3ce929d0e0e4736` | `4bf92f3577b34da6a3ce929d0e0e4736` |
| spanId | `17a9ad1e155a0dff` | `3b5d26cdb46d01a7` |
| correlationId | Same | Same |

The TraceId is shared across both services, while each service has its own SpanId.

## JSON logs

All services write structured JSON logs to stdout.

Logs include:
- `@timestamp`
- `level`
- `message`
- `app`
- `traceId` and `spanId` when a tracing context is active
- `correlationId` when available

Startup logs normally don't contain TraceId or SpanId because there's no active trace.

To inspect logs:

```powershell
docker compose logs --no-log-prefix --tail=100 tracking-service shipment-service
```

The `tracking-service` and `shipment-service` controllers have additional INFO logs for verifying HTTP tracing.

## For Kafka tracing

OpenTelemetry dependencies are already available in `shipment-service` and `notification-service`.

Still needed:

1. Propagate `traceparent` through Kafka record headers.
2. Handle the existing Transactional Outbox flow in `shipment-service`.
3. Store the trace context when creating an outbox event.
4. Restore that context in `OutboxRelay` before publishing to Kafka.
5. Extract the context in `notification-service` and create consumer spans linked to the original trace.
6. Verify tracing through retries and DLT where applicable.

**Important:** The Outbox relay runs asynchronously, so the original HTTP tracing context won't automatically be available when the relay publishes the event.

## For Collector, Tempo, Loki and Grafana

Still needed:

1. Deploy OpenTelemetry Collector, Tempo and Loki in Kubernetes.
2. Enable OTLP export in all services using the environment variables above.
3. Configure the Collector to receive OTLP traces and export them to Tempo.
4. Configure tail sampling to retain all traces containing errors.
5. Collect container stdout JSON logs into Loki.
6. Configure Grafana to navigate from a Tempo span to Loki logs using the TraceId.

The JSON logging configuration is already present, so there's no need to add another logging encoder.

## Verified locally

- All 5 services started successfully in Docker Compose.
- `GET /users` — HTTP 200.
- `GET /couriers` — HTTP 200.
- `GET /notifications` — HTTP 200.
- All 5 services produce JSON logs.
- HTTP Trace Context propagation works between `tracking-service` and `shipment-service`.
- Existing `X-Correlation-Id` propagation still works.

## Remaining integration test

Once Kafka tracing and Kubernetes infrastructure are ready, we need to verify:

- One complete trace involving at least 3 microservices and Kafka.
- Correct parent-child relationships across HTTP and Kafka.
- Spans visible in Grafana Tempo.
- Error traces retained by Collector tail sampling.
- Related JSON logs visible in Loki.
- Navigation from Tempo spans to Loki logs.

**HTTP tracing and JSON logging are implemented and locally verified. Kafka propagation and the full Collector/Grafana integration are not yet verified.**