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

Усі REST-клієнти у `client` — інтерфейси з `@HttpExchange` та `@GetExchange` / `@PostExchange` / `@PutExchange`.
`HttpClientConfiguration` реєструє їх через стандартний `@ImportHttpServices`; Spring створює реалізації автоматично.
Адреси й таймаути задаються в `application.properties` через `spring.http.serviceclient.*`.
Адреси сервісів задаються через `shipment.client.base-url`, `dispatch.client.base-url`, `tracking.client.base-url`, `notification.client.base-url`, `auth.client.base-url` та відповідні змінні середовища.
Connect timeout — 2 секунди, read timeout — 3 секунди; для створення посилки й розрахунку ціни — 20 секунд.

Коли потрібні HTTP-статус або заголовки, клієнт повертає стандартний `ResponseEntity<DTO>`.
`AvailabilityRequest` лежить у `client/dto`: це спільний контракт браузера та запиту до dispatch-service.
Прапорець `dispatch.courier-lookup.enabled` перевіряє `TrackingViewService` перед викликом клієнта.
gRPC та SSE від цього рефакторингу не змінюються.

## Обробка помилок

- Обробники помилок і власний `ShipmentNotInTransitException` розташовані в пакеті `com.delivery.webui.exception`.
- `ApiExceptionHandler` — спільний `@RestControllerAdvice` для REST-контролерів. Наслідує стандартний Spring `ResponseEntityExceptionHandler`, який формує `ProblemDetail` для помилок JSON, параметрів та заголовків.
- Відомі помилки бекенду 4xx зберігають HTTP-статус, публічний `type`, `detail` та помилки полів `errors`. Таймаут з'єднання або очікування відповіді повертає 504, відмова з'єднання та інші помилки доступу чи 5xx бекенду — 503. Непередбачена помилка web-ui повертає 500 із нейтральним повідомленням; подробиці залишаються в серверних логах.
- `TrackingPageAdvice` показує наявну сторінку пошуку з модальним повідомленням та HTML-fallback: некоректний ID — 400, посилку не знайдено — 404, сервіс недоступний — 503.
- `MyParcelsPageAdvice` залишає оформлений стан помилки списку з HTTP 503; відсутня посилка повертає HTML 404.
- `NotificationsPageAdvice` повертає сторінку Notifications зі станом помилки та HTTP 503. HTML-сторінки при таймаутах зберігають ті самі оформлені стани недоступності.
- Звичайні HTML-сторінки 404/500 обслуговує стандартний механізм Spring Boot. Stack trace, exception і внутрішні повідомлення не включаються в його відповіді, зокрема з увімкненими DevTools.
- Очікувана відсутність GPS або імені кур'єра обробляється як часткові дані в `TrackingViewService`. Помилки вже відкритого SSE-потоку обробляє `TrackingStreamService`; вони не замінюються JSON-відповіддю.

## Notifications

`GET /notifications` показує 50 останніх повідомлень поточного локального клієнта, від нових до старих.
`NotificationClient` викликає `GET /notifications/mine?limit=50` у notification-service із заголовком `X-User-Id`.
Джерело — власна PostgreSQL-база notification-service, із сортуванням `createdAt DESC, notificationId DESC` і фільтром за `userId`.
Сервіс зберігає повідомлення про створення посилки та зміни її статусу з наявних Kafka-подій.
Повторне отримання події з тим самим `eventId` не створює нового повідомлення.

Сторінка використовує спільний Header, картки й кнопки My Parcels. Порожній список — HTTP 200; помилка завантаження — оформлений стан з HTTP 503.
Посилання DETAILS передає `from=notifications`: деталі посилки зберігають активний пункт NOTIFICATIONS і повертають до повідомлень. Звичайне відкриття деталей із My Parcels зберігає попередню навігацію.
Поле `read` надходить із наявного `isRead` у notification-service. Нові картки позначені SVG-дзвіночком, трохи висунутим за правий верхній кут картки.
Header позначає непрочитані повідомлення маленькою червоною крапкою біля NOTIFICATIONS: перевіряє `GET /api/notifications/unread` при відкритті та приблизно раз на 5 секунд. Для Guest і Courier ці запити не виконуються.
Відкрита сторінка Notifications запитує `GET /notifications?fragment=true`: сервер повертає лише фрагмент наявного Thymeleaf-шаблону з останніми 50 повідомленнями.
Картки зіставляються за ID: нові додаються в порядку бекенду, наявні зберігають підсвічування, дублікати не створюються.
Після показу нових повідомлень виконується `POST /api/notifications/read?throughId=...`: notification-service позначає прочитаною історію поточного клієнта до найбільшого завантаженого ID та повертає, чи залишилися нові повідомлення. Для вже підтвердженої межі POST повторно не виконується.
Повідомлення з більшими ID не змінюються. Позначення ідемпотентне, а GET сторінки сам по собі не змінює БД.
На поточному відкритті дзвіночки та світліший фон нових карток зберігаються; при наступному відкритті прочитані картки мають звичайний вигляд.
Опитування послідовне: наступний запит планується після завершення попереднього. У прихованій вкладці мережеві запити пропускаються. При помилці поточний список залишається, а наступне опитування пробує оновити його знову.
Поки немає Keycloak, використовується той самий `SHIPMENT_CREATION_SENDER_ID`, що й у My Parcels; доступ до сторінки лише з loopback-адреси.
Це локальний режим розробки, а не авторизація. `notification-service` має бути запущений окремо.

