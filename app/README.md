# Docker Compose для BionicPRO

## 📚 Общее описание проекта

Проект **BionicPRO** – комплексная система для управления бионическими протезами. Архитектура построена на паттерне **Backend‑for‑Frontend (BFF)** и состоит из нескольких микросервисов, баз данных, систем потоковой обработки и CDN. Ниже представлена полная карта компонентов, их назначение и взаимодействие.

---

## 🏗️ Архитектура системы

Система состоит из следующих ключевых компонентов:

- **Keycloak 26.0.8** – OpenID Connect провайдер с поддержкой PKCE, MFA и Identity Brokering (Yandex ID).
- **BFF‑сервис `bionicpro-auth`** – Spring Boot приложение (порт 8000), управляет аутентификацией, сессиями (Redis) и выдачей HTTP‑Only куки.
- **Redis 7** – хранение пользовательских сессий (данные в `.data/data/`).
- **OpenLDAP 1.5.0** – федерация пользователей (домен `bionicpro.local`).
- **Yandex ID** – внешний Identity Provider.
- **Сервис отчётов `bionicpro-reports`** – Spring Boot API (порт 8081) для генерации и выдачи отчётов из ClickHouse.
- **Apache Airflow 2.8.1** – оркестрация ETL‑pipeline, ежедневно в 02:00 UTC. **Находится в отдельной папке `/airflow`**.
- **ClickHouse 23.8** – OLAP‑хранилище для агрегированных данных.
- **MinIO** – S3‑совместимое объектное хранилище, кеширует готовые отчёты.
- **Nginx (CDN)** – reverse‑proxy с кешированием, отдаёт отчёты из MinIO через presigned URL.
- **Kafka + Zookeeper + Debezium** – CDC‑модуль, захватывает изменения из PostgreSQL‑CRM и PostgreSQL‑Sensors и передаёт их в ClickHouse через KafkaEngine.
- **PostgreSQL 14 CRM** – хранит данные о клиентах.
- **PostgreSQL 14 Sensors** – хранит телеметрические данные сенсоров.
- **Frontend (React)** – пользовательский интерфейс для управления протезом и получения отчётов.

```mermaid
flowchart LR
    subgraph "Клиент"
        UI[React UI]
    end
    subgraph "BFF"
        BFF[bionicpro-auth]
    end
    subgraph "Auth"
        KC[Keycloak]
        LDAP[OpenLDAP]
        YID[Yandex ID]
    end
    subgraph "Сервисы"
        REP[bionicpro-reports]
        AIR[Airflow]
        CDC[Kafka + Debezium]
    end
    subgraph "Хранилища"
        CH[ClickHouse]
        MINIO[MinIO]
        CDN[Nginx CDN]
        CRM[PostgreSQL CRM]
        SENS[PostgreSQL Sensors]
        REDIS[Redis]
    end
    UI -->|auth| BFF
    BFF -->|token| KC
    KC -->|LDAP| LDAP
    KC -->|IdP| YID
    BFF -->|session| REDIS
    BFF -->|reports API| REP
    REP -->|query| CH
    REP -->|cache| MINIO
    MINIO -->|presigned URL| CDN
    UI -->|fetch report| CDN
    AIR -->|ETL| CH
    CDC -->|CDC| CH
    CRM -->|changes| CDC
    SENS -->|changes| CDC
```

---

## 📁 Структура проекта

Проект имеет монорепозитарную структуру с двумя основными папками на корневом уровне:

