# gRPC Unary та Client Streaming: реалізація, UNAVAILABLE і retries

**Частина №2.**

| Модель | RPC | Клієнт → сервер | Сценарій |
|---|---|---|---|
| Unary | `GetShipmentDeliveryInfo` | `notification-service` → `tracking-service` | поточний статус доставки та остання GPS-координата одним запитом |
| Client Streaming | `RecordShipmentRoute` | `dispatch-service` → `tracking-service` | застосунок кур'єра після відновлення зв'язку відправляє накопичені координати |

Частина №1 (Server Streaming, `notification-service`) не змінювалася. Нові RPC додано в той самий protobuf-сервіс `ShipmentTrackingService` і в той самий `@GrpcService` (`ShipmentTrackingGrpcService`), як домовлено в `docs/grpc-server-streaming.md`. Номери полів наявних повідомлень збережено.

## 1. Контракт

`tracking-api/src/main/proto/delivery/tracking/v1/tracking.proto`:

```protobuf
rpc GetShipmentDeliveryInfo(GetShipmentDeliveryInfoRequest) returns (ShipmentDeliveryInfo);   // Unary
rpc RecordShipmentRoute(stream ShipmentRoutePoint) returns (ShipmentRouteSummary);            // Client streaming

message ShipmentDeliveryInfo {
  int64 shipment_id = 1;
  string status = 2;        // статус із shipment-service: IN_TRANSIT, DELIVERED, ...
  bool has_location = 3;    // false, якщо координат ще немає
  double latitude = 4;
  double longitude = 5;
  google.protobuf.Timestamp recorded_at = 6;
}

message ShipmentRoutePoint {
  string upload_id = 1;     // ключ ідемпотентності, однаковий для всіх точок потоку
  int64 shipment_id = 2;
  double latitude = 3;
  double longitude = 4;
  google.protobuf.Timestamp recorded_at = 5;
}

message ShipmentRouteSummary {
  string upload_id = 1;
  int64 shipment_id = 2;
  int32 received_count = 3;   // отримано
  int32 saved_count = 4;      // збережено в locations
  int32 rejected_count = 5;   // відхилено як некоректні
  int64 first_location_id = 6;
  int64 last_location_id = 7;
  bool duplicate = 8;         // повторна спроба вже збереженого вивантаження
}
```

## 2. Unary: статус і місцезнаходження

**Сервер** (`ShipmentTrackingGrpcService.getShipmentDeliveryInfo`):
1. `shipmentId <= 0` → `INVALID_ARGUMENT`.
2. Статус береться з `shipment-service` через наявний `TrackingService.getShipmentById()` (resilience4j: bulkhead, circuit breaker, retry).
3. Остання координата — з найбільшим `recorded_at` (за рівності — з більшим `location_id`). Сортування за часом запису, а не за ID, бо Client Streaming може пізніше вивантажити старіші точки.
4. Відповідь: `shipmentId`, `status`, `latitude`, `longitude`, `recordedAt`. Якщо координат ще немає, повертається лише статус (`has_location = false`).

| Ситуація | gRPC-статус |
|---|---|
| `shipment-service` відповів 404 | `NOT_FOUND` |
| `shipment-service` недоступний (спрацював fallback, відкритий circuit breaker, bulkhead, помилка мережі або 5xx) | `UNAVAILABLE` — **повторюється клієнтом** |
| БД `tracking-service` тимчасово недоступна | `UNAVAILABLE` |
| Будь-яка інша неочікувана помилка | `INTERNAL` — не повторюється |

Fallback `TrackingService` повертає «замінник» зі статусом `UNAVAILABLE`; сервер не віддає його клієнту як справжній статус, а перетворює на gRPC `UNAVAILABLE`.

**Клієнт** (`notification-service`): `ShipmentDeliveryInfoGrpcClient` (blocking stub) і HTTP-ендпоїнт для перевірки:

```http
GET /notifications/tracking/shipments/{shipmentId}/delivery-info
200 {"shipmentId":6,"status":"IN_TRANSIT","latitude":50.452,"longitude":30.525,"recordedAt":"2026-11-15T10:00:20Z"}
```

## 3. Client Streaming: історія GPS-координат