```powershell
$env:NOTIFICATION_SERVICE_URL = "http://localhost:8085"
$env:SHIPMENT_CREATION_SENDER_ID = "42"
```

## My Parcels

`GET /my-parcels` читає `GET /shipments/mine` через `ClientShipmentClient`.
Бекенд повертає найновіші посилки першими. На сторінці до 20 посилок; PREVIOUS/NEXT відкривають інші сторінки.
`GET /my-parcels/{id}` завантажує реальні деталі цієї посилки.
У My Parcels та деталях кнопка CANCEL SHIPMENT доступна для WAITING_FOR_COURIER, ASSIGNED і TO_PICKUP.
Вона відкриває наявний WebExpressModal, після підтвердження викликає `POST /api/shipments/{id}/cancel`.
`ClientShipmentApiController` передає один запит через `ClientShipmentClient` у наявний `POST /shipments/{id}/cancel`.
Власника та допустимий статус перевіряє shipment-service. Після успіху спільний `js/components/shipment-cancel.js` оновлює поточну картку до CANCELLED без перезавантаження.
Для IN_TRANSIT, DELIVERED і CANCELLED кнопка прихована. Помилка показується в модальному вікні.
TRACK PACKAGE у скасованої посилки неактивна й не містить посилання. Для DELIVERED трекінг залишається доступним, щоб переглядати історію маршруту.
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

Для ручної перевірки скасування:

```powershell
docker compose up -d shipment-service dispatch-service tracking-service notification-service

$env:SHIPMENT_SERVICE_URL = "http://localhost:8082"
$env:SHIPMENT_CREATION_SENDER_ID = Read-Host "ID власника тестової посилки"
mvn -f web-ui/pom.xml spring-boot:run
```

На `/my-parcels` вибери свою тестову посилку до забору → DETAILS → CANCEL SHIPMENT.
KEEP SHIPMENT закриває підтвердження без запиту; YES, CANCEL виконує скасування.
У картці з'являється CANCELLED, кнопка скасування зникає, а TRACK PACKAGE стає неактивною. Так само працює скасування безпосередньо у My Parcels.
При переході з Notifications зберігаються відповідний Header і кнопка повернення.
Наявний Outbox/Kafka-ланцюжок обробляє скасування, звільняє призначення та створює сповіщення.
Доступ до нового API web-ui, як і до My Parcels, обмежений локальним середовищем.

## Редагування профілів

`/profile` і `/courier/profile` використовують один `js/pages/profile.js`.
Скрипт завантажує поточні дані, відкриває форму, виконує PUT та показує відповідь сервера.
Cancel відкидає чернетку. При помилці форма зберігає введені дані й показує наявний WebExpressModal.
Фото — спільні статичні ресурси; полів завантаження чи API редагування фото немає.

- Клієнт: `GET/PUT /api/profile` → `UserProfileClient` → `GET/PUT /users/profile` в auth-service.
- Кур'єр: `GET/PUT /api/courier/profile` → дані користувача в auth-service та наявні `GET/PATCH /couriers/{id}` у dispatch-service.
- В auth-service DTO містить тільки ім'я, прізвище, email і телефон. Роль, пароль та ID під час оновлення не змінюються.
- Dispatch зберігає ім'я й телефон кур'єра, робочу зону, код КАТОТТГ і максимальну вагу. Застосовується наявна логіка оновлення профілю кур'єра.
- Два сервіси оновлюються послідовно. Якщо другий запит не пройшов, інтерфейс повідомляє про можливе часткове збереження та пропонує повторити Save. Спільної транзакції між БД немає.

Для локального запуску:

```powershell
docker compose up -d --build auth-service
docker compose up -d dispatch-service

$users = Invoke-RestMethod "http://localhost:8081/users"
$users | Select-Object userId, firstName, lastName, role
$couriers = Invoke-RestMethod "http://localhost:8083/couriers"
$couriers | Select-Object userId, displayName, workZone
```

В окремому PowerShell із кореня проєкту вибери існуючі ID зі списків:

```powershell
$env:AUTH_SERVICE_URL = "http://localhost:8081"
$env:SHIPMENT_CREATION_SENDER_ID = Read-Host "User ID клієнта з auth-service"
$env:DISPATCH_COURIER_DASHBOARD_COURIER_ID = Read-Host "Спільний User ID кур'єра в auth-service та dispatch-service"
mvn -f web-ui/pom.xml spring-boot:run
```

До Keycloak використовуються локальні тестові ID, а нові API web-ui обмежені loopback-адресами.
Профіль потребує існуючого запису User; для кур'єра потрібен також Courier із тим самим userId.
Відсутній User повертає 404, невірні поля — 400, конфлікт даних — 409, недоступний сервіс — 503/504.

Ручна перевірка: відкрити обидва профілі, натиснути Edit, змінити поля, зберегти й перезавантажити сторінку.
Перевірити, що зміни залишились, Cancel не зберігає чернетку, КАТОТТГ обирає справжній населений пункт, а фото незмінні.
