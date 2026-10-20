# Звіт: Метрики, моніторинг та алертинг (Тема 6)

## 1. Загальна інформація

Завдання виконано командою з трьох учасників згідно з розподілом у `03-monitoring-contracts.md`:
- **Трек А:** Метрики в сервісах (Micrometer: Counter, Gauge, Timer, LongTaskTimer, DistributionSummary) та chaos-контролер.
- **Трек Б:** Маніфести Prometheus, Alertmanager, RBAC, правила алертів у `k8s/monitoring/`.
- **Трек В:** Мікросервіс `telegram-alert-bridge`, скрипти перевірки та збоїв у `docs/scripts/`, звіт та архів.

Усі компоненти працюють у просторі імен `delivery`.

---

## 2. Метрики в сервісах (Трек А)

Усі 5 сервісів експортують метрики через `/actuator/prometheus` на порту 8080:

| Тип | Назва метрики | Сервіс | Призначення |
|---|---|---|---|
| Counter | `delivery_shipments_registered_total` | `shipment-service` | Кількість створених відправлень |
| Counter | `delivery_shipments_failed_total` | `shipment-service` | Помилки створення (для алерту HighFailedShipments) |
| Counter | `delivery_notifications_dlt_total` | `notification-service` | Події, відправлені в DLT |
| Gauge | `delivery_outbox_pending` | `shipment-service` | Неопубліковані події в Outbox |
| Gauge | `delivery_couriers_available` | `dispatch-service` | Доступні кур'єри |
| Gauge | `delivery_tracking_locations_stored` | `tracking-service` | Збережені локації |
| Timer | `delivery_http_request_seconds` | `shipment-service` | Гістограма затримок HTTP (для алерту HighLatency) |
| Timer | `delivery_notifications_event_processing_seconds` | `notification-service` | Час обробки подій |
| LongTaskTimer | `delivery_outbox_relay_batch_seconds_*` | `shipment-service` | Тривалість циклу relay |
| DistributionSummary | `delivery_shipments_weight_kilograms` | `shipment-service` | Розподіл ваги посилок |
| DistributionSummary | `delivery_tracking_batch_size_ids` | `tracking-service` | Розмір пачки id |

Для тестування в `shipment-service` додано chaos-ендпоінти:
- `POST /internal/chaos/fail?rate=...` — задає відсоток збоїв (0.0 .. 1.0)
- `POST /internal/chaos/latency?ms=...` — додає штучну затримку
- `DELETE /internal/chaos` — скидає налаштування

---

## 3. Prometheus та Alertmanager (Трек Б)

### 3.1. Discovery та Targets
Prometheus знаходить поди за анотаціями:
```yaml
prometheus.io/scrape: "true"
prometheus.io/path: "/actuator/prometheus"
prometheus.io/port: "8080"
```
На сторінці `Status -> Targets` усі сервіси перебувають у статусі `UP`.

### 3.2. Правила алертів (`alert-rules.yml`)
1. **`ServiceDown`**:
   `up{job="delivery-pods", namespace="delivery"} == 0` (або `absent`), тривалість `for: 1m`, severity: `critical`.
2. **`HighFailedShipments`**:
   `sum(rate(delivery_shipments_failed_total{app="shipment-service"}[1m])) > 0.1`, `for: 1m`, severity: `warning`.
3. **`HighLatency`**:
   `histogram_quantile(0.95, sum by (le) (rate(delivery_http_request_seconds_bucket{app="shipment-service"}[1m]))) > 1`, `for: 2m`, severity: `warning`.

### 3.3. Alertmanager
Alertmanager групує алерти (`group_by: [alertname, app]`), витримує `group_wait: 10s` та надсилає webhook на `http://telegram-alert-bridge.delivery.svc:8080/alert` з прапорцем `send_resolved: true`.

---

## 4. Telegram Alert Bridge (Трек В)