**Сервер** (`RouteUploadObserver`, `ShipmentRouteService`):
1. Кожну координату в `onNext()` перевіряють окремо. **Некоректні відхиляються й рахуються, потік не переривається.** Відхиляються: широта поза [-90; 90], довгота поза [-180; 180], NaN, відсутній `recorded_at`, час у майбутньому (понад 5 хв).
2. Коректні координати накопичуються в пам'яті й зберігаються в `locations` **однією транзакцією** після `onCompleted()`. Якщо клієнт обірвав потік, нічого не зберігається.
3. Збереження виконується через `LocationService.createLocation()` (вимога частини №1), тож після коміту кожна точка потрапляє активним Server Streaming-підписникам.
4. Підсумок: `received_count`, `saved_count`, `rejected_count` (`received = saved + rejected`).
5. **Ідемпотентність.** Разом із точками записується `route_uploads(upload_id, …)`. Якщо такий `upload_id` уже є, сервер повертає збережений підсумок із `duplicate = true` і не створює нових рядків. Повторне використання `upload_id` для іншої посилки → `ALREADY_EXISTS`.
6. Весь потік відхиляється з `INVALID_ARGUMENT` лише при порушенні протоколу: порожній чи різний `upload_id`, `shipment_id <= 0` чи різний у межах потоку, порожній потік, понад 1000 точок.
7. Тимчасова недоступність БД → `UNAVAILABLE`, інші неочікувані помилки → `INTERNAL`.

`upload_id` у сценарії не згадано, але без нього повтор Client Streaming створював би дублікати: gRPC під час retry надсилає весь потік заново, а відповідь могла загубитися вже після коміту.

