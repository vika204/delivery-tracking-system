# Delivery Tracking System

Мікросервісна система керування кур'єрськими доставками та відстеженням посилок (Spring Boot 4.1, PostgreSQL, Docker Compose).

## Запуск

Потрібен Docker з Docker Compose.

1. Створити файл із налаштуваннями середовища на основі шаблону:

   ```bash
   cp .env.example .env
   ```

   Файл `.env` не потрапляє в git. Значення в шаблоні — лише приклад, для власного запуску їх варто замінити, особливо паролі.

2. Заповнити `GEOAPIFY_API_KEY` у локальному `.env` для розрахунку доставки. Підняти web-ui, п'ять бекендів, бази даних і Kafka:

   ```bash
   docker compose up -d --build --wait --wait-timeout 600
   ```

   Бекенди стартують після готовності їхніх БД і, де потрібно, Kafka. UI стартує після готовності п'яти бекендів. Перший запуск включає збірку образів; для Java-застосунків передбачено до 5 хвилин початкового періоду healthcheck.

3. Перевірити стан:

   ```bash
   docker compose ps
   ```

   Відкрити [http://localhost:8086/](http://localhost:8086/). Якщо на цьому порту вже працює web-ui з IntelliJ або Kubernetes port-forward, спочатку зупини попередній процес.

   GPS-історія доступна без додаткового прапорця. Пошук кур'єра для картки та SSE увімкнені в локальному Compose. За потреби їх можна вимкнути через `DISPATCH_COURIER_LOOKUP_ENABLED=false` або `TRACKING_STREAM_ENABLED=false` у `.env`. Spring Security працює; `WEB_LOCAL_DEV_LOOPBACK_ONLY=false` дозволяє доступ через Docker-мережу.

   Усередині контейнерів UI звертається до `http://shipment-service:8080`, `http://tracking-service:8080` та інших імен сервісів. gRPC використовує `tracking-service:9090`. Порти на хості відкриті лише на `127.0.0.1`.

## Сервіси

| Сервіс | Порт на хості | База даних | Том |
|---|---|---|---|
| auth-service | 8081 | auth-db | auth-db-data |
| shipment-service | 8082 | shipment-db | shipment-db-data |
| dispatch-service | 8083 | dispatch-db | dispatch-db-data |
| tracking-service | 8084 | tracking-db | tracking-db-data |
| notification-service | 8085 | notification-db | notification-db-data |
| web-ui | 8086 | — | — |

gRPC tracking-service доступний локальним інструментам на `localhost:9094`, Kafka — на `localhost:9092`.

Для ручної перевірки резервного екрана спочатку відкрий трекінг існуючої посилки, потім виконай `docker compose stop shipment-service` і онови сторінку. Відновлення: `docker compose start shipment-service`. Команда stop залишає БД і дані; `unless-stopped` не скасовує ручну зупинку. Якщо Circuit Breaker відкрився, після готовності сервісу дай йому щонайменше 10 секунд перед наступним запитом.

Кожен сервіс має власну базу. Усередині мережі `delivery-network` сервіси звертаються до баз за іменами служб (`shipment-db`, `dispatch-db`), а не через `localhost`.

## Перевірка HTTP і бази даних

### shipment-service

Створити посилку (запис у `shipment-db`):

```bash
curl -X POST localhost:8082/shipments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-1001' \
  -d '{"userId":1,"recipientName":"Ivan Petrenko","recipientPhone":"+380501112233","pickupAddress":"Kyiv, Khreshchatyk 1","deliveryAddress":"Lviv, Rynok Sq 5","weight":2.5,"length":30,"width":20,"height":10,"distance":540,"price":150.00,"status":"WAITING_FOR_COURIER"}'
```

Прочитати посилки (читання з бази):

```bash
curl localhost:8082/shipments
curl localhost:8082/shipments/1
```

Неіснуючий `id` повертає `404`.

### dispatch-service

Створити кур'єра (запис у `dispatch-db`):

```bash
curl -X POST localhost:8083/couriers \
  -H 'Content-Type: application/json' \
  -d '{"userId":10,"workZone":"Kyiv","maxPackageWeight":15.5,"isAvailable":true}'
```

Прочитати кур'єрів (читання з бази):

```bash
curl localhost:8083/couriers
curl localhost:8083/couriers/10
```

У запиті прапорець доступності передається як `isAvailable`, а у відповіді повертається як `available`. Неіснуючий `id` повертає `404`.

### tracking-service

Записати GPS-точку кур'єра (координати й час перевіряються; точка одразу йде підписникам gRPC-стріму):

```bash
curl -X POST localhost:8084/tracking/shipments/1/locations \
  -H 'Content-Type: application/json' \
  -d '{"latitude":50.4501,"longitude":30.5234,"recordedAt":"2026-09-21T12:00:00Z"}'
```

Некоректна точка повертає `400`, підозрілий стрибок позиції — `422`. Необов'язковий `pointId` (UUID)
робить повтор безпечним: та сама точка з тим самим `pointId` повертає вже збережену (`200`) без дубля.

Прочитати локації (читання з бази):

```bash
curl localhost:8084/tracking
curl localhost:8084/tracking/1
```

Неіснуючий `id` повертає `404`.

### notification-service

Створити сповіщення (запис у `notification-db`):

```bash
curl -X POST localhost:8085/notifications \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"shipmentId":1,"type":"SHIPMENT_CREATED","message":"Your shipment has been created."}'
```

Прочитати сповіщення (читання з бази):

```bash
curl localhost:8085/notifications
curl localhost:8085/notifications/1
```

Неіснуючий `id` повертає `404`.

## Shipment API для Tracking

Усі помилки shipment-service повертаються у форматі `ProblemDetail` (RFC 9457) з `Content-Type: application/problem+json`. Усі запити приймають заголовок `X-Correlation-Id` (якщо його немає, сервіс генерує власний) і повертають його у відповіді.

| Метод і шлях | Призначення | Відповідь |
|---|---|---|
| `GET /shipments/{id}` | Одна посилка | `ShipmentResponse`, або `404` |
| `GET /shipments/batch?ids=1,2,3` | Кілька посилок одним запитом (від 1 до 100 унікальних `id`) | `ShipmentBatchResponse` |
| `POST /shipments` | Створення посилки, обов'язковий заголовок `Idempotency-Key` | `ShipmentResponse` |

`ShipmentBatchResponse` повертає знайдені посилки у порядку запиту та окремо `id`, яких немає. Відсутні `id` не дають помилки:

```json
{
  "shipments": [
    {"shipmentId": 1, "status": "IN_TRANSIT", "pickupAddress": "Kyiv, Khreshchatyk 1", "deliveryAddress": "Lviv, Rynok Sq 5"}
  ],
  "missingIds": [999]
}
```

`ShipmentSummary` (елемент `shipments`) містить лише `shipmentId`, `status`, `pickupAddress`, `deliveryAddress`. `ShipmentResponse` містить усі поля посилки.

`POST /shipments` без заголовка `Idempotency-Key` (або з порожнім чи довшим за 255 символів) повертає `400`. З однаковим `Idempotency-Key` і однаковим тілом посилка не дублюється, а повертається та сама із заголовком `Idempotent-Replayed: true`. Той самий ключ з іншим тілом дає `422`.

| Код | Коли |
|---|---|
| `400` | Некоректне тіло, параметри або відсутній `Idempotency-Key` |
| `404` | Посилку не знайдено |
| `422` | `Idempotency-Key` уже використано з іншим тілом |
| `500` | Непередбачена помилка |

## Перевірка збереження даних

1. Переконатися, що записи лежать у самих базах (значення змінних беруться з `.env`):

   ```bash
   set -a && . ./.env && set +a
   docker compose exec shipment-db psql -U $SHIPMENT_DB_USER -d $SHIPMENT_DB_NAME -c 'select shipment_id, recipient_name, status from shipments'
   docker compose exec dispatch-db psql -U $DISPATCH_DB_USER -d $DISPATCH_DB_NAME -c 'select user_id, work_zone from couriers'
   docker compose exec tracking-db psql -U $TRACKING_DB_USER -d $TRACKING_DB_NAME -c 'select location_id, shipment_id, latitude, longitude from locations'
   docker compose exec notification-db psql -U $NOTIFICATION_DB_USER -d $NOTIFICATION_DB_NAME -c 'select notification_id, user_id, type, message from notifications'
   ```

2. Видалити контейнери та мережу, залишивши томи, і підняти все знову:

   ```bash
   docker compose down
   docker compose up -d
   ```

3. Повторити читання з розділу вище. Раніше створені посилки та кур'єри мають повернутися без змін.

## Колекція запитів

`docs/api/delivery-tracking.postman_collection.json` (Postman, Insomnia або Bruno). Усі запити йдуть через єдину точку входу web-ui (`{{baseUrl}}` = `http://localhost:8086`), сесійна cookie після входу підхоплюється автоматично. Папки 1–5 запускаються по черзі: реєстрація й вхід, створення посилки, публічне відстеження, дії кур'єра, помилки валідації та перевищення rate limit (`429` з `Retry-After`). Папка 6 звертається до сервісів напряму й призначена лише для локального налагодження.

## Тести і CI

```bash
for m in tracking-api tracking-grpc-client web-problems; do (cd $m && mvn install); done
for m in web-ui tracking-service notification-service dispatch-service shipment-service auth-service; do (cd $m && mvn verify); done
```

Потрібен лише Docker: бази й брокер піднімають Testcontainers і вбудований Kafka. Контрактні тести Pact виконуються в тому самому прогоні: споживачі оновлюють `pacts/`, провайдери їх перевіряють. Звіти покриття JaCoCo: `<модуль>/target/site/jacoco/index.html`.

GitHub Actions (`.github/workflows/build.yml`) запускає те саме на кожен pull request, падає, якщо закомічені pact-файли не збігаються з тестами споживачів, і збирає Docker-образи всіх сервісів.

## Зупинка

```bash
docker compose down
```

Команда зберігає томи з даними. Прапорець `-v` видаляє томи разом із даними.


## Kubernetes (Minikube)

Усі ресурси застосунку та моніторингу описані в `k8s/kustomization.yaml`. Команда `kubectl apply -k k8s` розгортає шість застосунків, п'ять PostgreSQL, Kafka, Grafana, Prometheus, Loki, Tempo, OpenTelemetry та Alertmanager. Telegram і Toxiproxy підключаються окремо.

Це локальне dev-середовище. Зовнішній Ingress веде лише до `web-ui`; REST API бекендів, PostgreSQL, Kafka, gRPC і chaos endpoint залишаються всередині кластера.

### Кластер і образи

Для нового кластера потрібен CNI з підтримкою NetworkPolicy. Приклад створює окремий профіль `delivery-dev` із Calico, не видаляючи наявний профіль `minikube` та його дані. Для повного набору з моніторингом у прикладі виділено 6 CPU і 10 GiB RAM.

```powershell
minikube start -p delivery-dev --driver=docker --cni=calico --cpus=6 --memory=10240
minikube -p delivery-dev addons enable ingress
kubectl config use-context delivery-dev

minikube -p delivery-dev image build -t auth-service:1.0.0 ./auth-service
minikube -p delivery-dev image build -t shipment-service:1.0.0 ./shipment-service
minikube -p delivery-dev image build -t dispatch-service:1.0.0 -f dispatch-service/Dockerfile .
minikube -p delivery-dev image build -t tracking-service:1.0.0 -f tracking-service/Dockerfile .
minikube -p delivery-dev image build -t notification-service:1.0.0 -f notification-service/Dockerfile .
minikube -p delivery-dev image build -t web-ui:1.0.0 -f web-ui/Dockerfile .
```

Для вже наявного профілю заміни `delivery-dev` на його назву. Звичайний запуск Minikube без Calico або іншого відповідного CNI не гарантує виконання NetworkPolicy. Перемикання профілю не переносить БД між кластерами.

### Секрети й розгортання

Заповни локальний `.env` за `.env.example`: логіни й паролі п'яти БД та `GEOAPIFY_API_KEY`. Скрипт читає ці значення, перевіряє їх і надсилає в Kubernetes через stdin. Він не створює файлів із паролями й не друкує значень.

```powershell
kubectl --context=delivery-dev apply -f k8s/namespace.yml
.\docs\scripts\apply-k8s-secrets.ps1 -Context delivery-dev -ValidateOnly
.\docs\scripts\apply-k8s-secrets.ps1 -Context delivery-dev

kubectl --context=delivery-dev apply --dry-run=server -k k8s
kubectl --context=delivery-dev apply -k k8s

foreach ($name in @('auth-service', 'shipment-service', 'dispatch-service', 'tracking-service', 'notification-service', 'web-ui')) {
    kubectl --context=delivery-dev -n delivery rollout status deployment/$name --timeout=600s
    if ($LASTEXITCODE -ne 0) { break }
}
kubectl --context=delivery-dev -n delivery get pods,pvc
```

Секрети не входять до Kustomization: вони мають бути створені перед запуском застосунків. Зміна Secret або ConfigMap сама не перезапускає вже запущені контейнери; після зміни потрібен `kubectl rollout restart deployment/<name> -n delivery`.

Для нового образу з тим самим тегом спочатку зупини потрібний Deployment через `kubectl scale deployment/<name> --replicas=0 -n delivery`, перебудуй образ у потрібному профілі й поверни попередню кількість реплік. Це локальне оновлення з перервою в роботі, а не production rollout.

### Відкриття застосунку

У двох окремих терміналах залиш працювати:

```powershell
kubectl --context=delivery-dev -n ingress-nginx port-forward service/ingress-nginx-controller 8086:80
```

```powershell
kubectl --context=delivery-dev -n delivery port-forward service/grafana 3000:3000
```

WEB EXPRESS: [http://localhost:8086/](http://localhost:8086/). Grafana: [http://localhost:3000/](http://localhost:3000/). Попередній локальний процес web-ui на порту 8086 потрібно зупинити.

Для ручних REST-запитів використовуй окремий port-forward, наприклад:

```powershell
kubectl --context=delivery-dev -n delivery port-forward service/shipment-service 18082:8080
```

### Особливості локального розгортання

- `startupProbe` дає Java-застосункам до 5 хвилин на запуск перед перевіркою liveness.
- `web-ui` і `tracking-service` мають по одній репліці: HTTP-сесія UI та live gRPC-підписки зараз зберігаються в пам'яті процесів.
- `WEB_LOCAL_DEV_LOOPBACK_ONLY=false` дозволяє UI приймати запити через локальний Kubernetes-проксі. При звичайному запуску перевірка localhost зберігається. Spring Security та перевірки даних залишаються активними.
- GPS history, SSE й courier lookup увімкнені явно в ConfigMap цього dev-середовища.
- NetworkPolicy дозволяють Ingress-контролеру звертатися до UI, UI — до HTTP/gRPC бекендів, а бекендам — до їхніх БД, Kafka та моніторингу. Обмежено вхідний трафік; вихідний трафік не ізольований, тому працюють DNS і Geoapify.
- П'ять БД, Kafka і сховища моніторингу використовують PVC. Старі дані з `emptyDir` автоматично не копіюються в новий PVC: перед оновленням старого кластера окремо збережи потрібну історію Kafka й моніторингу. Наявні PVC PostgreSQL не видаляються.
- Grafana налаштована для локального перегляду без входу. Цей набір не призначений для публікації в інтернет.
- Класи `tracking-api` і `tracking-grpc-client` — Maven-бібліотеки, окремі контейнери їм не потрібні.

### Додаткові сценарії

Для Toxiproxy:

```powershell
kubectl --context=delivery-dev apply -f k8s/testing/toxiproxy.yml
kubectl --context=delivery-dev -n delivery set env deployment/tracking-service SHIPMENT_SERVICE_URL=http://toxiproxy:8666
kubectl --context=delivery-dev -n delivery rollout status deployment/tracking-service --timeout=600s
```

Після цього в окремих терміналах відкрий порти `18084:8080` для `service/tracking-service` і `18474:8474` для `service/toxiproxy` та запусти `docs/scripts/scenario-toxiproxy.ps1 -ShipmentId <існуючий-ID>`. Після демонстрації поверни пряме з'єднання:

```powershell
kubectl --context=delivery-dev -n delivery set env deployment/tracking-service SHIPMENT_SERVICE_URL=http://shipment-service:8080
```

Без Telegram сповіщення про збої доступні в Alertmanager. Для Telegram додай `BOT_TOKEN` і `CHAT_ID` у локальний `.env`, після чого:

```powershell
.\docs\scripts\apply-k8s-secrets.ps1 -Context delivery-dev -IncludeTelegram
minikube -p delivery-dev image build -t telegram-alert-bridge:1.0.0 ./telegram-alert-bridge
kubectl --context=delivery-dev apply -f k8s/monitoring/telegram-alert-bridge.yml
kubectl --context=delivery-dev -n delivery rollout restart deployment/alertmanager
```

Повторне `apply -k k8s` повертає локальний receiver Alertmanager; якщо потрібен Telegram, після нього повторно застосуй `telegram-alert-bridge.yml` і перезапусти Alertmanager.
