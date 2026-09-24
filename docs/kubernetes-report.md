# Звіт: розгортання Shipment і Dispatch у Kubernetes

Розділи стосуються сервісів `shipment-service` і `dispatch-service` та їхніх баз даних. Середовище: Minikube (драйвер Docker, containerd), Kubernetes v1.37.0, namespace `delivery`.

## 1. Що зроблено

| Вимога | Реалізація |
|---|---|
| Java 25 | `java.version=25` у `pom.xml`, базові образи `maven:3.9-eclipse-temurin-25-alpine` та `eclipse-temurin:25-jre-alpine` |
| Actuator | залежність `spring-boot-starter-actuator`, проби `liveness` і `readiness`; readiness враховує стан БД (`readinessState,db`) |
| Deployment, 2 репліки | `k8s/<service>/app.yml`, `replicas: 2`, `RollingUpdate` з `maxUnavailable: 0` |
| Проби | `livenessProbe` → `/actuator/health/liveness`, `readinessProbe` → `/actuator/health/readiness` |
| ClusterIP | `shipment-service`, `dispatch-service`, `shipment-db`, `dispatch-db` |
| ConfigMap / Secret | ConfigMap: ім'я БД та `SPRING_DATASOURCE_URL`; Secret: логін і пароль БД |
| requests / limits | застосунок: 250m / 384Mi → 500m / 512Mi; PostgreSQL: 100m / 256Mi → 500m / 512Mi |
| Постійне сховище | окремий `StatefulSet` на кожну БД з `volumeClaimTemplates` (PVC 1Gi, StorageClass `standard`) |
| Внутрішні DNS-імена | застосунки підключаються до `shipment-db` / `dispatch-db`; сервіси доступні один одному як `shipment-service` і `dispatch-service` |
| Ingress | `k8s/ingress.yml`: `/shipments` → `shipment-service`, `/couriers` → `dispatch-service` |

Порядок запуску, який у Compose забезпечував `depends_on: service_healthy`, у Kubernetes реалізує `initContainer wait-for-db` (`pg_isready` до відповіді БД).

### Секрети

У репозиторії лежать лише шаблони `k8s/*/secret.yml.example` із `CHANGE_ME`. Реальні `secret.yml` додані в `.gitignore`. Значення для перевірки згенеровано випадково (`openssl rand`), у git вони не потрапили.

## 2. Dry-run перед розгортанням

```bash
kubectl apply -f k8s/namespace.yml -f k8s/shipment -f k8s/dispatch -f k8s/ingress.yml --dry-run=client
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/shipment -f k8s/dispatch -f k8s/ingress.yml --dry-run=server
```

Результат: усі 14 ресурсів успішно пройшли `client` і `server` dry-run (`created (dry run)` / `created (server dry run)`). Namespace довелось створити реально, бо серверна перевірка namespaced-ресурсів потребує, щоб він існував. Ingress повторно перевірено після ввімкнення контролера (`minikube addons enable ingress`), щоб його підхопив admission webhook.

## 3. Розгортання

> Виводи в розділах 3–4.3 зняті з першого розгортання (`…-6fb66df488-…`, `…-6d4df848d8-…`). Після знахідки з Lombok (розділ 5) образи перезібрано й Deployment перезапущено, тому в розділах 4.4–4.7 імена подів інші (`…-7fc59c6d79-…`, `…-55f6bf765f-…`). Конфігурація маніфестів не змінювалась.

```bash
minikube image build -t shipment-service:1.0.0 ./shipment-service
minikube image build -t dispatch-service:1.0.0 ./dispatch-service
kubectl apply -f k8s/shipment -f k8s/dispatch
kubectl -n delivery get pods
```

```
NAME                                READY   STATUS    RESTARTS   AGE
dispatch-db-0                       1/1     Running   0          53s
dispatch-service-6fb66df488-jxrrz   1/1     Running   0          53s
dispatch-service-6fb66df488-lr8br   1/1     Running   0          53s
shipment-db-0                       1/1     Running   0          53s
shipment-service-6d4df848d8-xs9hr   1/1     Running   0          53s
shipment-service-6d4df848d8-z9tfg   1/1     Running   0          53s
```

Обидва Deployment мають `2/2` готових реплік, обидва PVC у статусі `Bound`:

```
data-dispatch-db-0   Bound   1Gi   RWO   standard
data-shipment-db-0   Bound   1Gi   RWO   standard
```

Ендпоінти сервісів містять по дві адреси подів застосунку й по одній адресі БД:

```
dispatch-service   10.244.0.10:8080,10.244.0.8:8080
shipment-service   10.244.0.6:8080,10.244.0.7:8080
dispatch-db        10.244.0.11:5432
shipment-db        10.244.0.9:5432
```

## 4. Перевірки

### 4.1. Версія Java

```bash
kubectl -n delivery exec deploy/shipment-service -c shipment-service -- java -version
```

```
openjdk version "25.0.4" 2026-07-21 LTS
```

Так само для `dispatch-service`.

### 4.2. Підключення до PostgreSQL

```bash
kubectl -n delivery exec shipment-db-0 -- sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select current_database(), current_user, left(version(), 30)"'
```