**Клієнт** (`dispatch-service`, імітує застосунок кур'єра): `ShipmentRouteGrpcClient` (async stub) і HTTP-ендпоїнт:

```http
POST /dispatch/shipments/{shipmentId}/route
Idempotency-Key: <upload_id>      (необов'язковий; якщо його немає, генерується UUID)
{"points":[{"latitude":50.45,"longitude":30.52,"recordedAt":"2026-11-15T10:00:00Z"}, ...]}

200 {"uploadId":"demo-route-1","shipmentId":6,"receivedCount":4,"savedCount":2,"rejectedCount":2,
     "firstLocationId":7,"lastLocationId":8,"duplicate":false}
```

Клієнт перевіряє лише форму запиту (непорожній список, не більше 1000 точок). Координати він передає як є — перевіряє їх сервер і рахує відхилені.

## 4. Retries

Для обох клієнтів застосовано **стандартний механізм gRPC: `retryPolicy` у service config каналу**, що задається через `spring.grpc.client.channel.tracking.service-config.*` (Spring Boot 4.1). Retries виконує канал нижче рівня stub; прикладний код бачить лише остаточний результат. Для довготривалого Server Streaming (частина №1) використано прикладне перепідключення — на нього ця політика не впливає.

Для Client Streaming gRPC **буферизує всі надіслані повідомлення** і в новій спробі відправляє весь потік заново. Безпечним це робить комбінація: збереження лише після `onCompleted()` та `upload_id`.

### Конфігурація

`notification-service/src/main/resources/application.properties` (Unary) і `dispatch-service/src/main/resources/application.properties` (Client Streaming) містять однакову політику:

```properties
spring.grpc.client.channel.tracking.service-config.method[0].name[0].service=delivery.tracking.v1.ShipmentTrackingService
spring.grpc.client.channel.tracking.service-config.method[0].name[0].method=GetShipmentDeliveryInfo   # або RecordShipmentRoute
spring.grpc.client.channel.tracking.service-config.method[0].timeout=30s
spring.grpc.client.channel.tracking.service-config.method[0].retry.max-attempts=5
spring.grpc.client.channel.tracking.service-config.method[0].retry.initial-backoff=500ms
spring.grpc.client.channel.tracking.service-config.method[0].retry.max-backoff=4s
spring.grpc.client.channel.tracking.service-config.method[0].retry.backoff-multiplier=2.0
spring.grpc.client.channel.tracking.service-config.method[0].retry.retryable-status-codes=UNAVAILABLE
spring.grpc.client.channel.tracking.service-config.retrythrottling.max-tokens=100
spring.grpc.client.channel.tracking.service-config.retrythrottling.token-ratio=0.1
```

| Параметр | Значення | Пояснення |
|---|---|---|
| `max-attempts` | 5 | 1 початкова спроба + до 4 повторів (максимум gRPC) |
| `initial-backoff` / `multiplier` / `max-backoff` | 500 мс / 2.0 / 4 с | межа затримки перед спробою N: `min(0.5 с · 2^(N-2), 4 с)`; фактична затримка — випадкова в [0; межа] (jitter gRPC) |
| `retryable-status-codes` | `UNAVAILABLE` | `INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS`, `INTERNAL` не повторюються |
| `timeout` | 30 с | загальний deadline виклику **разом з усіма retries** |
| `retrythrottling` | 100 / 0.1 | кожна невдала спроба забирає 1 токен, успішна додає 0.1; повтори дозволені, поки токенів > 50 |

**Чому `timeout` 30 с.** Для Unary одна спроба може тривати секунди: `tracking-service` сам повторює запит до `shipment-service` (3 спроби з таймаутами). За першого запуску з 10 с друга спроба впиралася в дедлайн і клієнт отримував `DEADLINE_EXCEEDED` (504) замість `UNAVAILABLE`. З 30 с усі 5 спроб вміщуються. `DEADLINE_EXCEEDED` навмисно не повторюється: для Client Streaming він може означати, що сервер ще обробляє збереження.

Параметри можна перевизначити змінними середовища `TRACKING_GRPC_RETRY_MAX_ATTEMPTS`, `TRACKING_GRPC_RETRY_INITIAL_BACKOFF`, `TRACKING_GRPC_RETRY_MAX_BACKOFF`, `TRACKING_GRPC_UNARY_TIMEOUT`, `TRACKING_GRPC_STREAM_TIMEOUT`.

### Обробка UNAVAILABLE

Після вичерпання retries клієнт логує `ERROR … tracking-service unavailable after all retries` і кидає `TrackingGrpcException`. HTTP-шар відповідає:

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 5
{"type":"urn:delivery:problem:tracking-unavailable","title":"Tracking gRPC UNAVAILABLE","status":503,
 "detail":"Tracking-service тимчасово недоступний. Спробуйте пізніше.","grpcStatus":"UNAVAILABLE"}
```

Відповідність інших статусів: `DEADLINE_EXCEEDED` → 504, `NOT_FOUND` → 404, `INVALID_ARGUMENT` → 400, `ALREADY_EXISTS` → 409, `FAILED_PRECONDITION` → 422, решта → 502. Client Streaming після 503 безпечно повторити з тим самим `Idempotency-Key`.

### Логування спроб

Звичайний `ClientInterceptor` викликається один раз на логічний виклик, а retries відбуваються глибше в каналі. Тому `GrpcAttemptLoggingInterceptor` (`@GlobalClientInterceptor`, у `notification-service` і `dispatch-service`) додає до `CallOptions` `ClientStreamTracer.Factory`: gRPC створює новий tracer на **кожну** спробу й передає кількість попередніх спроб.

```
gRPC call attempt: method=…, attempt=1
gRPC attempt finished: method=…, attempt=1, status=UNAVAILABLE, description=…
gRPC retry attempt: method=…, attempt=2              (WARN)
gRPC attempt finished: method=…, attempt=2, status=OK
gRPC call completed: method=…, status=OK, elapsedMs=…
```

## 5. Тестування

### Автоматичні тести

| Модуль | Клас | Тестів | Що перевіряє |
|---|---|---|---|
| `tracking-service` | `ShipmentTrackingUnaryAndClientStreamingIntegrationTest` | 14 | реальний gRPC + PostgreSQL (Testcontainers), `TrackingService` замінено mock-ом |
| `tracking-service` | `ShipmentTrackingGrpcServiceTest` | +3 | `UNAVAILABLE` при недоступній БД і заповненому bulkhead, `INTERNAL` для інших помилок |
| `notification-service` | `ShipmentDeliveryInfoGrpcClientRetryIntegrationTest` | 7 | Unary-клієнт проти справжнього gRPC-сервера на TCP-порту, який вміє повертати збої та зупинятися |
| `dispatch-service` | `ShipmentRouteGrpcClientRetryIntegrationTest` | 7 | Client Streaming-клієнт проти такого самого сервера |

Сервер tracking-service перевіряє: статус + остання координата (за `recorded_at`), статус без координат, `NOT_FOUND`, fallback → `UNAVAILABLE`, `INVALID_ARGUMENT`; збереження коректних і відхилення некоректних точок з правильними лічильниками, потік лише з некоректних точок, ідемпотентність повтору з тим самим `upload_id` (`duplicate = true`, рядків не додається), `ALREADY_EXISTS`, порушення протоколу, порожній потік, обрив потоку клієнтом (нічого не зберігається), доставку точок Server Streaming-підписнику.

Клієнтські тести перевіряють: успішний виклик, 2× `UNAVAILABLE` → успіх з третьої спроби (у лозі `retry attempt … attempt=3`), постійний `UNAVAILABLE` → рівно 4 спроби (у тесті `max-attempts = 4`) → HTTP 503 + `Retry-After`, відсутність retry для `NOT_FOUND` / `INVALID_ARGUMENT`, повтор усього потоку з тим самим `upload_id` після збою сервера вже після прийому всіх точок, відновлення після зупинки та запуску сервера.

Запуск (потрібен Docker для Testcontainers):

```bash
cd tracking-api && mvn clean install -DskipTests
cd tracking-service && mvn test
cd notification-service && mvn test
cd dispatch-service && mvn test
```

Результат: `tracking-service` — 41/41, `notification-service` — 19/19, `dispatch-service` — 16/16, `web-ui` — 78/78.

### Ручна перевірка в Docker Compose

Піднято `tracking-service`, `notification-service`, `dispatch-service`, `shipment-service` (у `.env` має бути `KAFKA_VERSION`, див. `.env.example`). Посилку створено через `POST /shipments` зі статусом `IN_TRANSIT`.

| Крок | Запит | Результат |
|---|---|---|
| 1 | `GET /notifications/tracking/shipments/6/delivery-info`, координат ще немає | `200 {"status":"IN_TRANSIT","latitude":null,…}` |
| 2 | `POST /dispatch/shipments/6/route`, `Idempotency-Key: demo-route-1`, 4 точки (1 із широтою 95, 1 без часу) | `200 {"receivedCount":4,"savedCount":2,"rejectedCount":2,"firstLocationId":7,"lastLocationId":8,"duplicate":false}` |
| 3 | той самий запит повторно | `200 {…,"savedCount":2,…,"duplicate":true}`, нових рядків немає |
| 4 | Unary після вивантаження | `200 {"status":"IN_TRANSIT","latitude":50.452,"longitude":30.525,"recordedAt":"2026-11-15T10:00:20Z"}` |
| 5 | Unary для неіснуючої посилки | `404`, `grpcStatus: NOT_FOUND`, без retries |
| 6 | зупинено `shipment-service`, Unary | 5 спроб `UNAVAILABLE`, `503` за ~13 с |
| 7 | `shipment-service` запущено | Unary знову `200` |
| 8 | зупинено `tracking-service`, Unary і Client Streaming | обидва: 5 спроб, `503` + `Retry-After: 5` за ~7 с |
| 9 | `tracking-service` запущено | Unary `200`; повтор вивантаження з тим самим ключем — `200` |

Лог `notification-service` (Unary, `tracking-service` зупинено):

```text
10:07:57.007 INFO  gRPC call attempt: method=GetShipmentDeliveryInfo, attempt=1
10:07:57.008 WARN  gRPC attempt finished: method=GetShipmentDeliveryInfo, attempt=1, status=UNAVAILABLE, description=io exception
10:07:57.008 WARN  gRPC retry attempt: method=GetShipmentDeliveryInfo, attempt=2
10:07:57.434 WARN  gRPC attempt finished: method=GetShipmentDeliveryInfo, attempt=2, status=UNAVAILABLE, description=io exception
10:07:57.435 WARN  gRPC retry attempt: method=GetShipmentDeliveryInfo, attempt=3
10:07:58.532 WARN  gRPC attempt finished: method=GetShipmentDeliveryInfo, attempt=3, status=UNAVAILABLE, description=io exception
10:07:58.532 WARN  gRPC retry attempt: method=GetShipmentDeliveryInfo, attempt=4
10:08:00.776 WARN  gRPC attempt finished: method=GetShipmentDeliveryInfo, attempt=4, status=UNAVAILABLE, description=io exception
10:08:00.778 WARN  gRPC retry attempt: method=GetShipmentDeliveryInfo, attempt=5
10:08:04.222 WARN  gRPC attempt finished: method=GetShipmentDeliveryInfo, attempt=5, status=UNAVAILABLE, description=io exception
10:08:04.236 INFO  gRPC call completed: method=GetShipmentDeliveryInfo, status=UNAVAILABLE, elapsedMs=7229
10:08:04.237 ERROR gRPC GetShipmentDeliveryInfo failed: tracking-service unavailable after all retries: shipmentId=6, description=io exception
```

Лог `dispatch-service` (Client Streaming). Сервер запущено **під час** повторів, тож виклик завершився успішно на 5-й спробі всередині одного HTTP-запиту:

```text
10:08:17.027 INFO  gRPC call attempt: method=RecordShipmentRoute, attempt=1
10:08:17.027 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=1, status=UNAVAILABLE, description=Unable to resolve host tracking-service
10:08:17.027 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=2
10:08:17.565 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=2, status=UNAVAILABLE, description=Unable to resolve host tracking-service
10:08:17.566 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=3
10:08:18.604 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=3, status=UNAVAILABLE, description=Unable to resolve host tracking-service
10:08:18.605 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=4
10:08:20.566 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=4, status=UNAVAILABLE, description=Unable to resolve host tracking-service
10:08:20.568 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=5
10:08:24.237 INFO  gRPC attempt finished: method=RecordShipmentRoute, attempt=5, status=OK
10:08:24.238 INFO  gRPC route upload finished: uploadId=demo-route-9, shipmentId=6, received=2, saved=1, rejected=1, duplicate=false
10:08:24.238 INFO  gRPC call completed: method=RecordShipmentRoute, status=OK, elapsedMs=7211
```

Інтервали між спробами (≈0.5 → 1 → 2 → 3.7 с) відповідають експоненційному backoff із jitter. Загальний бюджет retries становить близько 7 с, тому коли відновлення відбувається пізніше, клієнт отримує 503 і повторює запит (для маршруту — з тим самим `Idempotency-Key`).

**Скріншоти для спільного звіту:** (1) результати `mvn test` трьох сервісів; (2) відповідь 503 з `Retry-After` (крок 8); (3) лог із 5 спробами; (4) відповідь `duplicate: true` (крок 3); (5) підсумок із `receivedCount` / `savedCount` / `rejectedCount` (крок 2).

## 6. Інфраструктура

- `dispatch-service/pom.xml`: `tracking-api`, `spring-boot-starter-grpc-client`. Канал `tracking` налаштовано з `TRACKING_GRPC_TARGET`.
- `dispatch-service/Dockerfile` і `docker-compose.yml`: збірка з кореня репозиторію (як у notification/tracking), щоб спершу встановити `tracking-api`.
- `notification-service`: додано blocking stub до `@ImportGrpcClients`.
- Kubernetes: `TRACKING_GRPC_TARGET` у `k8s/dispatch/configmap.yml` та `app.yml`; у `k8s/network-policy/07-allow-grpc.yml` дозволено вхідний трафік на порт 9090 `tracking-service` також від `dispatch-service`.
- Таблиця `route_uploads` створюється Hibernate (`ddl-auto=update`). Якщо в локальній БД лишилася таблиця зі старою схемою з іншої версії цієї гілки, її треба видалити: `DROP TABLE route_uploads;`.

## 7. Для частини №3 (Bidirectional Streaming)

- Новий RPC додавайте в той самий `ShipmentTrackingService` і в `ShipmentTrackingGrpcService`.
- `GrpcAttemptLoggingInterceptor` уже логує кожну спробу будь-якого RPC каналу `tracking` в обох клієнтських сервісах.
- Нативний retry gRPC для bidi-потоку працює лише до отримання першої відповіді сервера (після неї спроба «комітиться»). Для довготривалого bidi потрібне прикладне перепідключення, як у частині №1, та ідемпотентність, як `upload_id` тут.
- Правила перевірки координат (діапазони, NaN, час у майбутньому) реалізовано в `RouteUploadObserver`; за потреби їх можна винести й використати для статусу «відхилено як некоректну».