```
bionicpro-sprint9/
├── app/                          # Основное приложение
│   ├── docker-compose.yaml
│   ├── .env.example              # Шаблон переменных окружения
│   ├── bionicpro-auth/           # BFF‑сервис
│   ├── bionicpro-reports/        # Сервис отчётов
│   ├── cdc/                      # Kafka, Zookeeper, Debezium
│   ├── crm-db/                   # PostgreSQL CRM
│   ├── sensors-db/               # PostgreSQL Sensors
│   ├── olap-db/                  # ClickHouse
│   ├── frontend/                 # React‑приложение
│   ├── nginx/                    # CDN конфигурация
│   ├── keycloak/                 # Realm‑export.json
│   ├── ldap/                     # config.ldif
│   └── .data/                    # Данные всех сервисов
│       ├── data/                 # Redis данные
│       ├── clickhouse-data/      # ClickHouse данные
│       ├── postgres-crm-data/    # CRM данные
│       ├── postgres-sensors-data/# Sensors данные
│       └── postgres-keycloak-data/ # Keycloak данные
│
└── airflow/                      # Apache Airflow (оркестрация ETL)
    ├── docker-compose.yaml
    ├── .env.example
    ├── dags/
    │   └── bionicpro_etl_dag.py
    ├── requirements.txt
    └── ...
```

---

## 🚀 Как запустить

### Требования
- Docker
- Docker Compose (v2+)

### Порядок запуска основного приложения (app)
```bash
# 1. Сборка и запуск всех сервисов
cd app
docker-compose up -d

# 2. Ожидание инициализации (Keycloak импортирует realm, базы поднимаются)
echo "Ожидание инициализации сервисов..."
sleep 30

# 3. Проверка статуса контейнеров
docker-compose ps
```

### Запуск Apache Airflow (отдельный)
```bash
cd airflow
docker-compose up -d
```
> ⚠️ **Важно**: Airflow находится в отдельной папке `/airflow` и запускается отдельно от основного приложения.

> ⚠️ **Важно**: дождитесь, пока все контейнеры перейдут в статус `Up`. Keycloak импортирует `realm-export.json` с настройками PKCE, MFA и LDAP.

---

## 🌐 URL сервисов и учётные данные

| Сервис | URL | Логин / Пароль | Примечание |
|--------|-----|----------------|------------|
| **BFF (bionicpro-auth)** | `http://localhost:8000` | — | Health: `/actuator/health` |
| Keycloak Admin | `http://localhost:8088` | `admin` / `admin` | Внутренний порт: 8080 |
| Keycloak Health | `http://localhost:8088/health/ready` | — | |
| Realm | `reports-realm` | — | |
| Frontend | `http://localhost:3000` | — | React UI |
| Redis | `localhost:6379` | — | Пароль из `.env` |
| OpenLDAP | `ldap://localhost:389` | `cn=admin,dc=bionicpro,dc=local` / из `.env` | Домен: `bionicpro.local` |
| CRM DB | `localhost:5444` (PostgreSQL 14) | `crm_user` / из `.env` | |
| Sensors DB | `localhost:5436` (PostgreSQL 14) | `sensors_user` / из `.env` | |
| ClickHouse | `http://localhost:8123` | `default` / из `.env` | Native порт: 9005 |
| MinIO Console | `http://localhost:9001` | из `.env` | S3 порт: 9000 |
| Nginx CDN | `http://localhost:8082` | — | Кэш отчётов |
| **Airflow UI** | `http://localhost:8080` | `admin` / из `.env` | Запускается отдельно |
| Kafka Connect | `http://localhost:8083` | — | CDC коннекторы |
| Zookeeper | `localhost:2181` | — | |
| Kafka | `localhost:9092` | — | |

---

## 🔐 Конфигурация Keycloak

- **Версия**: 26.0.8
- **Realm**: `reports-realm` (импортируется из `keycloak/realm-export.json`).
- **PKCE**: включён, метод `S256`.
- **MFA/OTP**: TOTP, SHA256, 6 цифр, 30 сек (CONFIGURE_TOTP как required action).
- **LDAP Federation**: `user-ldap` → `ldap://openldap:389`, bind DN `cn=admin,dc=bionicpro,dc=local`, домен `bionicpro.local`.
- **Identity Brokering**: Yandex ID (alias `yandex`).
- **Клиенты**:
  - `bionicpro-auth` — конфиденциальный клиент для BFF (секрет из realm-export).
  - `reports-frontend` — публичный клиент для React UI.
  - `reports-api` — bearer-only для сервиса отчётов.
