# Docker Compose для BionicPRO

## 📚 Общее описание проекта

Проект **BionicPRO** – комплексная система для управления бионическими протезами. Архитектура построена на паттерне **Backend‑for‑Frontend (BFF)** и состоит из нескольких микросервисов, баз данных, систем потоковой обработки и CDN. Ниже представлена полная карта компонентов, их назначение и взаимодействие.

---

## 🏗️ Архитектура системы

Система состоит из следующих ключевых компонентов:

- **Keycloak 21.1** – OpenID Connect провайдер с поддержкой PKCE, MFA и Identity Brokering (Yandex ID).
- **BFF‑сервис `bionicpro-auth`** – Spring Boot приложение (порт 8000), управляет аутентификацией, сессиями (Redis) и выдачей HTTP‑Only куки.
- **Redis** – хранение пользовательских сессий.
- **OpenLDAP 1.5.0** – федерация пользователей по регионам.
- **Yandex ID** – внешний Identity Provider.
- **Сервис отчётов `bionicpro-reports`** – Spring Boot API (порт 8081) для генерации и выдачи отчётов из ClickHouse.
- **Apache Airflow** – оркестрация ETL‑pipeline, ежедневно в 02:00 UTC. **Находится в отдельной папке `/airflow`**.
- **ClickHouse** – OLAP‑хранилище для агрегированных данных.
- **MinIO** – S3‑совместимое объектное хранилище, кеширует готовые отчёты.
- **Nginx (CDN)** – reverse‑proxy с кешированием, отдаёт отчёты из MinIO через presigned URL.
- **Kafka + Zookeeper + Debezium** – CDC‑модуль, захватывает изменения из PostgreSQL‑CRM и PostgreSQL‑Sensors и передаёт их в ClickHouse через KafkaEngine.
- **PostgreSQL CRM** – хранит данные о клиентах.
- **PostgreSQL Sensors** – хранит телеметрические данные сенсоров.
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
│   ├── bionicpro-auth/           # BFF‑сервис
│   ├── bionicpro-reports/       # Сервис отчётов
│   ├── cdc/                     # Kafka, Zookeeper, Debezium
│   ├── crm-db/                  # PostgreSQL CRM
│   ├── sensors-db/              # PostgreSQL Sensors
│   ├── olap-db/                 # ClickHouse
│   ├── frontend/                 # React‑приложение
│   ├── nginx/                   # CDN конфигурация
│   ├── keycloak/                # Realm‑export.json
│   ├── ldap/                    # config.ldif
│   └── redis/                   # Redis данные
│
└── airflow/                      # Apache Airflow (оркестрация ETL)
    ├── docker-compose.yaml
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

| Сервис | URL | Логин / Пароль |
|--------|-----|----------------|
| **BFF (bionicpro-auth)** | `http://localhost:8000` | — |
| Keycloak | `http://localhost:8088` | `admin` / `admin` |
| Realm | `reports-realm` | — |
| Frontend | `http://localhost:3000` | — |
| Redis | `localhost:6379` | — |
| OpenLDAP | `ldap://localhost:389` | `cn=admin,dc=example,dc=com` / `admin` |
| CRM DB | `localhost:5444` (PostgreSQL) | `crm_user` / `crm_password` |
| Sensors DB | `localhost:5436` (PostgreSQL) | `sensors_user` / `sensors_password` |
| ClickHouse | `http://localhost:8123` | — (HTTP) |
| MinIO Console | `http://localhost:9001` | `minio_user` / `minio_password` |
| Nginx CDN | `http://localhost:8082` | — |
| **Airflow UI** | `http://localhost:8080` | `admin` / `admin` |

> **Примечание**: Airflow запускается из отдельной папки `/airflow`. Для доступа к Airflow UI необходимо сначала запустить `cd airflow && docker-compose up -d`.

---

## 🔐 Конфигурация Keycloak

- **Realm**: `reports-realm` (импортируется из `keycloak/realm-export.json`).
- **PKCE**: включён, метод `S256`.
- **MFA/OTP**: TOTP, SHA256, 6 цифр, 30 сек.
- **LDAP Federation**: `user-ldap` → `ldap://openldap:389`, bind DN `cn=admin,dc=example,dc=com`.
- **Identity Brokering**: Yandex ID (alias `yandex`).

---

## ⚙️ Конфигурация BFF‑сервиса (`bionicpro-auth`)

```yaml
bionicpro-auth:
  environment:
    SPRING_PROFILES_ACTIVE: dev
    REDIS_HOST: redis
    REDIS_PORT: 6379
    KEYCLOAK_SERVER_URL: http://keycloak:8088
    KEYCLOAK_REALM: reports-realm
    KEYCLOAK_CLIENT_ID: bionicpro-auth
    KEYCLOAK_REDIRECT_URI: http://localhost:8000/api/auth/callback
```

- **Порт**: 8000
- **Сессии**: Redis (`bionicpro:session` namespace)
- **Куки**: Secure, HttpOnly, SameSite=Lax
- **OAuth2 клиент**: PKCE (S256)

---

## 📊 Сервис отчётов (`bionicpro-reports`)

**Назначение** – предоставление агрегированных данных о работе протезов через REST API.

### Основные функции
- Генерация отчётов из OLAP‑хранилища ClickHouse.
- Кеширование готовых отчётов в MinIO.
- Защищённый доступ по JWT‑токенам (Keycloak).
- Ограничение доступа: пользователь видит только свои отчёты.

