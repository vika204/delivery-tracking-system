# gRPC Server Streaming: реалізація та інтеграція

**частина №1.**  
**Сценарій:** `notification-service` підписується на нові GPS-координати посилки, а `tracking-service` передає їх через gRPC Server Streaming. Цей документ описує реалізацію та домовленості для наступних частин командного завдання; матеріали демонстрації й скріншоти будуть у спільному звіті.

## 1. Контракт і межі відповідальності

Спільний protobuf-контракт розташований у `tracking-api/src/main/proto/delivery/tracking/v1/tracking.proto`:

```protobuf
service ShipmentTrackingService {
  rpc SubscribeToShipmentLocations(SubscribeToShipmentLocationsRequest)
      returns (stream ShipmentLocationUpdate);
}
```

- Запит: `shipment_id` (`int64`).
- Повідомлення потоку: `location_id`, `shipment_id`, `latitude`, `longitude`, `recorded_at` (`google.protobuf.Timestamp`).
- Клієнт: `notification-service`; сервер: `tracking-service`.
- Кожен виклик RPC створює окремий логічний потік відповідей. Одночасно можуть існувати підписки на різні посилки.

Java-класи повідомлень, клієнтський `ShipmentTrackingServiceStub` і серверний `ShipmentTrackingServiceImplBase` генеруються з `.proto`; їх не потрібно писати чи редагувати вручну.

## 2. Передавання GPS-координат

1. `TrackingController` приймає `POST /tracking` і передає координату в `LocationService.createLocation()`.
2. `LocationService` зберігає `Location` через `TrackingRepository` в межах транзакції та публікує **локальну подію Spring** `LocationSavedEvent`.
3. `LocationStreamPublisher` обробляє подію через `@TransactionalEventListener(phase = AFTER_COMMIT)`. Отже, розсилка запускається після успішного коміту транзакції.
4. `TrackingProtoMapper` перетворює координату й час на `ShipmentLocationUpdate`, зокрема `LocalDateTime` на protobuf `Timestamp` з використанням `tracking.recorded-at-zone` (типово `UTC`).
5. `LocationStreamPublisher` знаходить активних підписників відповідного `shipmentId` та викликає серверний `observer.onNext(update)`.
6. gRPC доставляє повідомлення до `notification-service`, де клієнтський `StreamObserver.onNext()` наразі **записує координати в лог** та скидає затримку наступних retries до початкового значення.

`LocationSavedEvent` — **не Kafka-повідомлення й не запис Outbox**: це внутрішня подія в `tracking-service`. Самі GPS-координати зберігаються в PostgreSQL, але доставка події до активного gRPC-потоку не є гарантованою чергою повідомлень.

## 3. Основні класи

| Модуль | Клас | Відповідальність |
|---|---|---|
| `tracking-api` | `tracking.proto` | Єдиний контракт повідомлень і RPC |
| `tracking-service` | `ShipmentTrackingGrpcService` | Приймає підписку, перевіряє `shipmentId > 0`, передає observer до publisher; некоректний ID → `INVALID_ARGUMENT` |
| `tracking-service` | `LocationStreamPublisher` | Зберігає активні server observers за `shipmentId`, надсилає оновлення, прибирає скасовані підписки |
| `tracking-service` | `LocationService`, `LocationSavedEvent` | Збереження GPS та локальна подія після виклику сервісу |
| `tracking-service` | `TrackingProtoMapper` | Перетворення доменних даних на protobuf |
| `notification-service` | `ShipmentLocationGrpcClient` | Керування підписками, асинхронний stub, отримання координат, скасування та reconnect |
| `notification-service` | `TrackingSubscriptionController` | Допоміжні HTTP-ендпоїнти для керування підписками й перевірки |

На сервері використовується `ConcurrentHashMap` із множинами observers: на одну посилку можуть підписатися кілька клієнтів. На стороні **одного екземпляра** `notification-service` повторна підписка на той самий `shipmentId` не відкриває другий потік.

## 4. Обробка помилок і завершення потоку

Клієнт працює через асинхронний `ShipmentTrackingServiceStub`. Для довготривалого Server Streaming реалізовано **прикладне перепідключення**, а не автоматичне повторення вже активного потоку за допомогою gRPC Service Config.

