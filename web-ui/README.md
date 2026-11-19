# WEB EXPRESS — web-ui

## Орієнтири для подальших змін

За документом викладача «MS-SpringBoot: Вимоги до проєкту і деталі заліку»:

- Єдиний стиль коду, DRY/SOLID; без мертвого коду, дублювання та зайвих абстракцій.
- Вимога викладача — 0 зауважень SonarQube for IDE у кожному сервісі; проходження Maven-тестів не замінює цей аналіз.
- Розділення MVC-контролерів, REST-клієнтів, DTO та ViewModel. Новий бізнес-сервіс потрібен за наявності бізнес-логіки.
- Декларативні REST-клієнти через Spring Boot RestClient та `@HttpExchange`.
- Міжсервісні DTO — Java records. Без читання чужих БД і передавання JPA entities через API.
- Валідація вхідних даних і централізована обробка помилок. JSON API — ProblemDetail, HTML — зрозумілий стан помилки з належним HTTP-статусом.
- Конфігурація через властивості та змінні середовища. Секрети не потрапляють у код, Git або логи.
- Збереження gRPC-контрактів, retries, Kafka/Outbox, логування та tracing при змінах суміжного коду. Outbox потрібен для змін бізнес-стану, а не для читання списків.
- Тести без попереднього ручного запуску зовнішніх БД і брокерів: моки або автоматично керовані тестові залежності.
- Загальні вимоги системи також включають єдину точку входу, колекцію запитів, Rate Limiter, Circuit Breaker/Fallback, Prometheus/Micrometer/Grafana, JSON-логи/MDC і tracing із traceId/spanId.
- Зберігати Database-per-Service, внутрішню мережеву ізоляцію та Health Probes. Контейнери — Multi-stage Dockerfile, Java 25, non-root.
- Для захисту потрібні contract-тести, перевірка відмовостійкості через Toxiproxy, демонстрація реєстрації/логіну та трьох міжсервісних бізнес-операцій. Презентація з Live Demo — до 15 хвилин на команду.

Це орієнтири з вимог, а не заява, що весь перелік уже реалізований у web-ui.

## Структура Java-пакетів

DTO розташовуємо біля тієї частини застосунку, якій вони належать:

```text
com.delivery.webui
├── client
│   └── dto         — запити, відповіді й результати REST-клієнтів інших сервісів
├── controller
│   ├── dto         — запити та JSON-відповіді власних HTTP endpoint
│   └── support     — спільна обробка помилок і перевірки доступу
├── grpc
│   └── dto         — дані потокових оновлень
├── service         — логіка підготовки даних і роботи з потоками
├── viewmodel       — дані для Thymeleaf-шаблонів
└── config          — конфігурація Spring
```

Якщо контролер передає той самий контракт без змін, використовуємо наявний DTO з `client/dto`, а не створюємо його копію.
Приватні типи для внутрішнього стану залишаємо всередині відповідного класу.
Тести повторюють структуру пакетів основного коду.

## REST-клієнти

Усі сім REST-клієнтів у `client` — інтерфейси з `@HttpExchange` та `@GetExchange` / `@PostExchange` / `@PutExchange`.
`HttpClientConfiguration` реєструє їх через стандартний `@ImportHttpServices`; Spring створює реалізації автоматично.
Адреси й таймаути задаються в `application.properties` через `spring.http.serviceclient.*`.
Збережені наявні `shipment.client.base-url`, `dispatch.client.base-url`, `tracking.client.base-url` та відповідні змінні середовища.
Connect timeout — 2 секунди, read timeout — 3 секунди; для створення посилки й розрахунку ціни — 20 секунд.

Коли потрібні HTTP-статус або заголовки, клієнт повертає стандартний `ResponseEntity<DTO>`.
`AvailabilityRequest` лежить у `client/dto`: це спільний контракт браузера та запиту до dispatch-service.
Прапорець `dispatch.courier-lookup.enabled` перевіряє `TrackingViewService` перед викликом клієнта.
gRPC та SSE від цього рефакторингу не змінюються.

## Обробка помилок

- `ApiExceptionHandler` — спільний `@RestControllerAdvice` для REST-контролерів. Наслідує стандартний Spring `ResponseEntityExceptionHandler`, який формує `ProblemDetail` для помилок JSON, параметрів та заголовків.
- Відомі помилки бекенду 4xx зберігають HTTP-статус, публічний `type`, `detail` та помилки полів `errors`. Недоступність бекенду і його 5xx перетворюються на 503. Непередбачена помилка web-ui повертає 500 із нейтральним повідомленням; подробиці залишаються в серверних логах.
- `TrackingPageAdvice` показує наявну сторінку пошуку з модальним повідомленням та HTML-fallback: некоректний ID — 400, посилку не знайдено — 404, сервіс недоступний — 503.
- `MyParcelsPageAdvice` залишає оформлений стан помилки списку з HTTP 503; відсутня посилка повертає HTML 404.
- Звичайні HTML-сторінки 404/500 обслуговує стандартний механізм Spring Boot. Stack trace, exception і внутрішні повідомлення не включаються в його відповіді, зокрема з увімкненими DevTools.
- Очікувана відсутність GPS або імені кур'єра обробляється як часткові дані в `TrackingViewService`. Помилки вже відкритого SSE-потоку обробляє `TrackingStreamService`; вони не замінюються JSON-відповіддю.

## My Parcels

`GET /my-parcels` читає `GET /shipments/mine` через `ShipmentReadClient`.
Бекенд повертає найновіші посилки першими. На сторінці до 20 посилок; PREVIOUS/NEXT відкривають інші сторінки.
`GET /my-parcels/{id}` завантажує реальні деталі цієї посилки.
Статус та ім’я кур’єра надходять із shipment-service без окремого запиту до dispatch-service.
Дані оновлюються при відкритті або перезавантаженні сторінки.

До інтеграції Keycloak ці сторінки доступні лише з loopback-адреси.
Клієнт задається `SHIPMENT_CREATION_SENDER_ID` (типове значення `42`), як у Create Shipment.
Це спільна локальна тестова ідентичність, а не автентифікація чи повноцінна авторизація.
Після підключення Keycloak senderId потрібно отримувати з даних авторизованого користувача.

Запуск із кореня репозиторію:

```powershell
$env:SHIPMENT_SERVICE_URL = "http://localhost:8082"
$env:SHIPMENT_CREATION_ENABLED = "true"
$env:SHIPMENT_CREATION_SENDER_ID = "42"
mvn -f web-ui/pom.xml spring-boot:run
```

Перевірки:

```powershell
mvn -f web-ui/pom.xml clean verify
npm --prefix web-ui run test:js
```
