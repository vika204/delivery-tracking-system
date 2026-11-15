# gRPC Bidirectional Streaming, логування та спільне тестування

**Частина №3.**

| Модель | RPC | Клієнт → сервер | Сценарій |
|---|---|---|---|
| Bidirectional Streaming | `StreamCourierLocations` | `dispatch-service` → `tracking-service` | під час активного руху кур'єр передає GPS-координати в реальному часі, а сервер одразу повертає рішення по кожній точці без закриття потоку |

Частини №1 (Server Streaming, `notification-service`) і №2 (Unary та Client Streaming) збережено без змін. Новий RPC додано в той самий protobuf-сервіс `ShipmentTrackingService` і в той самий `@GrpcService` (`ShipmentTrackingGrpcService`), як домовлено в `docs/grpc-server-streaming.md` та `docs/grpc-unary-client-streaming.md`. Номери полів наявних повідомлень збережено.

## 1. Контракт

`tracking-api/src/main/proto/delivery/tracking/v1/tracking.proto`:

```protobuf
rpc StreamCourierLocations(stream CourierLocationReport) returns (stream CourierLocationDecision); // Bidirectional streaming

enum CourierLocationStatus {
  COURIER_LOCATION_STATUS_UNSPECIFIED = 0;
  COURIER_LOCATION_STATUS_ACCEPTED = 1;         // координату прийнято й збережено в locations
  COURIER_LOCATION_STATUS_REJECTED = 2;         // координату відхилено як некоректну
  COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP = 3;  // виявлено підозрілий стрибок місцезнаходження
}

message CourierLocationReport {
  string point_id = 1;                          // ключ ідемпотентності окремої точки
  int64 shipment_id = 2;
  double latitude = 3;
  double longitude = 4;
  google.protobuf.Timestamp recorded_at = 5;
}

message CourierLocationDecision {
  string point_id = 1;
  int64 shipment_id = 2;
  CourierLocationStatus status = 3;
  bool saved = 4;
  int64 location_id = 5;                        // ID у locations, якщо saved = true (інакше 0)
  string reason = 6;                            // пояснення рішення або причини відхилення/стрибка
  double distance_meters = 7;                   // відстань від попередньої збереженої точки посилки
  double speed_kmh = 8;                         // розрахункова швидкість руху між точками
  bool duplicate = 9;                           // повторна спроба вже збереженої точки
}
```

## 2. Bidirectional Streaming: обмін координатами та відповідями в реальному часі

**Сервер** (`CourierLocationStreamObserver`, `CourierLiveLocationService`):
1. Кожне вхідне повідомлення `CourierLocationReport` у `onNext()` обробляється одразу в реальному часі, і сервер негайно надсилає у відповідь `CourierLocationDecision` через `responseObserver.onNext(...)`, **не закриваючи двосторонній потік**.
2. Спершу перевіряється коректність самої точки: `shipment_id > 0`, `point_id` до 64 символів, широта в межах `[-90; 90]`, довгота в межах `[-180; 180]`, відсутність `NaN`, наявність `recorded_at` і час не в майбутньому (допуск 5 хв). Некоректна точка не перериває потік — сервер повертає `COURIER_LOCATION_STATUS_REJECTED` (`saved = false`) із текстовою причиною в `reason`, а кур'єр продовжує надсилати наступні координати.
3. Якщо для цієї посилки вже є збережені координати, сервер бере останню за `recorded_at` (`LocationService.findLatestLocation()`) та обчислює відстань за формулою Гаверсинуса (`distance_meters`) і швидкість (`speed_kmh`). Якщо стрибок перевищує `tracking.live.max-jump-meters` (`5000 м`) або розрахункова швидкість перевищує `tracking.live.max-speed-kmh` (`150 км/год` при зміщенні понад `200 м`), точка не записується в `locations`, а сервер відповідає `COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP` (`saved = false`).
4. Коректна координата зберігається через `LocationService.createLocation()` (вимога частини №1), тому після коміту транзакції `LocationSavedEvent` миттєво розсилається всім активним підписникам `SubscribeToShipmentLocations` (Server Streaming), а сама точка стає видимою в `GetShipmentDeliveryInfo` (Unary).
5. **Ідемпотентність.** Разом із точкою в тій самій транзакції записується квитанція `live_location_receipts(point_id, shipment_id, location_id, distance_meters, speed_kmh, created_at)`. Якщо точка з таким `point_id` надходить повторно під час retry для тієї самої посилки, сервер повертає збережене рішення з `duplicate = true` без повторного запису в `locations`. Якщо `point_id` використано для іншої посилки, точка відхиляється зі статусом `COURIER_LOCATION_STATUS_REJECTED` без розриву потоку.
6. Потік завершується з помилкою лише у разі тимчасової недоступності БД (`UNAVAILABLE`) або неочікуваної помилки сервера (`INTERNAL`).