- При `Status.Code.UNAVAILABLE` клієнт завершує стару спробу та планує новий виклик `subscribeToShipmentLocations()` через `ScheduledExecutorService`.
- Затримка збільшується за exponential backoff: **1 → 2 → 4 → 8 → 10 секунд**, далі не перевищує 10 секунд. Обмежено затримку, **не кількість спроб**.
- Після отримання `onNext()` лічильник спроб і затримка скидаються.
- Для інших статусів, зокрема `INVALID_ARGUMENT` та `CANCELLED`, retry не запускається.
- Явне відписування скасовує поточний RPC через `CancellableContext` і заплановану спробу через `ScheduledFuture`.
- У разі нормального `onCompleted()` клієнт видаляє підписку **без retry**.

При зміні статусу доставки на `DELIVERED` або `CANCELLED` подія зміни статусу передається з `shipment-service` через Kafka до `tracking-service`. Обробник `ShipmentStatusChangedListener` ініціює завершення відповідних Server Streaming-підписок через `onCompleted()`. Тут **`CANCELLED` як статус доставки** не слід плутати з **gRPC `Status.Code.CANCELLED`**.

Поточні підписки й заплановані retries зберігаються **в пам'яті процесу**, не в PostgreSQL. Після перезапуску `notification-service` їх потрібно створити заново.

## 5. Конфігурація й локальна перевірка

Сервер слухає `spring.grpc.server.port=${GRPC_SERVER_PORT:9090}`. Клієнт використовує:

```properties
spring.grpc.client.channel.tracking.target=${TRACKING_GRPC_TARGET:dns:///localhost:9094}
tracking.grpc.reconnect.initial-delay-ms=1000
tracking.grpc.reconnect.max-delay-ms=10000
tracking.grpc.reconnect.multiplier=2.0
```

У Docker Compose адреса всередині мережі — `dns:///tracking-service:9090`; для локального підключення з хоста передбачено порт `9094`. У Kubernetes налаштовано gRPC-порт `9090`, адресу `TRACKING_GRPC_TARGET` для notification та NetworkPolicy для трафіку notification → tracking.

Керування підписками через HTTP API `notification-service`:

```http
POST   /notifications/tracking/subscriptions/{shipmentId}
DELETE /notifications/tracking/subscriptions/{shipmentId}
```

Передбачено два види тестування:

- `tracking-service`: `ShipmentTrackingGrpcServiceIntegrationTest` — реальний gRPC-виклик і PostgreSQL через Testcontainers, фільтрація за `shipmentId`, передавання координат і валідація ID.
- `notification-service`: `ShipmentLocationGrpcClientTest` — тести повторного підключення, обробки помилок і скасування запланованих retries на mock-об'єктах.

Для ручної перевірки reconnect: підписатися на посилку, зупинити `tracking-service`, зафіксувати в логах `gRPC retry scheduled`, відновити сервер і перевірити повторне відкриття потоку. Завершення при `DELIVERED` / `CANCELLED` перевіряється окремо. Перед спільною здачею тести потрібно повторно виконати на **інтегрованій** версії всіх гілок.

## 6. Важливо для виконавців частин №2 і №3

1. **Розширювати спільний контракт** у `tracking-api`, зберігаючи існуючий RPC та номери protobuf-полів. Згенеровані Java-файли не редагувати вручну.
2. **Зберігати GPS через `LocationService.createLocation()`.** Це критично для Client Streaming та Bidirectional Streaming: прямий виклик `TrackingRepository.save()` обійде публікацію `LocationSavedEvent`, і поточні Server Streaming-підписники не отримають відповідне оновлення.
3. **Узгодити реалізацію серверних RPC.** Не реєструвати незалежно кілька `@GrpcService` для одного protobuf-сервісу `ShipmentTrackingService`; методи можна інтегрувати в узгоджену серверну реалізацію або завчасно виділити окремі protobuf-сервіси.
4. **Не припускати гарантованого replay.** Поточний Server Streaming передає нові оновлення активним підписникам; після перепідключення пропущені під час збою координати автоматично не відтворюються. `lastLocationId`/resume та дедуплікація — окремі можливі розширення, не частина наявного контракту.
5. **Не плутати механізми подій:** GPS → локальний Spring event після коміту → gRPC; зміна статусу доставки → Kafka → завершення gRPC-потоку. Повторні спроби для Client Streaming/Bidirectional Streaming потрібно проектувати з урахуванням можливого часткового приймання даних, а не механічно копіювати reconnect із Server Streaming.

**Поточна межа реалізації:** gRPC-клієнт `notification-service` отримує й логує GPS-повідомлення; передавання координат у UI, SMS/email чи окреме сховище на стороні notification у цю частину завдання не входить.