- **Пользователи**: предопределённые пользователи `user1`, `user2`, `admin1`, `prothetic1-3` с ролями `user`, `administrator`, `prothetic_user`.
- **Health endpoint**: `/health/ready` (порт 8080 внутри контейнера, наружу — 8088).

---

## ⚙️ Конфигурация BFF‑сервиса (`bionicpro-auth`)

```yaml
bionicpro-auth:
  environment:
    SPRING_PROFILES_ACTIVE: dev
    REDIS_HOST: redis
    REDIS_PORT: 6379
    REDIS_PASSWORD: ${REDIS_PASSWORD}
    KEYCLOAK_SERVER_URL: http://keycloak:8080
    KEYCLOAK_PUBLIC_URL: http://localhost:8088
    KEYCLOAK_REALM: reports-realm
    KEYCLOAK_CLIENT_ID: bionicpro-auth
    KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET}
    KEYCLOAK_REDIRECT_URI: http://localhost:8000/api/auth/callback
    OAUTH2_AES_KEY: ${OAUTH2_AES_KEY}
    OAUTH2_SALT: ${OAUTH2_SALT}
    REPORTS_SERVICE_URL: http://bionicpro-reports:8081
```

- **Порт**: 8000
- **Сессии**: Redis (`bionicpro:session` namespace), пароль из `.env`
- **Куки**: Secure, HttpOnly, SameSite=Lax
- **OAuth2 клиент**: PKCE (S256)
- **Таймауты**: ≤2000ms для внешних интеграций (LDAP, API)
- **Health endpoints**: `/actuator/health`, `/actuator/info`

---

## 📊 Сервис отчётов (`bionicpro-reports`)

**Назначение** – предоставление агрегированных данных о работе протезов через REST API.

### Основные функции
- Генерация отчётов из OLAP‑хранилища ClickHouse.
- Кеширование готовых отчётов в MinIO.
- Защищённый доступ по JWT‑токенам (Keycloak, OAuth2 Resource Server).
- Ограничение доступа: пользователь видит только свои отчёты.
- Health checks: liveness/readiness через Spring Actuator.
- OpenAPI документация: `/swagger-ui.html`, `/v3/api-docs`.

### Переменные окружения
```yaml
bionicpro-reports:
  environment:
    SPRING_PROFILES_ACTIVE: prod
    CLICKHOUSE_HOST: ${CLICKHOUSE_HOST}
    CLICKHOUSE_PORT: ${CLICKHOUSE_PORT}
    CLICKHOUSE_USER: ${CLICKHOUSE_USER}
    CLICKHOUSE_PASSWORD: ${CLICKHOUSE_PASSWORD}
    KEYCLOAK_ISSUER_URI: http://localhost:8088/realms/reports-realm
    KEYCLOAK_JWK_SET_URI: http://keycloak:8080/realms/reports-realm/protocol/openid-connect/certs
    MINIO_ENDPOINT: http://minio:9000
    MINIO_ROOT_USER: ${MINIO_ROOT_USER}
    MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    MINIO_BUCKET_NAME: reports
    CDN_BASE_URL: http://nginx-cdn:80
```

### Health endpoints
- `/actuator/health/liveness` — liveness probe
- `/actuator/health/readiness` — readiness probe
- `/actuator/health` — общий health

---

## 🛠️ ETL и Airflow

**Назначение** – извлечение, трансформация и загрузка данных из CRM и Sensors в ClickHouse.

### Расположение
- Apache Airflow находится в **отдельной папке `/airflow`** на том же уровне, что и `/app`.
- Запускается отдельно: `cd airflow && docker-compose up -d`
- Версия Airflow: 2.8.1 (Python 3.11)