| Ситуація | Відповідь / gRPC-статус |
|---|---|
| Коректна нова точка | `onNext`: `COURIER_LOCATION_STATUS_ACCEPTED`, `saved = true`, `location_id > 0`, `duplicate = false` |
| Повтор вже збереженої точки з тим самим `point_id` | `onNext`: `COURIER_LOCATION_STATUS_ACCEPTED`, `saved = true`, той самий `location_id`, `duplicate = true` |
| Некоректні координати / відсутній або майбутній `recorded_at` / чужий `point_id` | `onNext`: `COURIER_LOCATION_STATUS_REJECTED`, `saved = false`, потік залишається відкритим |
| Стрибок `> 5000 м` або швидкість `> 150 км/год` (при зміщенні `> 200 м`) | `onNext`: `COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP`, `saved = false`, потік залишається відкритим |
| БД `tracking-service` тимчасово недоступна | `onError`: gRPC `UNAVAILABLE` — **повторюється клієнтом** |
| Неочікувана помилка сервера | `onError`: gRPC `INTERNAL` — не повторюється |

**Клієнт** (`dispatch-service`, імітує застосунок кур'єра під час руху): `CourierLiveTrackingGrpcClient` (async stub) і HTTP-ендпоінт:

```http
POST /dispatch/shipments/{shipmentId}/live-locations
Idempotency-Key: <session_id>     (необов'язковий; якщо його немає, генерується UUID)
{"points":[
  {"pointId":"pt-1","latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-11-15T10:00:00Z"},
  {"pointId":"pt-2","latitude":95.0,"longitude":30.5240,"recordedAt":"2026-11-15T10:00:10Z"},
  {"pointId":"pt-3","latitude":49.8397,"longitude":24.0297,"recordedAt":"2026-11-15T10:00:20Z"},
  {"pointId":"pt-4","latitude":50.4515,"longitude":30.5250,"recordedAt":"2026-11-15T10:00:30Z"}
]}

200 {"sessionId":"demo-live-1","shipmentId":6,"sentCount":4,"acceptedCount":2,"rejectedCount":1,"suspiciousJumpCount":1,
     "decisions":[
       {"pointId":"pt-1","shipmentId":6,"status":"ACCEPTED","saved":true,"locationId":9,"reason":"Coordinate accepted","distanceMeters":0.0,"speedKmh":0.0,"duplicate":false},
       {"pointId":"pt-2","shipmentId":6,"status":"REJECTED","saved":false,"locationId":null,"reason":"latitude must be between -90 and 90","distanceMeters":0.0,"speedKmh":0.0,"duplicate":false},
       {"pointId":"pt-3","shipmentId":6,"status":"SUSPICIOUS_JUMP","saved":false,"locationId":null,"reason":"suspicious location jump: 470001.2 m exceeds max 5000.0 m (speed 84600.2 km/h)","distanceMeters":470001.2,"speedKmh":84600.2,"duplicate":false},
       {"pointId":"pt-4","shipmentId":6,"status":"ACCEPTED","saved":true,"locationId":10,"reason":"Coordinate accepted","distanceMeters":192.4,"speedKmh":23.1,"duplicate":false}
     ]}
```

Якщо для точки в JSON не передано `pointId`, клієнт формує детермінований ключ `<sessionId>-<index>`, щоб повторні спроби залишалися ідемпотентними.

## 3. Retries, перепідключення та обробка UNAVAILABLE

Нативний механізм повторів gRPC (`retryPolicy` у service config) повторює двосторонній потік лише до моменту отримання першої відповіді від сервера (після першого `onNext` від сервера спроба вважається *committed*, як зазначено в розд. 7 `docs/grpc-unary-client-streaming.md`). Тому для `StreamCourierLocations` поєднано **два рівні повторів**:

1. **На старті потоку (до першої відповіді сервера)** — стандартний retry каналу gRPC за `service-config` (`method[1]` у `dispatch-service/src/main/resources/application.properties`): буферизує надіслані повідомлення та виконує до 5 спроб з експоненційним backoff.
2. **Посеред відкритого потоку (`mid-stream UNAVAILABLE`)** — прикладне перепідключення у `CourierLiveTrackingGrpcClient`: якщо сервер уже надіслав відповіді на перші $K$ точок і після цього з'єднання обірвалося з `UNAVAILABLE`, клієнт зберігає отримані рішення, очікує експоненційний backoff і відкриває новий потік, повторно надсилаючи **лише непідтверджені точки** з їхніми `point_id`. Навіть якщо точка $(K+1)$ встигла зберегтися на сервері перед обривом, завдяки `live_location_receipts` вона повернеться з `duplicate = true` без дублювання в `locations`.

### Конфігурація

`dispatch-service/src/main/resources/application.properties`:

```properties
spring.grpc.client.channel.tracking.service-config.method[1].name[0].service=delivery.tracking.v1.ShipmentTrackingService
spring.grpc.client.channel.tracking.service-config.method[1].name[0].method=StreamCourierLocations
spring.grpc.client.channel.tracking.service-config.method[1].timeout=${TRACKING_GRPC_STREAM_TIMEOUT:30s}
spring.grpc.client.channel.tracking.service-config.method[1].retry.max-attempts=${TRACKING_GRPC_RETRY_MAX_ATTEMPTS:5}
spring.grpc.client.channel.tracking.service-config.method[1].retry.initial-backoff=${TRACKING_GRPC_RETRY_INITIAL_BACKOFF:500ms}
spring.grpc.client.channel.tracking.service-config.method[1].retry.max-backoff=${TRACKING_GRPC_RETRY_MAX_BACKOFF:4s}
spring.grpc.client.channel.tracking.service-config.method[1].retry.backoff-multiplier=2.0
spring.grpc.client.channel.tracking.service-config.method[1].retry.retryable-status-codes=UNAVAILABLE
```

### Обробка UNAVAILABLE

Якщо після вичерпання всіх спроб `tracking-service` залишається недоступним, `CourierLiveTrackingGrpcClient` логує `ERROR gRPC StreamCourierLocations failed: tracking-service unavailable after all retries` і кидає `TrackingGrpcException`. `TrackingGrpcExceptionHandler` повертає:

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 5
{"type":"urn:delivery:problem:tracking-unavailable","title":"Tracking gRPC UNAVAILABLE","status":503,
 "detail":"Tracking-service тимчасово недоступний. Спробуйте пізніше.","grpcStatus":"UNAVAILABLE"}
```

## 4. Логування gRPC-викликів

- **На клієнті (`dispatch-service`, `notification-service`):** `GrpcAttemptLoggingInterceptor` (`@GlobalClientInterceptor` із `ClientStreamTracer.Factory`) логує кожну фізичну спробу (`attempt=1..N`), її завершення зі статусом і загальний час виклику; `CourierLiveTrackingGrpcClient` додатково логує кожне отримане рішення `CourierLocationDecision` та перепідключення посеред потоку.
- **На сервері (`tracking-service`):** додано `GrpcServerLoggingInterceptor` (`@GlobalServerInterceptor`), який логує старт кожного вхідного RPC (`SubscribeToShipmentLocations`, `GetShipmentDeliveryInfo`, `RecordShipmentRoute`, `StreamCourierLocations`), фінальний статус, опис помилки (якщо є) та `elapsedMs`:

```text
INFO  c.d.t.grpc.GrpcServerLoggingInterceptor  : gRPC server call started: method=StreamCourierLocations
INFO  c.d.t.grpc.CourierLocationStreamObserver : Live courier location stream opened: firstShipmentId=15
WARN  c.d.t.grpc.CourierLocationStreamObserver : Live courier location rejected: pointId=pt-2, shipmentId=15, index=1, reason=latitude must be between -90 and 90
WARN  c.d.t.live.CourierLiveLocationService    : Suspicious courier location jump detected: pointId=pt-3, shipmentId=15, distanceMeters=470001.2, speedKmh=9999.9, reason=...
INFO  c.d.t.grpc.CourierLocationStreamObserver : Live courier location stream completed: received=4, accepted=2, rejected=1, suspicious=1
INFO  c.d.t.grpc.GrpcServerLoggingInterceptor  : gRPC server call completed: method=StreamCourierLocations, status=OK, elapsedMs=15
```

## 5. Тестування

### Автоматичні тести

| Модуль | Клас | Тестів | Що перевіряє |
|---|---|---|---|
| `tracking-service` | `ShipmentTrackingBidirectionalStreamingIntegrationTest` | 4 | Bidirectional Streaming (Testcontainers PostgreSQL): `ACCEPTED`, `REJECTED`, `SUSPICIOUS_JUMP` без розриву потоку, ідемпотентність `point_id`, серверне логування |
| `tracking-service` | `ShipmentTrackingAllModelsIntegrationTest` | 1 | **спільний інтеграційний тест усіх 4 моделей gRPC** (`Server Streaming + Client Streaming + Bidirectional Streaming + Unary`) на одній посилці |
| `tracking-service` | `ShipmentTrackingGrpcServiceTest` | +1 | повернення `UNAVAILABLE` у `StreamCourierLocations`, коли БД недоступна |
| `dispatch-service` | `CourierLiveTrackingGrpcClientRetryIntegrationTest` | 5 | Bidirectional Streaming-клієнт проти `FakeTrackingServer`: 3 типи рішень, retry через service-config, mid-stream reconnect (досилання лише решти точок), 503 + `Retry-After` після вичерпання спроб, відновлення після рестарту сервера |

Спільний тест `ShipmentTrackingAllModelsIntegrationTest` перевіряє наскрізний сценарій у межах єдиного `ShipmentTrackingService`:
1. Клієнт відкриває підписку **Server Streaming** (`SubscribeToShipmentLocations`) на посилку `42`.
2. Кур'єр вивантажує 2 точки офлайн-історії через **Client Streaming** (`RecordShipmentRoute`).
3. Кур'єр передає в реальному часі ще 2 точки через **Bidirectional Streaming** (`StreamCourierLocations`: одна валідна точка приймається як `ACCEPTED`, друга з нереалістичним стрибком відхиляється як `SUSPICIOUS_JUMP`).
4. Підписник **Server Streaming** отримує всі 3 збережені точки (2 з Client Streaming + 1 з Bidirectional Streaming).
5. Виклик **Unary** (`GetShipmentDeliveryInfo`) повертає статус `IN_TRANSIT` та останню збережену координату з Bidirectional Streaming.

Запуск (потрібен Docker для Testcontainers):

```bash
cd tracking-api && mvn clean install -DskipTests
cd tracking-service && mvn test
cd notification-service && mvn test
cd dispatch-service && mvn test
```

Результат: `tracking-service` — 47/47, `notification-service` — 19/19, `dispatch-service` — 21/21.

### Ручна перевірка в Docker Compose

Піднято `tracking-service`, `notification-service`, `dispatch-service`, `shipment-service`. Посилка `6` у статусі `IN_TRANSIT`.

| Крок | Запит | Результат |
|---|---|---|
| 1 | `POST /notifications/tracking/subscriptions/6` | відкрито підписку Server Streaming у `notification-service` |
| 2 | `POST /dispatch/shipments/6/live-locations`, `Idempotency-Key: demo-live-1`, 4 точки (1 валідна, 1 із широтою 95, 1 зі стрибком у Львів, 1 валідна) | `200 {"sentCount":4,"acceptedCount":2,"rejectedCount":1,"suspiciousJumpCount":1,"decisions":[…]}` |
| 3 | той самий запит повторно (`Idempotency-Key: demo-live-1`) | `200`, для прийнятих точок `"duplicate": true`, нових рядків у `locations` не створено |
| 4 | `GET /notifications/tracking/shipments/6/delivery-info` (Unary) | `200`, повертає останню прийняту точку `pt-4` з кроку 2; у логах `notification-service` зафіксовано отримання точок `pt-1` і `pt-4` через Server Streaming |
| 5 | зупинено `tracking-service`, викликано `POST /dispatch/shipments/6/live-locations` | 5 спроб `UNAVAILABLE` у логах, відповідь `503 Service Unavailable` + `Retry-After: 5` за ~7 с |
| 6 | `tracking-service` запущено, повторно викликано `POST /dispatch/shipments/6/live-locations` | `200`, двосторонній потік успішно відновив роботу |

Лог `dispatch-service` при недоступному `tracking-service` та відновленні під час повторів:

```text
11:14:02.110 INFO  gRPC bidirectional location stream started: sessionId=demo-live-2, shipmentId=6, points=3
11:14:02.112 INFO  gRPC call attempt: method=StreamCourierLocations, attempt=1
11:14:02.114 WARN  gRPC attempt finished: method=StreamCourierLocations, attempt=1, status=UNAVAILABLE, description=io exception
11:14:02.114 WARN  gRPC retry attempt: method=StreamCourierLocations, attempt=2
11:14:02.580 WARN  gRPC attempt finished: method=StreamCourierLocations, attempt=2, status=UNAVAILABLE, description=io exception
11:14:02.581 WARN  gRPC retry attempt: method=StreamCourierLocations, attempt=3
11:14:03.612 INFO  gRPC live location decision received: sessionId=demo-live-2, pointId=pt-1, shipmentId=6, status=COURIER_LOCATION_STATUS_ACCEPTED, saved=true, locationId=11, duplicate=false
11:14:03.619 INFO  gRPC attempt finished: method=StreamCourierLocations, attempt=3, status=OK
11:14:03.620 INFO  gRPC call completed: method=StreamCourierLocations, status=OK, elapsedMs=1508
```

Лог `dispatch-service` при обриві з'єднання посеред потоку (`mid-stream UNAVAILABLE`):

```text
WARN  gRPC StreamCourierLocations interrupted mid-stream with UNAVAILABLE; reconnecting: sessionId=session-midstream, shipmentId=15, reconnectAttempt=1/3, remainingPoints=2, backoffMs=50
```

**Скріншоти для спільного звіту:** (1) результати `mvn test` із новими тестами `ShipmentTrackingBidirectionalStreamingIntegrationTest`, `ShipmentTrackingAllModelsIntegrationTest` та `CourierLiveTrackingGrpcClientRetryIntegrationTest`; (2) відповідь `200` від `POST /dispatch/shipments/{id}/live-locations` із трьома статусами (`ACCEPTED`, `REJECTED`, `SUSPICIOUS_JUMP`); (3) відповідь із `duplicate: true` при повторі; (4) лог `tracking-service` (`GrpcServerLoggingInterceptor`) та `dispatch-service` (`GrpcAttemptLoggingInterceptor`) під час retries і `503` з `Retry-After: 5`.