### Переменные окружения
```yaml
bionicpro-reports:
  environment:
    SPRING_PROFILES_ACTIVE: prod
    KEYCLOAK_SERVER_URL: http://keycloak:8088
    KEYCLOAK_REALM: reports-realm
    KEYCLOAK_CLIENT_ID: bionicpro-reports
    MINIO_ENDPOINT: http://minio:9000
    MINIO_ACCESS_KEY: minioadmin
    MINIO_SECRET_KEY: minioadmin
    MINIO_BUCKET_NAME: reports
    CDN_BASE_URL: http://nginx-cdn:80
```

---

## 🛠️ ETL и Airflow

**Назначение** – извлечение, трансформация и загрузка данных из CRM и Sensors в ClickHouse.

### Расположение
- Apache Airflow находится в **отдельной папке `/airflow`** на том же уровне, что и `/app`.
- Запускается отдельно: `cd airflow && docker-compose up -d`

### Взаимодействие с компонентами `/app`
Airflow взаимодействует со следующими сервисами основного приложения:
- **Читает из PostgreSQL**: `/app/sensors-db` (Sensors DB) и `/app/crm-db` (CRM DB)
- **Пишет в ClickHouse**: `/app/olap-db`

### Конфигурация
- **Расписание**: ежедневно в 02:00 UTC.
- **DAG**: `bionicpro_etl_dag` (см. `airflow/dags/bionicpro_etl_dag.py`).
- **Этапы**: извлечение данных → трансформация → загрузка в ClickHouse‑витрину `user_reports`.

---

## 📦 Хранилища и CDN

- **ClickHouse** – OLAP‑база, партиционирование по `user_id` и `report_date`.
- **MinIO** – S3‑совместимое объектное хранилище, используется для кеша отчётов.
- **Nginx CDN** – reverse‑proxy с кешированием, отдаёт отчёты из MinIO через presigned URL (`app/nginx/conf.d/reports-cdn.conf`).

---

## 🔄 CDC и потоковая обработка

**Назначение** – обеспечить независимость выгрузок из CRM от транзакционных операций.

### Компоненты
- **Zookeeper** – координация Kafka.
- **Kafka** – брокер сообщений.
- **Debezium** (через Kafka Connect) – захват изменений из PostgreSQL‑CRM и PostgreSQL‑Sensors.
- **ClickHouse KafkaEngine** – потребление изменений и обновление материализованных представлений (`user_reports_cdc`).

### Запуск CDC
```bash
cd app/cdc
docker-compose up -d   # поднимает Zookeeper, Kafka, Kafka Connect, Debezium
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

### CRM DB (PostgreSQL)
- Хост: `localhost:5444`
- Пользователь: `crm_user`
- Пароль: `crm_password`
- Скрипт инициализации: `app/crm-db/init.sql`

### Sensors DB (PostgreSQL)
- Хост: `localhost:5436`
- Пользователь: `sensors_user`
- Пароль: `sensors_password`
- Скрипт инициализации: `app/sensors-db/init.sql`

### ClickHouse OLAP
- Хост: `localhost:8123`
- Скрипт инициализации: `app/olap-db/init.sql`

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
curl -s http://localhost:8088/health/ready
curl -s http://localhost:8080/realms/reports-realm | jq .realm
```
4. **BFF‑сервис**
```bash
curl -s http://localhost:8000/actuator/health
curl -s http://localhost:8000/api/auth/status
```
5. **Сервис отчётов**
```bash
curl -s http://localhost:8081/api/v1/reports   # требует JWT‑токен
```
6. **Airflow UI** – `http://localhost:8080` (запускается из папки `/airflow`)
7. **CDC** – проверка топиков:
```bash
docker exec -it app-kafka-1 kafka-topics --list --bootstrap-server localhost:9092
```
8. **Frontend** – откройте `http://localhost:3000` и выполните вход через BFF.

---

## 🔧 Устранение проблем

### Keycloak не стартует
```bash
cd app
docker-compose logs keycloak
# При необходимости увеличить таймаут БД
docker-compose up -d keycloak_db
sleep 10
docker-compose up -d keycloak
```

### BFF‑сервис не подключается к Redis
```bash
cd app
docker-compose logs redis
docker network inspect app_default
```

### LDAP Federation не работает
1. Проверить соединение из Keycloak:
```bash
docker exec -it keycloak bash
nc -zv openldap 389
```
2. В админ‑консоли Keycloak проверить `User Federation → user-ldap → Test connection`.

### CDC не захватывает изменения
```bash
curl http://localhost:8083/connectors/   # список коннекторов
curl http://localhost:8083/connectors/customers-connector/status
```

---

## 📂 Полезные ссылки
- Файл realm‑export: `app/keycloak/realm-export.json`
- Конфигурация MinIO: `app/bionicpro-reports/src/main/resources/application.yml`
- Конфигурация Nginx CDN: `app/nginx/conf.d/reports-cdn.conf`
- Airflow DAG: `airflow/dags/bionicpro_etl_dag.py`
- CDC‑коннекторы: `app/cdc/debezium/connector-customers.json`, `app/cdc/debezium/connector-sensors.json`

---

## 🎉 Готово!

Все сервисы запущены, инфраструктура готова к работе. При необходимости обратитесь к разделу **Устранение проблем** или к документации в репозитории.