### Взаимодействие с компонентами `/app`
Airflow взаимодействует со следующими сервисами основного приложения через общую сеть `app-network`:
- **Читает из PostgreSQL**: `/app/sensors-db` (Sensors DB) и `/app/crm-db` (CRM DB)
- **Пишет в ClickHouse**: `/app/olap-db`

### Конфигурация
- **Расписание**: ежедневно в 02:00 UTC (`0 2 * * *`).
- **DAG**: `bionicpro_etl_pipeline` (см. `airflow/dags/bionicpro_etl_dag.py`).
- **Этапы**:
  1. `extract_sensors_data` — извлечение данных ЭМГ-сенсоров из PostgreSQL
  2. `extract_crm_data` — извлечение данных о клиентах из CRM
  3. `transform_and_merge_data` — трансформация и объединение данных
  4. `load_to_olap` — загрузка в ClickHouse‑витрину `user_reports`
- **Таблица ClickHouse**: `user_reports` с агрегированными данными по пользователям.

### Переменные окружения Airflow
```bash
SENSORS_DB_HOST=sensors-db
SENSORS_DB_PASSWORD=<password>
CRM_DB_HOST=crm_db
CRM_DB_PASSWORD=<password>
OLAP_DB_HOST=olap_db
AIRFLOW_ADMIN_USER=admin
AIRFLOW_ADMIN_PASSWORD=<password>
```

---

## 📦 Хранилища и CDN

- **ClickHouse 23.8** – OLAP‑база, партиционирование по `user_id` и `report_date`. Таблица `user_reports` для агрегированных данных.
- **MinIO** – S3‑совместимое объектное хранилище, используется для кеша отчётов (бакет `reports`).
- **Nginx CDN** – reverse‑proxy с кешированием, отдаёт отчёты из MinIO. Кэш хранится 24 часа (`/var/cache/nginx/reports`). Presigned URL не используются — отдача напрямую через прокси.
  - Конфигурация: `app/nginx/conf.d/reports-cdn.conf`
  - Health endpoint: `http://localhost:8082/health`
  - Cache purge: `http://localhost:8082/purge/<path>` (только из внутренней сети)

---

## 🔄 CDC и потоковая обработка

**Назначение** – обеспечить независимость выгрузок из CRM от транзакционных операций.

### Компоненты
- **Zookeeper** – координация Kafka (порт 2181).
- **Kafka** – брокер сообщений (порт 9092).
- **Debezium 2.4** (через Kafka Connect) – захват изменений из PostgreSQL‑CRM и PostgreSQL‑Sensors.
- **Kafka Connect** – REST API для управления коннекторами (порт 8083).
- **ClickHouse KafkaEngine** – потребление изменений и обновление материализованных представлений (`user_reports_cdc`).

### Запуск CDC
CDC запускается автоматически вместе с основным приложением через `include` в `docker-compose.yaml`:
```bash
cd app
docker-compose up -d   # поднимает все сервисы, включая CDC
```

### Коннекторы
- `customers-connector` — CDC для таблицы `customers` из CRM DB
- `sensors-connector` — CDC для таблицы `emg_sensor_data` из Sensors DB

Конфигурация коннекторов:
- `app/cdc/debezium/connector-customers.json`
- `app/cdc/debezium/connector-sensors.json`

### Проверка CDC
```bash
# Список топиков
docker exec -it app-kafka-1 kafka-topics --list --bootstrap-server localhost:9092

# Статус коннекторов
curl http://localhost:8083/connectors/
curl http://localhost:8083/connectors/customers-connector/status
curl http://localhost:8083/connectors/sensors-connector/status
```

---

## 🌐 Frontend (React)

**Назначение** – пользовательский интерфейс для управления протезом и получения отчётов.

### Основные функции
- Авторизация через BFF‑сервис (HTTP‑Only куки).
- Кнопка «Получить отчёт», вызывающая API `/reports`.
- Отображение данных отчёта в удобном виде.
- Обработка ошибок и состояний загрузки.

---

## 🗄️ Базы данных

