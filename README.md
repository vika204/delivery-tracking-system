# Delivery Tracking System

Мікросервісна система керування кур'єрськими доставками та відстеженням посилок (Spring Boot 4.1, PostgreSQL, Docker Compose).

## Запуск

Потрібен Docker з Docker Compose.

1. Створити файл із налаштуваннями середовища на основі шаблону:

   ```bash
   cp .env.example .env
   ```

   Файл `.env` не потрапляє в git. Значення в шаблоні — лише приклад, для власного запуску їх варто замінити, особливо паролі.

2. Підняти сервіси разом із базами даних:

   ```bash
   docker compose up -d
   ```

   Кожен сервіс стартує лише після того, як його база пройде `healthcheck`.

3. Перевірити стан:

   ```bash
   docker compose ps
   ```

## Сервіси

| Сервіс | Порт на хості | База даних | Том |
|---|---|---|---|
| auth-service | 8081 | auth-db | auth-db-data |
| shipment-service | 8082 | shipment-db | shipment-db-data |
| dispatch-service | 8083 | dispatch-db | dispatch-db-data |

Кожен сервіс має власну базу. Усередині мережі `delivery-network` сервіси звертаються до баз за іменами служб (`shipment-db`, `dispatch-db`), а не через `localhost`.

## Перевірка HTTP і бази даних

### shipment-service

Створити посилку (запис у `shipment-db`):

```bash
curl -X POST localhost:8082/shipments \
  -H 'Content-Type: application/json' \
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

## Перевірка збереження даних

1. Переконатися, що записи лежать у самих базах (значення змінних беруться з `.env`):

   ```bash
   set -a && . ./.env && set +a
   docker compose exec shipment-db psql -U $SHIPMENT_DB_USER -d $SHIPMENT_DB_NAME -c 'select shipment_id, recipient_name, status from shipments'
   docker compose exec dispatch-db psql -U $DISPATCH_DB_USER -d $DISPATCH_DB_NAME -c 'select user_id, work_zone from couriers'
   ```

2. Видалити контейнери та мережу, залишивши томи, і підняти все знову:

   ```bash
   docker compose down
   docker compose up -d
   ```

3. Повторити читання з розділу вище. Раніше створені посилки та кур'єри мають повернутися без змін.

## Зупинка

```bash
docker compose down
```

Команда зберігає томи з даними. Прапорець `-v` видаляє томи разом із даними.
