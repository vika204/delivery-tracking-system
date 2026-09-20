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

## Зупинка

```bash
docker compose down
```

Команда зберігає томи з даними. Прапорець `-v` видаляє томи разом із даними.