### CRM DB (PostgreSQL 14)
- Хост: `localhost:5444`
- БД: `crm_db`
- Пользователь: `crm_user`
- Пароль: из `.env`
- Скрипт инициализации: `app/crm-db/init.sql`
- Таблица: `customers`

### Sensors DB (PostgreSQL 14)
- Хост: `localhost:5436`
- БД: `sensors-data`
- Пользователь: `sensors_user`
- Пароль: из `.env`
- Скрипт инициализации: `app/sensors-db/init.sql`
- Таблица: `emg_sensor_data`
- Индексы: по `user_id`, `prosthesis_type`, `signal_time`, составной `(user_id, signal_time)`

### ClickHouse OLAP (23.8)
- Хост: `localhost:8123` (HTTP), `localhost:9005` (native)
- Пользователь: `default`
- Пароль: из `.env`
- Скрипт инициализации: `app/olap-db/init.sql`
- Таблица: `emg_sensor_data` (сырые данные), `user_reports` (агрегированные данные из Airflow)
- Конфигурация пользователей: `app/olap-db/users.xml`

### Keycloak DB (PostgreSQL 14)
- Хост: `localhost:5433`
- БД: `keycloak_db`
- Пользователь: `keycloak_user`
- Пароль: из `.env`
- Данные: `.data/postgres-keycloak-data/`

### Airflow DB (PostgreSQL 16)
- Хост: `localhost` (внутри сети airflow)
- БД: `airflow`
- Пользователь: `airflow`
- Пароль: из `.env`
- Запускается внутри `airflow/docker-compose.yaml`

---

## ✅ Проверка работоспособности

1. **Контейнеры (app)**
```bash
cd app && docker-compose ps
```

2. **Контейнеры (airflow)**
```bash
cd airflow && docker-compose ps
```

3. **Keycloak**
```bash
# Health check
curl -s http://localhost:8088/health/ready

# Проверка realm
curl -s http://localhost:8088/realms/reports-realm | jq .realm
```

4. **BFF‑сервис**
```bash
# Health check
curl -s http://localhost:8000/actuator/health

# Auth status
curl -s http://localhost:8000/api/auth/status
```

5. **Сервис отчётов**
```bash
# Health check
curl -s http://localhost:8081/actuator/health/liveness
curl -s http://localhost:8081/actuator/health/readiness

# API (требуется JWT‑токен)
curl -s http://localhost:8081/api/v1/reports -H "Authorization: Bearer <token>"

# Swagger UI
curl -s http://localhost:8081/swagger-ui.html
```

6. **Airflow UI** – `http://localhost:8080` (запускается из папки `/airflow`)

7. **CDC** – проверка топиков и коннекторов:
```bash
# Список топиков
docker exec -it app-kafka-1 kafka-topics --list --bootstrap-server localhost:9092

# Список коннекторов
curl http://localhost:8083/connectors/

# Статус коннекторов
curl http://localhost:8083/connectors/customers-connector/status
curl http://localhost:8083/connectors/sensors-connector/status
```

8. **Frontend** – откройте `http://localhost:3000` и выполните вход через BFF.

9. **Nginx CDN**
```bash
# Health check
curl -s http://localhost:8082/health

# Проверка кэша (требуется presigned URL из MinIO)
curl -I http://localhost:8082/reports/<path>
# Заголовок X-Cache-Status покажет HIT/MISS
```

10. **MinIO Console** – `http://localhost:9001`

---

## 🔧 Устранение проблем

### Keycloak не стартует
```bash
cd app
docker-compose logs keycloak

# Проверка БД Keycloak
docker-compose logs keycloak_db

# При необходимости увеличить время ожидания БД
docker-compose up -d keycloak_db
sleep 10
docker-compose up -d keycloak
```

### BFF‑сервис не подключается к Redis
```bash
cd app
docker-compose logs redis
docker-compose logs bionicpro-auth
docker network inspect app_default

# Проверка подключения к Redis
docker exec -it app-redis-1 redis-cli -a ${REDIS_PASSWORD} ping
```