Сервіс розроблено на Python (FastAPI + HTTPX):
- Приймає вебхук `POST /alert` (Alertmanager v4 schema).
- Перетворює payload у структуроване повідомлення (статус `firing` / `resolved`, `severity`, назва сервісу, `summary`, час).
- Відправляє повідомлення через Telegram Bot API (`sendMessage`) на `CHAT_ID`.
- Реалізує `GET /health` (liveness) та `GET /ready` (readiness, перевіряє наявність секретів).
- Повертає `200` при успішній доставці або `502` при помилці Telegram API, щоб Alertmanager виконав retry.

Конфігурація у кластері:
- Deployment та Service описано в `k8s/monitoring/telegram-alert-bridge.yml`.
- Секрети `BOT_TOKEN` та `CHAT_ID` створюються за шаблоном `k8s/monitoring/alertmanager-telegram.secret.yml.example` у Secret `alertmanager-telegram`.

---

## 5. Результати тестування

### 5.1. Автономний тест шлюзу (без Prometheus)
Виконано запуск `docs/scripts/test-telegram-bridge.ps1`:
- `GET /health` повернув `{"status":"up"}`
- `GET /ready` повернув `{"status":"ready"}`
- `POST /alert` успішно прийняв тестові файли `alert-firing.json` та `alert-resolved.json` і доставив їх до Telegram Bot API.

Приклад повідомлення:
```text
🚨 [FIRING] ServiceDown
Severity: critical
App: shipment-service
Namespace: delivery
Summary: Service shipment-service is down
Started: 2026-10-20 17:00:00 UTC
```

### 5.2. Сценарій 1: `ServiceDown`
Скрипт: `docs/scripts/scenario-service-down.ps1`
1. Масштабування `shipment-service` до 0 реплік (`kubectl scale --replicas=0`).
2. У Prometheus статус правила переходить:
   - `INACTIVE` -> `PENDING` (протягом 1 хвилини за параметром `for: 1m`)
   - `PENDING` -> `FIRING` після завершення інтервалу.
3. Alertmanager формує сповіщення та надсилає на `telegram-alert-bridge`.
4. У Telegram надходить повідомлення про інцидент `ServiceDown` (critical).
5. Відновлення реплік до 2 (`kubectl scale --replicas=2`).
6. Після проходження readiness-проби алерт переходить у стан `RESOLVED`, і в Telegram надходить сповіщення про завершення інциденту:
```text
✅ [RESOLVED] ServiceDown
Severity: critical
App: shipment-service
Namespace: delivery
Summary: Service shipment-service is down
Ended: 2026-10-20 17:05:00 UTC
```

### 5.3. Сценарій 2: `HighFailedShipments`
Скрипт: `docs/scripts/scenario-failed-shipments.ps1`
1. Виклик `POST /internal/chaos/fail?rate=0.8`.
2. Генерація потоку запитів на `POST /shipments`.
3. 80% запитів завершуються винятком, що фіксується лічильником `delivery_shipments_failed_total`.
4. Значення `rate(...)` перевищує поріг 0.1/с. Через 1 хвилину алерт переходить у `FIRING`, у Telegram надходить сповіщення.
5. Виклик `DELETE /internal/chaos` повертає сервіс до штатного стану, алерт закривається (`RESOLVED`).

### 5.4. Сценарій 3: `HighLatency`
Скрипт: `docs/scripts/scenario-high-latency.ps1` (або `load-test-k6.js`)
1. Виклик `POST /internal/chaos/latency?ms=1500`.
2. Генерація запитів на `GET /shipments` протягом 2 хвилин.
3. Перцентиль p95 затримки перевищує 1.0 с.
4. Після завершення вікна `for: 2m` алерт переходить у `FIRING`, у Telegram надходить сповіщення.
5. Виклик `DELETE /internal/chaos` повертає затримку до значень < 50 мс, алерт переходить у `RESOLVED`.
