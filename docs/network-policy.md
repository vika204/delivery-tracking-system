# NetworkPolicy — Group Assignment 8

## Implemented

Added Kubernetes NetworkPolicy configuration in `k8s/network-policy/` (7 YAML files, 17 policies).

- **Default Deny** — blocks incoming traffic by default in the `delivery` namespace.
- **HTTP access** — allows traffic from Ingress Controller and required service-to-service communication.
- **PostgreSQL** — restricts database access to the corresponding business services.
- **Kafka** — allows connections from shipment-service and notification-service.
- **Observability** — adds required permissions for OpenTelemetry, Tempo, Prometheus, Loki, Grafana and alerting.

## Verification

- All 17 policies passed `kubectl apply --dry-run=client`.
- HTTP routing through Ingress returned `HTTP 200` in the original cluster.
- NetworkPolicy enforcement was tested in a separate Minikube cluster (`np-test`) with Calico.

Test results:
- Authorized service → test DB: **allowed**.
- Unauthorized Pod → test DB: **blocked**.
- Ingress-labeled Pod → business service: **allowed**.
- Unauthorized Pod → business service: **blocked**.

Testing used simulated services on ports 5432 and 8080.

## Notes for the Team

The original Minikube cluster uses **Kindnet**, which does not enforce NetworkPolicy. A separate Calico cluster was used for testing without modifying the original environment.

Before final integration, verify that the cluster supports NetworkPolicy and that the full system still works with all policies applied, including Kafka and observability.

Detailed test results and screenshots are included in the group word report.