# OpenTelemetry Tracing, JSON Logging & Grafana Observability (Lecture 7)

## What is configured

### 1. Microservices (`auth`, `shipment`, `dispatch`, `tracking`, `notification`)
- `spring-boot-starter-opentelemetry` (Micrometer Tracing + OTel bridge + OTLP exporter)
- `logstash-logback-encoder` (`8.1`) + `logback-spring.xml` (12-factor single-line JSON logs to `stdout` with `traceId`, `spanId`, `correlationId`, `app`)
- W3C Trace Context (`traceparent`) propagation over HTTP (`RestClient.Builder` + `@HttpExchange`) and Apache Kafka (`spring.kafka.template.observation-enabled=true`, `spring.kafka.listener.observation-enabled=true`)
- Transactional Outbox trace continuation in `shipment-service` (`OutboxTraceContext` stores `traceparent` in `outbox_events` and restores it in `OutboxRelay` before publishing to Kafka)
- Kubernetes Downward API resource attributes (`k8s.namespace.name`, `k8s.pod.name`, `k8s.node.name`) in every span via `OTEL_RESOURCE_ATTRIBUTES`
- OTLP export toggled via `k8s/monitoring/otel-exporter-config.yml` (`OTLP_TRACES_ENABLED=true`, `OTLP_TRACES_ENDPOINT=http://otel-collector:4318/v1/traces`)

### 2. Kubernetes Observability Stack (`k8s/monitoring/` & `k8s/kafka/`)
- **`k8s/kafka/kafka.yml`** — Apache Kafka (KRaft, `apache/kafka:4.3.1`) in the `delivery` namespace.
- **`k8s/monitoring/otel-collector.yml`** — OpenTelemetry Collector load-balancing tier (2 replicas, routes spans by `traceID` to `otel-gateway`).
- **`k8s/monitoring/otel-gateway.yml`** — OpenTelemetry Collector tail-sampling gateway (2 replicas, `keep-errors`, `keep-http-server-errors`, `keep-slow-traces`, `keep-manual-shipments`, `sample-successful`) exporting to Tempo.
- **`k8s/monitoring/otel-log-agent.yml`** — OpenTelemetry Collector `DaemonSet` tailing `/var/log/pods/delivery_*-service-*/*/*.log`, parsing JSON + `traceId`/`spanId`, enriching with `k8sattributes`, and pushing OTLP logs to Loki (`/otlp`).
- **`k8s/monitoring/tempo.yml`** — Grafana Tempo (`2.10.8`) with `metrics_generator` (`service-graphs`, `span-metrics`) remote-writing to Prometheus.
- **`k8s/monitoring/loki.yml`** — Grafana Loki (`3.7.8`, TSDB v13, `allow_structured_metadata: true`).
- **`k8s/monitoring/grafana.yml`** — Grafana (`12.4.13`) with provisioned Prometheus, Tempo (`tracesToLogsV2`, `tracesToMetrics`, `serviceMap`), Loki (`derivedFields` -> Tempo), and the `Delivery Tracking Observability` dashboard.

## End-to-End Verification in Kubernetes

Run the automated end-to-end & fault-injection scenario script:

```powershell
.\docs\scripts\tracing-e2e.ps1 -BaseUrl http://localhost:8080
```

See the full report in `docs/report/04-tracing-logging.md`.