```
shipment_db|shipment_user|PostgreSQL 16.15 on aarch64-un
dispatch_db|dispatch_user|PostgreSQL 16.15 on aarch64-un
```

Кожен сервіс має власну БД і власного користувача.

### 4.3. Проби та готовність реплік

`kubectl describe pod` показує налаштовані проби, `Restart Count: 0` у всіх подів:

```
Liveness:   http-get http://:http/actuator/health/liveness delay=45s timeout=3s period=10s failureThreshold=3
Readiness:  http-get http://:http/actuator/health/readiness delay=20s timeout=3s period=5s failureThreshold=6
```

Прямий запит до кожної з чотирьох реплік:

```
pod/dispatch-service-6fb66df488-jxrrz: liveness={"status":"UP"} readiness={"status":"UP"}
pod/dispatch-service-6fb66df488-lr8br: liveness={"status":"UP"} readiness={"status":"UP"}
pod/shipment-service-6d4df848d8-xs9hr: liveness={"status":"UP"} readiness={"status":"UP"}
pod/shipment-service-6d4df848d8-z9tfg: liveness={"status":"UP"} readiness={"status":"UP"}
```

### 4.4. Робота API та запис у БД

Запити виконано через `kubectl port-forward svc/...` до Service (тобто до обох реплік):

| Запит | Результат |
|---|---|
| `POST /shipments` | 200, створено посилку `shipmentId=1` |
| `GET /shipments/1` | 200 |
| `GET /shipments/999` | 404 |
| `POST /couriers` | 200, створено кур'єра `userId=10` |
| `GET /couriers/10` | 200 |

Запис підтверджено прямо в базах:

```
shipment_db: 1|Ivan Petrenko|WAITING_FOR_COURIER
dispatch_db: 10|Kyiv|t
```

### 4.5. Міжсервісна взаємодія через DNS

Запити виконано зсередини подів:

| Звідки → куди | Результат |
|---|---|
| dispatch → `http://shipment-service:8080/actuator/health/readiness` | `{"status":"UP"}` |
| dispatch → `http://shipment-service.delivery.svc.cluster.local:8080/...` | `{"status":"UP"}` |
| shipment → `http://dispatch-service:8080/actuator/health/readiness` | `{"status":"UP"}` |
| `nslookup shipment-service.delivery.svc.cluster.local` | `10.108.213.133` (ClusterIP сервісу) |

Це перевірка мережевої досяжності за DNS. У коді `dispatch-service` і `shipment-service` поки немає HTTP-викликів один до одного, тому бізнес-взаємодію на рівні коду цією перевіркою не покрито.

### 4.6. Самовідновлення та збереження даних

Видалення одного пода застосунку (`kubectl delete pod shipment-service-7fc59c6d79-7t9mq`): Deployment одразу створив новий под, за ~25 секунд знову `2/2` реплік у стані `Ready`.

Видалення пода бази (`kubectl delete pod shipment-db-0`): StatefulSet підняв його заново, PVC `data-shipment-db-0` лишився `Bound`, дані збереглись:

```
1|Ivan Petrenko
```

Після цього `dispatch` успішно прочитав `GET /shipments/1` з `shipment-service`.

### 4.7. Ingress

Ввімкнено `minikube addons enable ingress`, застосовано `k8s/ingress.yml`, ресурс отримав адресу `192.168.49.2`. Маршрути перевірено через `kubectl -n ingress-nginx port-forward svc/ingress-nginx-controller 18080:80`:

| Запит | Код |
|---|---|
| `GET /shipments/1` | 200 |
| `GET /shipments` | 200 |
| `POST /shipments` | 200 |
| `GET /couriers/10` | 200 |
| `GET /couriers` | 200 |
| `GET /shipments/999`, `GET /couriers/999` | 404 (від застосунку) |
| `GET /actuator/health` | 404 (Actuator назовні не маршрутизується) |
| `GET /unknown` | 404 (немає маршруту) |

## 5. Знахідки під час виконання

1. **Lombok на JDK 25.** Починаючи з JDK 23, `javac` не запускає annotation processors неявно. Після переходу на Java 25 образ збирався без помилок, але в entity не було згенерованих геттерів і сеттерів, і `POST` повертав 500 (`deliveryAddress` = `null`). Виправлено явним `annotationProcessorPaths` для Lombok у `maven-compiler-plugin` в обох `pom.xml`. Після цього в класі з'явились акцесори, а API повернув 200. Перевірено також запуск через Docker Compose.
2. **Мережева ізоляція баз.** У Kubernetes діє плоска мережа: з поду `dispatch-service` порт `shipment-db:5432` відкритий (`nc` → `open`). Розмежування доступу до баз (NetworkPolicy) не налаштовувалось, воно не було у вимогах.
3. **Ingress через port-forward.** Маршрути перевірено через `port-forward` до контролера, а не через `minikube tunnel`, який на macOS потребує привілейованого доступу.
4. **`ingress.yml`.** У репозиторії такого файлу ще не було, тому створено `k8s/ingress.yml` з маршрутами Shipment і Dispatch. Маршрути інших сервісів команда може додати в цей самий файл.
