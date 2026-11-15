# gRPC Unary та Client Streaming: реалізація, UNAVAILABLE і retries

**Частина №2.**
**Учасники взаємодії:** `dispatch-service` (кур'єрська сторона, gRPC-клієнт) → `tracking-service` (gRPC-сервер, PostgreSQL).

| Модель | RPC | Бізнес-сценарій |
|---|---|---|
| Unary | `GetLatestShipmentLocation` | dispatch запитує останню відому координату посилки (наприклад, перед призначенням кур'єра) |
| Client Streaming | `RecordShipmentRoute` | кур'єрський застосунок накопичує GPS-точки офлайн, а dispatch одним потоком вивантажує маршрут до tracking |

Частина №1 (Server Streaming, `notification-service`) лишилася без змін: нові RPC додано в той самий protobuf-сервіс `ShipmentTrackingService` і в той самий `@GrpcService` (`ShipmentTrackingGrpcService`), як домовлено в `docs/grpc-server-streaming.md`.

## 1. Контракт

`tracking-api/src/main/proto/delivery/tracking/v1/tracking.proto` (номери полів наявних повідомлень не змінено):

```protobuf
service ShipmentTrackingService {
  rpc SubscribeToShipmentLocations(SubscribeToShipmentLocationsRequest)
      returns (stream ShipmentLocationUpdate);              // частина №1

  rpc GetLatestShipmentLocation(GetLatestShipmentLocationRequest)
      returns (ShipmentLocationUpdate);                     // Unary

  rpc RecordShipmentRoute(stream ShipmentRoutePoint)
      returns (ShipmentRouteSummary);                       // Client streaming
}

message GetLatestShipmentLocationRequest { int64 shipment_id = 1; }

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
  int32 accepted_count = 3;
  int64 first_location_id = 4;
  int64 last_location_id = 5;
  bool duplicate = 6;       // true — це повторна спроба вже збереженого вивантаження
}
```

Unary повертає наявне повідомлення `ShipmentLocationUpdate`, тож клієнти Server Streaming і Unary працюють з однаковою моделлю координати.

## 2. Серверна частина (`tracking-service`)

| Клас | Відповідальність |
|---|---|
| `ShipmentTrackingGrpcService` | `getLatestShipmentLocation()` та `recordShipmentRoute()` поряд із методом частини №1 |
| `RouteUploadObserver` | Серверний `StreamObserver` для Client Streaming: валідація, накопичення точок, збереження після `onCompleted()` |
| `ShipmentRouteService` | Атомарне збереження маршруту і перевірка `upload_id` |
| `RouteUpload` / `RouteUploadRepository` | Таблиця `route_uploads` — журнал успішних вивантажень (PK = `upload_id`) |
| `GrpcStatusMapper` | Перетворення винятків на gRPC-статуси (`UNAVAILABLE` / `INTERNAL`) |
| `TrackingProtoMapper` | Додано перетворення `Location` → protobuf і `ShipmentRoutePoint` → доменна точка |

**Unary.** `shipmentId <= 0` → `INVALID_ARGUMENT`; координат немає → `NOT_FOUND`; інакше повертається точка з найбільшим `recorded_at` (за рівності — з більшим `location_id`). Сортування за часом запису, а не за ID, бо Client Streaming може пізніше вивантажити старіші точки.

**Client Streaming.**
1. Кожну точку в `onNext()` валідують: непорожній `upload_id` (≤ 64 символи), `shipment_id > 0`, однакові `upload_id`/`shipment_id` у всьому потоці, широта в межах [-90; 90], довгота в межах [-180; 180], наявний `recorded_at`, не більше 1000 точок. Перша помилка → `INVALID_ARGUMENT`, решта потоку ігнорується, **нічого не зберігається**.
2. Точки лише накопичуються в пам'яті. У БД вони потрапляють **однією транзакцією** після `onCompleted()` клієнта. Якщо клієнт обірвав потік (`onError`/`CANCELLED`), частковий маршрут не з'являється.
3. Збереження виконується через `LocationService.createLocation()` (вимога частини №1), тож після коміту кожна точка потрапляє активним Server Streaming-підписникам (перевірено тестом).
4. Разом із точками записується `route_uploads(upload_id, …)`. Якщо такий `upload_id` уже є, сервер повертає збережений підсумок із `duplicate = true` і не створює нових рядків. Повторне використання `upload_id` для іншої посилки → `ALREADY_EXISTS`.

**Обробка UNAVAILABLE на сервері.** Якщо сховище тимчасово недоступне (`CannotCreateTransactionException`, `DataAccessResourceFailureException`, `TransientDataAccessException`, `SQLTransientException`), сервер повертає `UNAVAILABLE` — і клієнт повторить виклик. Інші неочікувані помилки → `INTERNAL`, їх не повторюють.

## 3. Клієнтська частина (`dispatch-service`)

| Клас | Відповідальність |
|---|---|
| `DispatchServiceApplication` | `@ImportGrpcClients(target = "tracking", …)` — blocking stub для Unary, async stub для Client Streaming |
| `TrackingGrpcClient` | Виклики RPC, перетворення фінального `StatusRuntimeException` на `TrackingGrpcException` |
| `GrpcAttemptLoggingInterceptor` | `@GlobalClientInterceptor`: логує **кожну фізичну спробу** (зокрема retries) і фінальний статус |
| `ShipmentTrackingController` | HTTP-фасад для перевірки |
| `TrackingGrpcExceptionHandler` | gRPC-статус → HTTP (`UNAVAILABLE` → `503` + `Retry-After: 5`) |

HTTP API:

```http
GET  /dispatch/shipments/{shipmentId}/location          -> Unary
POST /dispatch/shipments/{shipmentId}/route              -> Client streaming
     Idempotency-Key: <upload_id>  (необов'язковий; якщо його немає, генерується UUID)
     {"points":[{"latitude":50.45,"longitude":30.52,"recordedAt":"2026-11-15T10:00:00Z"}, ...]}
```

Відповідність статусів: `UNAVAILABLE` → 503, `DEADLINE_EXCEEDED` → 504, `NOT_FOUND` → 404, `INVALID_ARGUMENT` → 400, `ALREADY_EXISTS` → 409, інші → 502. Тіло — `ProblemDetail` з полем `grpcStatus`.

## 4. Retries

### 4.1. Чому нативний retry gRPC, а не прикладний reconnect

Для Server Streaming (частина №1) використано прикладне перепідключення, бо потік довготривалий. Unary і Client Streaming — короткі виклики, тож для них застосовано **стандартний механізм gRPC: `retryPolicy` у service config каналу**. Spring Boot 4.1 задає його через властивості `spring.grpc.client.channel.<name>.service-config.*`. Retries виконує канал, нижче рівня stub; прикладний код бачить лише остаточний результат.

Для Client Streaming gRPC **буферизує всі надіслані повідомлення** і в новій спробі відправляє весь потік заново. Тому:
- сервер зберігає маршрут лише після `onCompleted()` — обрив посередині нічого не записує;
- навіть якщо сервер встиг закомітити, а відповідь загубилася, повтор із тим самим `upload_id` поверне `duplicate = true` без дублікатів у БД.

### 4.2. Конфігурація (`dispatch-service/src/main/resources/application.properties`)

```properties
spring.grpc.client.channel.tracking.target=${TRACKING_GRPC_TARGET:dns:///localhost:9094}

# Unary
spring.grpc.client.channel.tracking.service-config.method[0].name[0].service=delivery.tracking.v1.ShipmentTrackingService
spring.grpc.client.channel.tracking.service-config.method[0].name[0].method=GetLatestShipmentLocation
spring.grpc.client.channel.tracking.service-config.method[0].timeout=10s
spring.grpc.client.channel.tracking.service-config.method[0].retry.max-attempts=5
spring.grpc.client.channel.tracking.service-config.method[0].retry.initial-backoff=500ms
spring.grpc.client.channel.tracking.service-config.method[0].retry.max-backoff=4s
spring.grpc.client.channel.tracking.service-config.method[0].retry.backoff-multiplier=2.0
spring.grpc.client.channel.tracking.service-config.method[0].retry.retryable-status-codes=UNAVAILABLE

# Client streaming — аналогічно, method[1] = RecordShipmentRoute, timeout=30s

# Захист від шторму повторів
spring.grpc.client.channel.tracking.service-config.retrythrottling.max-tokens=100
spring.grpc.client.channel.tracking.service-config.retrythrottling.token-ratio=0.1
```

| Параметр | Значення | Пояснення |
|---|---|---|
| `max-attempts` | 5 | 1 початкова спроба + до 4 повторів (максимум, який дозволяє gRPC) |
| `initial-backoff` / `multiplier` / `max-backoff` | 500 мс / 2.0 / 4 с | межа затримки перед спробою N: `min(0.5 с · 2^(N-2), 4 с)` → 0.5, 1, 2, 4 с; фактична затримка — **випадкова в [0; межа]** (jitter gRPC) |
| `retryable-status-codes` | `UNAVAILABLE` | лише тимчасова недоступність. `INVALID_ARGUMENT`, `NOT_FOUND`, `ALREADY_EXISTS`, `INTERNAL` не повторюються |
| `timeout` | 10 с / 30 с | загальний deadline виклику **разом з усіма retries** |
| `retrythrottling` | 100 / 0.1 | кожна невдала спроба забирає 1 токен, успішна додає 0.1; повтори дозволені, поки токенів > 50 |

`DEADLINE_EXCEEDED` навмисно не повторюється: для Client Streaming він може означати, що сервер ще обробляє збереження. `upload_id` зробив би повтор безпечним, але подовжив би й без того вичерпаний deadline.

Параметри `max-attempts`, `initial-backoff`, `max-backoff` і таймаути можна перевизначити змінними середовища `TRACKING_GRPC_RETRY_*` та `TRACKING_GRPC_*_TIMEOUT`.

### 4.3. Обробка UNAVAILABLE після вичерпання retries

`TrackingGrpcClient` логує `ERROR … tracking-service unavailable after all retries` і кидає `TrackingGrpcException`, а `TrackingGrpcExceptionHandler` відповідає:

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 5
Content-Type: application/problem+json

{"type":"urn:delivery:problem:tracking-unavailable","title":"Tracking gRPC UNAVAILABLE",
 "status":503,"detail":"Tracking-service тимчасово недоступний. Спробуйте пізніше.","grpcStatus":"UNAVAILABLE"}
```

Для Client Streaming такий запит безпечно повторити з тим самим `Idempotency-Key`.

## 5. Логування спроб

Звичайний `ClientInterceptor` викликається один раз на логічний виклик, а retries відбуваються глибше в каналі. Тому `GrpcAttemptLoggingInterceptor` додає до `CallOptions` `ClientStreamTracer.Factory`. gRPC створює новий tracer на **кожну** спробу й передає кількість попередніх спроб (`StreamInfo.getPreviousAttempts()`). У лог потрапляють:

```
gRPC call attempt: method=…, attempt=1
gRPC attempt finished: method=…, attempt=1, status=UNAVAILABLE, description=…
gRPC retry attempt: method=…, attempt=2              (WARN)
gRPC attempt finished: method=…, attempt=2, status=OK
gRPC call completed: method=…, status=OK, elapsedMs=…
```

## 6. Тестування

### 6.1. Автоматичні тести

**`dispatch-service` — `TrackingGrpcClientRetryIntegrationTest`** (11 тестів): повний Spring-контекст із каналом, налаштованим властивостями з `application.properties` (у тесті змінено лише затримки: 4 спроби, backoff 50–200 мс), і **справжній gRPC-сервер на TCP-порту** (`FakeTrackingServer`), який вміє повертати збій у перших N викликах, а також зупинятися й запускатися на тому самому порту.

| Тест | Що перевіряє |
|---|---|
| `unaryReturnsLatestLocation` | успішний Unary через HTTP → gRPC |
| `unaryRetriesUnavailableAndSucceeds` | 2× `UNAVAILABLE`, потім OK → 3 виклики на сервері, у лозі є `retry attempt … attempt=3` |
| `unaryReturns503WhenRetriesExhausted` | постійний `UNAVAILABLE` → рівно 4 спроби, HTTP 503 + `Retry-After` |
| `unaryDoesNotRetryNonRetryableStatus` | `NOT_FOUND` → 1 спроба, HTTP 404 |
| `unaryRecoversAfterServerRestart` | сервер зупинено → 4 спроби з `io exception`, 503; сервер запущено → виклики знову успішні |
| `clientStreamingUploadsAllPoints` | усі точки потоку з однаковим `upload_id` доходять до сервера |
| `clientStreamingRetryReplaysWholeStreamWithSameUploadId` | сервер прийняв усі точки, але відповів `UNAVAILABLE` (найгірший випадок) → gRPC повторно надсилає **увесь** потік; 3 спроби, у кожній 3 точки з тим самим `upload_id` |
| `clientStreamingReturns503WhenRetriesExhausted` | 4 спроби → 503 |
| `clientStreamingDoesNotRetryInvalidArgument` | `INVALID_ARGUMENT` → 1 спроба, HTTP 400 |
| `clientStreamingRecoversAfterServerRestart` | сервер недоступний → 503; після запуску — успішне вивантаження |
| `rejectsInvalidRouteBeforeCallingGrpc` | некоректна точка відхиляється ще до gRPC |

**`tracking-service` — `ShipmentTrackingUnaryAndClientStreamingIntegrationTest`** (11 тестів): реальний gRPC-виклик і PostgreSQL у Testcontainers. Перевіряє вибір останньої координати за `recorded_at`, `NOT_FOUND`/`INVALID_ARGUMENT`, атомарне збереження маршруту, ідемпотентність повтору з тим самим `upload_id` (`duplicate = true`, кількість рядків не змінюється), `ALREADY_EXISTS`, відхилення некоректної точки, змішаних ID і порожнього потоку без часткового запису, відсутність запису при обриві потоку клієнтом, а також те, що точки з Client Streaming доходять до Server Streaming-підписника.

**`ShipmentTrackingGrpcServiceTest`** (модульні): недоступна БД → `UNAVAILABLE` для Unary і Client Streaming; інша помилка → `INTERNAL`.

Запуск:

```bash
cd tracking-api && mvn install -DskipTests
cd dispatch-service && mvn test
cd tracking-service && mvn test      # потрібен Docker для Testcontainers
```

Результат: `dispatch-service` — 20/20, `tracking-service` — 37/37; `notification-service` (12/12) і `web-ui` (78/78) після розширення контракту також зелені.

### 6.2. Ручна перевірка в Docker Compose

Запуск мінімального набору (у `.env` має бути `KAFKA_VERSION`, див. `.env.example`):

```bash
docker compose up -d --build tracking-service dispatch-service
```

Сценарій і фактичні відповіді (`dispatch-service` на `127.0.0.1:8083`):

| Крок | Запит | Результат |
|---|---|---|
| 1 | `POST /dispatch/shipments/42/route`, `Idempotency-Key: demo-route-1`, 3 точки | `200 {"acceptedCount":3,"firstLocationId":1,"lastLocationId":3,"duplicate":false}` |
| 2 | той самий запит повторно | `200 {…,"firstLocationId":1,"lastLocationId":3,"duplicate":true}` — нових рядків немає |
| 3 | `GET /dispatch/shipments/42/location` | `200 {"locationId":3,"latitude":50.452,"longitude":30.525,"recordedAt":"2026-11-15T10:00:20Z"}` |
| 4 | `GET /dispatch/shipments/999/location` | `404`, `grpcStatus: NOT_FOUND`, без retries |
| 5 | `docker compose stop tracking-service`, далі `GET …/42/location` | `503`, `Retry-After: 5`, `grpcStatus: UNAVAILABLE` через ~7 с (5 спроб) |
| 6 | `POST …/42/route`, `Idempotency-Key: demo-route-2` | `503` через ~7.5 с (5 спроб) |
| 7 | `docker compose start tracking-service`, `GET …/42/location` щосекунди | перший виклик ще 503, наступний — `200` |
| 8 | повтор кроку 6 з тим самим ключем `demo-route-2` | `200 {"acceptedCount":3,"firstLocationId":4,"lastLocationId":6,"duplicate":false}` |

Лог `dispatch-service` (JSON-логи перетворено на рядки; час UTC):

```text
# крок 1-2: Client streaming, потім повтор з тим самим upload_id
09:37:33.204 INFO  gRPC route upload started: uploadId=demo-route-1, shipmentId=42, points=3
09:37:33.207 INFO  gRPC call attempt: method=RecordShipmentRoute, attempt=1
09:37:33.425 INFO  gRPC attempt finished: method=RecordShipmentRoute, attempt=1, status=OK
09:37:33.427 INFO  gRPC route upload finished: uploadId=demo-route-1, shipmentId=42, accepted=3, duplicate=false
09:37:33.454 INFO  gRPC route upload finished: uploadId=demo-route-1, shipmentId=42, accepted=3, duplicate=true

# крок 4: NOT_FOUND не повторюється
09:37:33.631 INFO  gRPC call attempt: method=GetLatestShipmentLocation, attempt=1
09:37:33.636 WARN  gRPC attempt finished: method=GetLatestShipmentLocation, attempt=1, status=NOT_FOUND, description=no locations for shipment 999

# крок 5: tracking-service зупинено -> Unary, 5 спроб з експоненційним backoff
09:37:34.948 INFO  gRPC call attempt: method=GetLatestShipmentLocation, attempt=1
09:37:34.949 WARN  gRPC attempt finished: method=GetLatestShipmentLocation, attempt=1, status=UNAVAILABLE, description=io exception
09:37:34.949 WARN  gRPC retry attempt: method=GetLatestShipmentLocation, attempt=2
09:37:35.456 WARN  gRPC attempt finished: method=GetLatestShipmentLocation, attempt=2, status=UNAVAILABLE, description=io exception
09:37:35.456 WARN  gRPC retry attempt: method=GetLatestShipmentLocation, attempt=3
09:37:36.569 WARN  gRPC attempt finished: method=GetLatestShipmentLocation, attempt=3, status=UNAVAILABLE, description=io exception
09:37:36.571 WARN  gRPC retry attempt: method=GetLatestShipmentLocation, attempt=4
09:37:38.636 WARN  gRPC attempt finished: method=GetLatestShipmentLocation, attempt=4, status=UNAVAILABLE, description=io exception
09:37:38.637 WARN  gRPC retry attempt: method=GetLatestShipmentLocation, attempt=5
09:37:41.900 WARN  gRPC attempt finished: method=GetLatestShipmentLocation, attempt=5, status=UNAVAILABLE, description=io exception
09:37:41.915 INFO  gRPC call completed: method=GetLatestShipmentLocation, status=UNAVAILABLE, elapsedMs=6967
09:37:41.916 ERROR gRPC GetLatestShipmentLocation failed: tracking-service unavailable after all retries: shipmentId=42, description=io exception

# крок 6: Client streaming під час аварії
09:37:41.944 INFO  gRPC call attempt: method=RecordShipmentRoute, attempt=1
09:37:41.944 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=1, status=UNAVAILABLE, description=io exception
09:37:41.944 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=2
09:37:42.532 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=2, status=UNAVAILABLE, description=io exception
09:37:42.533 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=3
09:37:43.363 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=3, status=UNAVAILABLE, description=io exception
09:37:43.364 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=4
09:37:45.601 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=4, status=UNAVAILABLE, description=io exception
09:37:45.602 WARN  gRPC retry attempt: method=RecordShipmentRoute, attempt=5
09:37:49.452 WARN  gRPC attempt finished: method=RecordShipmentRoute, attempt=5, status=UNAVAILABLE, description=io exception
09:37:49.456 INFO  gRPC call completed: method=RecordShipmentRoute, status=UNAVAILABLE, elapsedMs=7512
09:37:49.458 ERROR gRPC RecordShipmentRoute failed: tracking-service unavailable after all retries: shipmentId=42, description=io exception

# кроки 7-8: сервер відновлено
09:37:57.719 INFO  gRPC call attempt: method=GetLatestShipmentLocation, attempt=1
09:37:58.001 INFO  gRPC attempt finished: method=GetLatestShipmentLocation, attempt=1, status=OK
09:37:58.028 INFO  gRPC call attempt: method=RecordShipmentRoute, attempt=1
09:37:58.089 INFO  gRPC attempt finished: method=RecordShipmentRoute, attempt=1, status=OK
09:37:58.089 INFO  gRPC route upload finished: uploadId=demo-route-2, shipmentId=42, accepted=3, duplicate=false
```

Інтервали між спробами (≈0.5 → 1.1 → 2.1 → 3.3 с) відповідають експоненційному backoff із межами 0.5 / 1 / 2 / 4 с і випадковим jitter.

**Спостереження: межі retries у Docker Compose.** Повний бюджет повторів одного виклику — близько 7 с (`max-attempts = 5` — це максимум gRPC). Коли контейнер `tracking-service` зупинено, Docker DNS перестає резолвити ім'я (`Unable to resolve host tracking-service`). Після запуску канал відновлюється лише після повторного DNS-резолву й свого connect-backoff, тому в Compose відновлення спрацювало **на наступному** виклику (крок 7), а не всередині поточного. Те, що retries перекривають короткий збій усередині одного виклику, підтверджують автотести:
- `unaryRetriesUnavailableAndSucceeds` / `clientStreamingRetryReplaysWholeStreamWithSameUploadId` — сервер повертає `UNAVAILABLE`, а виклик однаково завершується успішно;
- `clientStreamingRecoversAfterServerRestart` — після рестарту TCP-сервера в лозі `attempt=3, status=OK`.

Для довших аварій клієнт отримує 503 + `Retry-After` і безпечно повторює запит; для маршруту — з тим самим `Idempotency-Key`.

**Скріншоти для спільного звіту:** (1) результати `mvn test` для обох сервісів; (2) відповідь 503 з `Retry-After` (крок 5); (3) лог із 5 спробами (крок 5 або 6); (4) відповідь `duplicate: true` (крок 2).

## 7. Інфраструктура

- `dispatch-service/pom.xml`: `tracking-api`, `spring-boot-starter-grpc-client`.
- `dispatch-service/Dockerfile` і `docker-compose.yml`: збірка з кореня репозиторію (як у notification/tracking), щоб спершу встановити `tracking-api`; `TRACKING_GRPC_TARGET=dns:///tracking-service:9090`.
- Kubernetes: `TRACKING_GRPC_TARGET` у `k8s/dispatch/configmap.yml` та `app.yml`; у `k8s/network-policy/07-allow-grpc.yml` дозволено вхідний трафік на порт 9090 `tracking-service` також від `dispatch-service`.

## 8. Для частини №3 (Bidirectional Streaming)

- Новий RPC додавайте в той самий `ShipmentTrackingService` і в `ShipmentTrackingGrpcService`.
- `GrpcAttemptLoggingInterceptor` в `dispatch-service` вже логує кожну спробу будь-якого RPC цього каналу. Його можна взяти за основу спільного логування (або перенести в окремий модуль).
- Нативний retry gRPC для bidi-потоку працює лише до отримання першої відповіді сервера (після неї спроба «комітиться»). Для довготривалого bidi потрібне прикладне перепідключення, як у частині №1, та ідемпотентність, як `upload_id` тут.
