# Delivery Tracking System

Мікросервісна система кур'єрської доставки: оформлення посилки за розрахованою ціною, автоматичний пошук кур'єра, забір і доставка, відстеження посилки на карті в реальному часі та сповіщення відправника.

Spring Boot 4.1 на Java 25, PostgreSQL (окрема база для кожного сервісу), Kafka з Transactional Outbox, REST через `@HttpExchange`, gRPC між сервісами, моніторинг на Prometheus, Grafana, Loki і Tempo.

| Сервіс | Що робить | Сховище |
|---|---|---|
| web-ui | Єдина точка входу: сторінки, REST API для браузера, сесії, rate limit | — |
| auth-service | Користувачі, вхід, профіль | auth-db |
| shipment-service | Ціна, посилки, статуси, outbox-події | shipment-db |
| dispatch-service | Кур'єри, пропозиції, призначення | dispatch-db |
| tracking-service | GPS-точки, історія маршруту, gRPC-стріми | tracking-db |
| notification-service | Сповіщення за подіями з Kafka | notification-db |

Спільні Maven-модулі (`tracking-api`, `tracking-grpc-client`, `web-problems`) — це бібліотеки, окремих контейнерів у них немає.

Є два способи запуску: [Docker Compose](#запуск-у-docker-compose) (швидко, без моніторингу) і [Kubernetes на Minikube](#запуск-у-kubernetes-minikube) (повний набір із моніторингом і NetworkPolicy).

## Налаштування `.env`

Обидва способи беруть паролі та ключі з локального `.env`, який не потрапляє в git:

```bash
cp .env.example .env
```

| Змінна | Призначення |
|---|---|
| `<SERVICE>_DB_NAME`, `<SERVICE>_DB_USER`, `<SERVICE>_DB_PASSWORD` | База кожного сервісу (`AUTH`, `SHIPMENT`, `DISPATCH`, `TRACKING`, `NOTIFICATION`). Значення в шаблоні — лише приклад, замініть паролі. |
| `GEOAPIFY_API_KEY` | Ключ [Geoapify](https://www.geoapify.com/) для геокодування адрес і розрахунку відстані. Без нього не працює розрахунок ціни, тож і створення посилки. |
| `POSTGRES_VERSION`, `KAFKA_VERSION` | Версії образів (лише Compose). |
| `BOT_TOKEN`, `CHAT_ID` | Необов'язково: Telegram-сповіщення про алерти (лише Kubernetes). |

## Запуск у Docker Compose

Потрібен Docker Desktop (або Docker Engine) з Compose v2.

1. Зібрати образи й підняти шість застосунків, п'ять PostgreSQL і Kafka:

   ```bash
   docker compose up -d --build --wait --wait-timeout 600
   ```

   Сервіси стартують у правильному порядку: бекенди чекають на свої бази й Kafka, web-ui — на всі бекенди. Перша збірка займає кілька хвилин; Java-сервісам дано до 5 хвилин на запуск, перш ніж healthcheck вважатиме їх несправними.

2. Перевірити, що всі контейнери `healthy`:

   ```bash
   docker compose ps
   ```

3. Відкрити [http://localhost:8086](http://localhost:8086).

### Порти

Назовні відкрито лише `127.0.0.1`, тож із мережі машину не видно. Користувач працює тільки через web-ui; прямі порти сервісів — для налагодження.

| Сервіс | Адреса на хості |
|---|---|
| web-ui (точка входу) | http://localhost:8086 |
| auth-service | http://localhost:8081 |
| shipment-service | http://localhost:8082 |
| dispatch-service | http://localhost:8083 |
| tracking-service | http://localhost:8084, gRPC `localhost:9094` |
| notification-service | http://localhost:8085 |
| Kafka | `localhost:9092` |

Усередині мережі `delivery-network` сервіси звертаються один до одного за іменами (`http://shipment-service:8080`, `tracking-service:9090`, `shipment-db:5432`, `kafka:29092`).

### Оновлення після зміни коду

```bash
docker compose up -d --build <service>
```

Без `--build` Compose запустить старий образ. Якщо поведінка не збігається з кодом, найчастіше причина саме в цьому.

### Логи, зупинка, очищення

```bash
docker compose logs -f shipment-service
docker compose down        # зупиняє й видаляє контейнери, дані в томах лишаються
docker compose down -v     # видаляє й томи: бази стануть порожніми
```

## Запуск у Kubernetes (Minikube)

`k8s/kustomization.yaml` описує все середовище: шість застосунків, п'ять PostgreSQL, Kafka, Prometheus, Grafana, Loki, Tempo, OpenTelemetry Collector, Alertmanager, Ingress і NetworkPolicy. Ingress веде лише до web-ui; REST і gRPC бекендів, бази, Kafka і chaos-ендпоінт доступні тільки всередині кластера.

Потрібні: Docker, [minikube](https://minikube.sigs.k8s.io/), `kubectl`. Для повного набору з моніторингом — 6 CPU і 10 ГБ пам'яті.

Команди нижче для bash/zsh (macOS, Linux, WSL). Для PowerShell відрізняються лише цикли та скрипт секретів, їхні варіанти подано окремо.

### 1. Кластер

NetworkPolicy працюють лише з CNI, який їх підтримує, тому кластер створюється з Calico. Окремий профіль `delivery-dev` не чіпає наявний профіль `minikube` і його дані.

```bash
minikube start -p delivery-dev --driver=docker --cni=calico --cpus=6 --memory=10240
minikube -p delivery-dev addons enable ingress
kubectl config use-context delivery-dev
```

Для наявного профілю замініть `delivery-dev` на його назву. Перемикання профілю не переносить бази між кластерами.

### 2. Образи

Образи збираються прямо в Minikube, тож реєстр не потрібен (`imagePullPolicy: IfNotPresent`). Усі Dockerfile використовують корінь репозиторію як контекст, бо підтягують спільні модулі:

```bash
for service in auth-service shipment-service dispatch-service tracking-service notification-service web-ui; do
  minikube -p delivery-dev image build -t "$service:1.0.0" -f "$service/Dockerfile" . || break
done
```

<details>
<summary>PowerShell</summary>

```powershell
foreach ($service in @('auth-service', 'shipment-service', 'dispatch-service', 'tracking-service', 'notification-service', 'web-ui')) {
    minikube -p delivery-dev image build -t "${service}:1.0.0" -f "$service/Dockerfile" .
    if ($LASTEXITCODE -ne 0) { break }
}
```
</details>

### 3. Секрети

Секрети не входять до Kustomization і створюються з `.env` перед розгортанням. Скрипт перевіряє, що всі значення заповнені, і передає їх у `kubectl` через конвеєр: паролі не потрапляють ні у файли, ні в історію команд, ні у вивід.

```bash
kubectl --context=delivery-dev apply -f k8s/namespace.yml
docs/scripts/apply-k8s-secrets.sh --context delivery-dev --validate-only
docs/scripts/apply-k8s-secrets.sh --context delivery-dev
```

<details>
<summary>PowerShell</summary>

```powershell
kubectl --context=delivery-dev apply -f k8s/namespace.yml
.\docs\scripts\apply-k8s-secrets.ps1 -Context delivery-dev -ValidateOnly
.\docs\scripts\apply-k8s-secrets.ps1 -Context delivery-dev
```
</details>

### 4. Розгортання

```bash
kubectl --context=delivery-dev apply --dry-run=server -k k8s
kubectl --context=delivery-dev apply -k k8s

for service in auth-service shipment-service dispatch-service tracking-service notification-service web-ui; do
  kubectl --context=delivery-dev -n delivery rollout status "deployment/$service" --timeout=600s || break
done
kubectl --context=delivery-dev -n delivery get pods,pvc
```

<details>
<summary>PowerShell</summary>

```powershell
kubectl --context=delivery-dev apply --dry-run=server -k k8s
kubectl --context=delivery-dev apply -k k8s

foreach ($service in @('auth-service', 'shipment-service', 'dispatch-service', 'tracking-service', 'notification-service', 'web-ui')) {
    kubectl --context=delivery-dev -n delivery rollout status deployment/$service --timeout=600s
    if ($LASTEXITCODE -ne 0) { break }
}
kubectl --context=delivery-dev -n delivery get pods,pvc
```
</details>

### 5. Відкриття

Кожну команду залиште працювати у власному терміналі:

```bash
kubectl --context=delivery-dev -n ingress-nginx port-forward service/ingress-nginx-controller 8086:80
```

```bash
kubectl --context=delivery-dev -n delivery port-forward service/grafana 3000:3000
```

Застосунок: [http://localhost:8086](http://localhost:8086). Grafana: [http://localhost:3000](http://localhost:3000) (без входу, лише для локального перегляду). Якщо порт 8086 зайнятий, спершу зупиніть Compose-стек або локальний web-ui.

Для налагодження конкретного бекенда відкрийте окремий порт, наприклад:

```bash
kubectl --context=delivery-dev -n delivery port-forward service/shipment-service 18082:8080
```

### Оновлення образу

Зміна Secret або ConfigMap не перезапускає вже запущені поди:

```bash
kubectl --context=delivery-dev -n delivery rollout restart deployment/<service>
```

Новий образ із тим самим тегом поди не підхоплять самі. Зупиніть Deployment, перезберіть образ і поверніть попередню кількість реплік (auth, shipment і dispatch мають по 2, решта — по 1):

```bash
kubectl --context=delivery-dev -n delivery scale deployment/<service> --replicas=0
minikube -p delivery-dev image build -t <service>:1.0.0 -f <service>/Dockerfile .
kubectl --context=delivery-dev -n delivery scale deployment/<service> --replicas=<попередня кількість>
```

Це оновлення з короткою перервою для локального середовища, а не production rollout.

### Особливості середовища

- `startupProbe` дає Java-застосункам до 5 хвилин на запуск, перш ніж почне діяти liveness.
- `web-ui` і `tracking-service` працюють в одній репліці: HTTP-сесії, rate limit і живі gRPC-підписки зберігаються в пам'яті процесу.
- NetworkPolicy пропускають трафік лише так: Ingress → web-ui → бекенди (HTTP і gRPC) → їхні бази, Kafka, моніторинг. Обмежено вхідний трафік; вихідний відкритий, тому працюють DNS і Geoapify.
- Бази, Kafka і сховища моніторингу живуть на PVC і переживають перезапуск подів.

### Додатково: Toxiproxy і Telegram

Toxiproxy між tracking-service і shipment-service для демонстрації Circuit Breaker:

```bash
kubectl --context=delivery-dev apply -f k8s/testing/toxiproxy.yml
kubectl --context=delivery-dev -n delivery set env deployment/tracking-service SHIPMENT_SERVICE_URL=http://toxiproxy:8666
kubectl --context=delivery-dev -n delivery rollout status deployment/tracking-service --timeout=600s
```

Далі відкрийте порти `18084:8080` для `service/tracking-service` і `18474:8474` для `service/toxiproxy` та запустіть `docs/scripts/scenario-toxiproxy.ps1 -ShipmentId <ID наявної посилки>`. Повернути пряме з'єднання:

```bash
kubectl --context=delivery-dev -n delivery set env deployment/tracking-service SHIPMENT_SERVICE_URL=http://shipment-service:8080
```

Алерти без Telegram видно в Alertmanager. Щоб надсилати їх у Telegram, додайте `BOT_TOKEN` і `CHAT_ID` у `.env`:

```bash
docs/scripts/apply-k8s-secrets.sh --context delivery-dev --include-telegram
minikube -p delivery-dev image build -t telegram-alert-bridge:1.0.0 ./telegram-alert-bridge
kubectl --context=delivery-dev apply -f k8s/monitoring/telegram-alert-bridge.yml
kubectl --context=delivery-dev -n delivery rollout restart deployment/alertmanager
```

Повторний `apply -k k8s` повертає локальний receiver Alertmanager; після нього знову застосуйте `telegram-alert-bridge.yml` і перезапустіть Alertmanager.

### Видалення

```bash
kubectl --context=delivery-dev delete -k k8s
minikube delete -p delivery-dev      # видаляє весь кластер разом із даними
```

`delete -k` видаляє й PVC Kafka та моніторингу, бо вони описані в маніфестах. PVC баз даних створені StatefulSet-ами, тож лишаються, доки їх не видалити окремо (`kubectl -n delivery delete pvc --all`).

## Перевірка роботи

**Колекція запитів** `docs/api/delivery-tracking.postman_collection.json` (Postman, Insomnia, Bruno). Усі запити йдуть через web-ui (`{{baseUrl}}` = `http://localhost:8086`), сесійна cookie після входу підхоплюється автоматично. Папки 1–5 виконуються по черзі: реєстрація й вхід, створення посилки, публічне відстеження, дії кур'єра, помилки валідації та rate limit (`429` з `Retry-After`). Папка 6 звертається до сервісів напряму й працює лише з Compose.

**Chaos-експерименти** на запущеному Compose-стеку: `docs/scripts/chaos/run-chaos.sh` без аргументів лише перевіряє стан, `--yes` по черзі зупиняє сервіси, Kafka і базу та перевіряє, що система деградує й відновлюється. Скрипт відмовиться працювати, якщо образи старші за код.

Усі помилки API мають формат `application/problem+json` (RFC 9457) з полем `type` на кшталт `urn:delivery:problem:validation-failed`; помилки валідації містять список полів `errors`.

## Тести і CI

```bash
for m in tracking-api tracking-grpc-client web-problems; do (cd $m && mvn install); done
for m in web-ui tracking-service notification-service dispatch-service shipment-service auth-service; do (cd $m && mvn verify); done
```

Потрібен лише Docker: бази й Toxiproxy піднімають Testcontainers, Kafka в тестах вбудований або замінений заглушкою. Запущений Compose-стек тестам не потрібен. Контрактні тести Pact виконуються в тому самому прогоні: споживачі оновлюють `pacts/`, провайдери їх перевіряють. Звіти покриття JaCoCo: `<модуль>/target/site/jacoco/index.html`.

GitHub Actions (`.github/workflows/build.yml`) запускає те саме на кожен pull request, падає, якщо закомічені pact-файли не збігаються з тестами споживачів, і збирає Docker-образи всіх сервісів.