### LDAP Federation не работает
1. Проверить соединение из Keycloak:
```bash
docker exec -it keycloak bash
nc -zv openldap 389
```
2. В админ‑консоли Keycloak проверить `User Federation → user-ldap → Test connection`.
3. Проверить конфигурацию LDAP:
```bash
# Домен: bionicpro.local (не example.com!)
# Bind DN: cn=admin,dc=bionicpro,dc=local
```

### CDC не захватывает изменения
```bash
# Проверка Kafka Connect
curl http://localhost:8083/connectors/

# Проверка статуса коннекторов
curl http://localhost:8083/connectors/customers-connector/status
curl http://localhost:8083/connectors/sensors-connector/status

# Перезапуск коннектора
curl -X PUT http://localhost:8083/connectors/customers-connector/restart

# Проверка логов Debezium
docker-compose logs kafka-connect
```

### Airflow DAG не выполняется
```bash
cd airflow
docker-compose logs airflow-scheduler
docker-compose logs airflow-init

# Проверка переменных окружения
docker-compose exec airflow-webserver env | grep -E 'SENSORS_DB|CRM_DB|OLAP_DB'
```

### Nginx CDN не кэширует
```bash
# Проверка логов nginx
docker-compose logs nginx-cdn

# Проверка кэша
docker exec -it app-nginx-cdn-1 ls -la /var/cache/nginx/reports

# Тест кэша (первый запрос MISS, второй HIT)
curl -I http://localhost:8082/reports/<path>
```

---

## 📂 Полезные ссылки

### Конфигурация
- **Realm Keycloak**: `app/keycloak/realm-export.json`
- **BFF (bionicpro-auth)**: `app/bionicpro-auth/src/main/resources/application.yml`
- **Сервис отчётов**: `app/bionicpro-reports/src/main/resources/application.yml`
- **Nginx CDN**: `app/nginx/nginx.conf`, `app/nginx/conf.d/reports-cdn.conf`
- **ClickHouse пользователи**: `app/olap-db/users.xml`
- **LDAP конфигурация**: `app/ldap/config.ldif`

### CDC / Debezium
- **Коннектор customers**: `app/cdc/debezium/connector-customers.json`
- **Коннектор sensors**: `app/cdc/debezium/connector-sensors.json`

### Airflow
- **DAG**: `airflow/dags/bionicpro_etl_dag.py`
- **Требования**: `airflow/requirements.txt`
- **Тесты**: `airflow/tests/`

### Инициализация БД
- **CRM DB**: `app/crm-db/init.sql`, `app/crm-db/crm.csv`
- **Sensors DB**: `app/sensors-db/init.sql`, `app/sensors-db/sensors.csv`
- **ClickHouse**: `app/olap-db/init.sql`, `app/olap-db/olap.csv`

### Переменные окружения
- **App**: `app/.env.example`
- **Airflow**: `airflow/.env.example`

---

## 🎉 Готово!

Все сервисы запущены, инфраструктура готова к работе.

### Быстрый старт

```bash
# 1. Настройка переменных окружения
cd app
cp .env.example .env
# Отредактируйте .env, установив безопасные пароли

# 2. Запуск основного приложения
docker-compose up -d

# 3. Ожидание инициализации (Keycloak импортирует realm, базы поднимаются)
echo "Ожидание инициализации сервисов..."
sleep 60

# 4. Проверка статуса контейнеров
docker-compose ps

# 5. Запуск Airflow (отдельно)
cd ../airflow
cp .env.example .env
# Отредактируйте .env, установив пароли
docker-compose up -d
```

### Тестовые учётные данные

| Пользователь | Роль | Пароль |
|--------------|------|--------|
| `user1` | user | `password123` |
| `admin1` | administrator | `admin123` |
| `prothetic1` | prothetic_user | `prothetic123` |

При необходимости обратитесь к разделу **Устранение проблем** или к документации в репозитории.
