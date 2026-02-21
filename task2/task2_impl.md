# Документация по настройке сетевого взаимодействия Airflow и BionicPRO App

> Инструкции по конфигурации подключения между автономным модулем Airflow и основным приложением BionicPRO.

---

## 1. Обзор

### Архитектура системы

Система BionicPRO состоит из двух основных компонентов:

| Компонент | Расположение | Назначение |
|-----------|----------------|--------------|
| **App** | `/app/` | Основное приложение: auth, reports, frontend, базы данных |
| **Airflow** | `/airflow/` | ETL-канал для переноса данных в OLAP-витрину |

### Схема архитектуры

```mermaid
graph TB
    subgraph App - app-network
        SDB[sensors-db:5432]
        CRM[crm_db:5432]
        OLAP[olap_db:9000]
        AUTH[bionicpro-auth:8000]
        REP[bionicpro-reports:8081]
        FE[frontend:3000]
        KC[keycloak:8080]
        RD[redis:6379]
    end
    
    subgraph Airflow - airflow-network
        AFWS[airflow-webserver:8080]
        AFSCH[airflow-scheduler]
        AFTR[airflow-triggerer]
        AFPG[postgres:5432]
    end
    
    subgraph External Network
        APPNET[app-network]
    end
    
    AFWS -.->|app-network| APPNET
    AFSCH -.->|app-network| APPNET
    AFTR -.->|app-network| APPNET
    
    AFWS --> SDB
    AFWS --> CRM
    AFSCH --> SDB
    AFSCH --> CRM
    AFSCH --> OLAP
    AFTR --> SDB
    AFTR --> CRM
    AFTR --> OLAP
```

### Назначение компонентов

| Сервис | Контейнер | Функция |
|---------|------------|-----------|
| `sensors-db` | PostgreSQL | База данных показаний EMG-датчиков |
| `crm_db` | PostgreSQL | База данных клиентов CRM |
| `olap_db` | ClickHouse | OLAP-витрина для аналитики и отчётов |
| `airflow-webserver` | Airflow | Веб-интерфейс и REST API Airflow |
| `airflow-scheduler` | Airflow | Планировщик выполнения DAG |
| `airflow-triggerer` | Airflow | Обработчик отложенных задач |
| `postgres` | PostgreSQL 16 | База метаданных Airflow |

---

## 2. Предварительные требования

### Программное обеспечение

| Компонент | Требование | Проверка |
|-----------|------------|----------|
| Docker Engine | версия 20.10+ | `docker --version` |
| Docker Compose | версия 2.0+ | `docker compose version` |
| OpenSSL | для генерации ключей | `openssl version` |

### Системные требования

| Ресурс | Минимум | Рекомендуется |
|--------|---------|----------------|
| Оперативная память | 8 ГБ | 16 ГБ |
| CPU | 4 ядра | 8 ядер |
| Дисковое пространство | 20 ГБ | 50 ГБ |

### Порты

| Порт | Сервис | Назначение |
|------|--------|--------------|
| 3000 | frontend | Веб-интерфейс приложения |
| 8000 | bionicpro-auth | Auth API |
| 8080 | keycloak | Keycloak UI |
| 8080 | airflow-webserver | Airflow UI |
| 8081 | bionicpro-reports | Reports API |
| 9000 | olap_db | ClickHouse native |
| 5432 | sensors-db | PostgreSQL sensors |
| 5433 | keycloak_db | PostgreSQL Keycloak |
| 5436 | sensors-db | PostgreSQL sensors external |
| 5444 | crm_db | PostgreSQL CRM external |

---

## 3. Настройка сетевого взаимодействия

### Архитектура сети

Система использует две сети Docker:

#### app-network

- **Тип**: Bridge, создана приложением `/app`
- **Назначение**: Сеть для баз данных и сервисов основного приложения
- **Создание**: Автоматически при запуске `app/docker-compose.yaml`

#### airflow-network

- **Тип**: Bridge, внутренняя сеть Airflow
- **Назначение**: Изолированная сеть для сервисов Airflow
- **Создание**: Автоматически при запуске `airflow/docker-compose.yaml`

### Конфигурация в docker-compose.yaml

```yaml
# airflow/docker-compose.yaml
networks:
  airflow-network:
    driver: bridge
    name: airflow-network
  app-network:
    external: true  # Подключение к внешней сети
```

### Правила подключения сервисов

| Сервис | airflow-network | app-network | Обоснование |
|--------|-----------------|-------------|-------------|
| `postgres` | Да | Нет | Только внутренний доступ Airflow |
| `airflow-webserver` | Да | Да | Доступ к БД приложения |
| `airflow-scheduler` | Да | Да | Доступ к БД приложения |
| `airflow-triggerer` | Да | Да | Доступ к БД приложения |
| `airflow-init` | Да | Нет | Только инициализация |

### Схема сетевого взаимодействия

```mermaid
graph LR
    subgraph airflow-network
        AFW[airflow-webserver]
        AFS[airflow-scheduler]
        AFT[airflow-triggerer]
        AFP[postgres - metadata]
    end
    
    subgraph app-network
        SDB[sensors-db]
        CRM[crm_db]
        OLAP[olap_db]
    end
    
    AFW -->|read data| SDB
    AFW -->|read data| CRM
    AFS -->|read data| SDB
    AFS -->|read data| CRM
    AFS -->|write data| OLAP
    AFT -->|read data| SDB
    AFT -->|read data| CRM
    AFT -->|write data| OLAP
```

---

## 4. Порядок запуска

### Важность порядка

**Критически важно**: сеть `app-network` должна быть создана до запуска Airflow, иначе возникнет ошибка:

```
ERROR: Network app-network not found
```

### Шаг 1: Подготовка файлов окружения

```bash
# Копирование шаблона для App
cp app/.env.example app/.env

# Копирование шаблона для Airflow
cp airflow/.env.example airflow/.env
```

### Шаг 2: Генерация ключей

```bash
# Linux/macOS
openssl rand -base64 32  # Fernet key
openssl rand -base64 32  # Webserver secret

# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

### Шаг 3: Заполнение переменных окружения

Редактировать файлы `.env` в соответствии с разделом 5.

### Шаг 4: Первичный запуск App

```bash
# Переход в папку приложения
cd app

# Запуск сервисов
docker compose up -d

# Проверка создания сети
docker network ls | grep app-network
```

**Ожидаемый вывод**:
```
BRIDGE     app-network   local
```

### Шаг 5: Первичный запуск Airflow

```bash
# Переход в папку Airflow
cd ../airflow

# Запуск сервисов
docker compose up -d

# Проверка статуса
docker compose ps
```

### Шаг 6: Проверка готовности

```bash
# Проверка подключения к сети
docker network inspect app-network

# Должны быть видны контейнеры:
# - airflow-webserver
# - airflow-scheduler
# - airflow-triggerer
```

### Команды управления

| Действие | Команда |
|----------|---------|
| Запустить все сервисы App | `cd app && docker compose up -d` |
| Остановить все сервисы App | `cd app && docker compose down` |
| Запустить все сервисы Airflow | `cd airflow && docker compose up -d` |
| Остановить все сервисы Airflow | `cd airflow && docker compose down` |
| Пересборка Airflow | `cd airflow && docker compose up -d --build` |
| Полный сброс Airflow | `cd airflow && docker compose down -v` |

---

## 5. Конфигурация переменных окружения

### Переменные Airflow

#### Ядро Airflow

```bash
# airflow/.env
AIRFLOW__CORE__FERNET_KEY=<generated-key>
AIRFLOW__CORE__EXECUTOR=LocalExecutor
AIRFLOW__WEBSERVER__SECRET_KEY=<generated-key>
```

#### База данных Airflow

```bash
AIRFLOW_DB_HOST=postgres
AIRFLOW_DB_PORT=5432
AIRFLOW_DB_USER=airflow
AIRFLOW_DB_PASSWORD=<set-password>
AIRFLOW_DB_NAME=airflow
```

#### Администратор Airflow

```bash
AIRFLOW_ADMIN_USER=admin
AIRFLOW_ADMIN_PASSWORD=<set-password>
```

### Переменные подключения к базам данных BionicPRO

#### База данных датчиков

| Переменная | Значение | Описание |
|-------------|-----------|----------|
| `SENSORS_DB_HOST` | `sensors-db` | Имя сервиса Docker |
| `SENSORS_DB_PORT` | `5432` | Порт PostgreSQL |
| `SENSORS_DB_PASSWORD` | `<from app/.env>` | Пароль базы данных |

#### База данных CRM

| Переменная | Значение | Описание |
|-------------|-----------|----------|
| `CRM_DB_HOST` | `crm_db` | Имя сервиса Docker |
| `CRM_DB_PORT` | `5432` | Порт PostgreSQL |
| `CRM_DB_PASSWORD` | `<from app/.env>` | Пароль базы данных |

#### OLAP база данных

| Переменная | Значение | Описание |
|-------------|-----------|----------|
| `OLAP_DB_HOST` | `olap_db` | Имя сервиса Docker |
| `OLAP_DB_PORT` | `9000` | Порт ClickHouse native |

### Mapping переменных

| Airflow | App | Сервис |
|---------|-----|--------|
| `SENSORS_DB_HOST=sensors-db` | `sensors-db` | PostgreSQL sensors |
| `CRM_DB_HOST=crm_db` | `crm_db` | PostgreSQL CRM |
| `OLAP_DB_HOST=olap_db` | `olap_db` | ClickHouse OLAP |

### Согласование паролей

**Важно**: пароли в `airflow/.env` должны совпадать с паролями в `app/.env`:

```bash
# app/.env
SENSORS_DB_PASSWORD=sensors123
CRM_DB_PASSWORD=crm123
CLICKHOUSE_PASSWORD=ch123

# airflow/.env
SENSORS_DB_PASSWORD=sensors123  # ТО ЖЕ ЗНАЧЕНИЕ
CRM_DB_PASSWORD=crm123           # ТО ЖЕ ЗНАЧЕНИЕ
```

---

## 6. Подключения к базам данных

### Sensors Database - PostgreSQL

| Параметр | Значение |
|----------|-----------|
| Host | `sensors-db` |
| Port | `5432` |
| Database | `sensors-data` |
| User | `sensors_user` |
| Password | `${SENSORS_DB_PASSWORD}` |

**Connection URI**:
```
postgresql+psycopg2://sensors_user:${SENSORS_DB_PASSWORD}@sensors-db:5432/sensors-data
```

### CRM Database - PostgreSQL

| Параметр | Значение |
|----------|-----------|
| Host | `crm_db` |
| Port | `5432` |
| Database | `crm_db` |
| User | `crm_user` |
| Password | `${CRM_DB_PASSWORD}` |

**Connection URI**:
```
postgresql+psycopg2://crm_user:${CRM_DB_PASSWORD}@crm_db:5432/crm_db
```

### OLAP Database - ClickHouse

| Параметр | Значение |
|----------|-----------|
| Host | `olap_db` |
| Port | `9000` native / `8123` HTTP |
| Database | `default` |
| User | `default` |
| Password | `${CLICKHOUSE_PASSWORD}` |

**Connection URI**:
```
clickhouse://default:${CLICKHOUSE_PASSWORD}@olap_db:9000/default
```

### Airflow Metadata Database - PostgreSQL

| Параметр | Значение |
|----------|-----------|
| Host | `postgres` |
| Port | `5432` |
| Database | `airflow` |
| User | `airflow` |
| Password | `${AIRFLOW_DB_PASSWORD}` |

**Connection URI**:
```
postgresql+psycopg2://airflow:${AIRFLOW_DB_PASSWORD}@postgres:5432/airflow
```

---

## 7. Проверка работоспособности

### Проверка подключения к базам данных

#### Sensors Database

```bash
# Из контейнера Airflow
docker exec -it airflow-scheduler python -c "
import psycopg2
conn = psycopg2.connect(
    host='sensors-db',
    port=5432,
    database='sensors-data',
    user='sensors_user',
    password='${SENSORS_DB_PASSWORD}'
)
print('Sensors DB: OK')
conn.close()
"
```

#### CRM Database

```bash
docker exec -it airflow-scheduler python -c "
import psycopg2
conn = psycopg2.connect(
    host='crm_db',
    port=5432,
    database='crm_db',
    user='crm_user',
    password='${CRM_DB_PASSWORD}'
)
print('CRM DB: OK')
conn.close()
"
```

#### OLAP Database

```bash
docker exec -it airflow-scheduler python -c "
from clickhouse_driver import Client
client = Client('olap_db', port=9000)
result = client.execute('SELECT 1')
print('ClickHouse: OK')
"
```

### Проверка Airflow UI

#### Health Check Webserver

```bash
curl http://localhost:8080/health
```

**Ожидаемый ответ**:
```json
{
  "metadatabase": "healthy",
  "scheduler": "healthy",
  "triggerer": "healthy",
  "webserver": "healthy"
}
```

#### Health Check Scheduler

```bash
curl http://localhost:8974/health
```

### Доступ к веб-интерфейсу Airflow

1. Открыть браузер: http://localhost:8080
2. Войти с учётными данными:
   - Username: `admin`
   - Password: `${AIRFLOW_ADMIN_PASSWORD}`
3. Проверить наличие DAG `bionicpro_etl_pipeline`

### Тестирование DAG

#### Ручной запуск DAG

```bash
# Через CLI
docker exec -it airflow-scheduler airflow dags trigger bionicpro_etl_pipeline

# Через API
curl -X POST "http://localhost:8080/api/v1/dags/bionicpro_etl_pipeline/dagRuns" \
  -H "Authorization: Basic $(echo -n 'admin:password' | base64)" \
  -H "Content-Type: application/json" \
  -d '{}'
```

#### Проверка статуса выполнения

```bash
docker exec -it airflow-scheduler airflow dags list-runs -d bionicpro_etl_pipeline
```

#### Просмотр логов задачи

```bash
docker exec -it airflow-scheduler airflow tasks logs bionicpro_etl_pipeline extract_sensors_data
```

---

## 8. Устранение неполадок

### Типичные проблемы и их решения

#### Ошибка: Network app-network not found

| Причина | Решение |
|----------|----------|
| App не запущен | Запустить `cd app && docker compose up -d` |
| Сеть удалена | Реинициализировать приложение |

#### Ошибка: Connection refused to sensors-db

| Причина | Решение |
|----------|----------|
| Контейнер не в сети | Проверить networks в docker-compose.yaml |
| Host не верный | Использовать `sensors-db` не localhost |
| Порт недоступен | Проверить что сервис запущен |

#### Ошибка: Authentication failed for PostgreSQL

| Причина | Решение |
|----------|----------|
| Неверный пароль | Согласовать пароли в `.env` файлах |
| Неверный user | Проверить имена пользователей |
| БД не инициализирована | Проверить logs контейнера БД |

#### DAG не отображается в UI

| Причина | Решение |
|----------|----------|
| Ошибка в синтаксисе | Проверить logs scheduler |
| Файл не в папке dags | Проверить volume mapping |
| DAG на паузе | Снять паузу через UI |

#### Ошибка: Permission denied on volumes

| Причина | Решение |
|----------|----------|
| Неверный AIRFLOW_UID | Установить `AIRFLOW_UID=$(id -u)` |
| Недостаточно прав | Запустить с правами sudo |

### Логи и отладка

#### Просмотр логов сервисов

```bash
# Все сервисы Airflow
cd airflow && docker compose logs -f

# Конкретный сервис
docker compose logs -f airflow-scheduler
docker compose logs -f airflow-webserver
docker compose logs -f postgres

# Последние 100 строк
docker compose logs --tail=100 airflow-scheduler
```

#### Проверка сетевого подключения

```bash
# Список сетей
docker network ls

# Инспекция сети app-network
docker network inspect app-network

# Тест связи из контейнера
docker exec -it airflow-scheduler ping sensors-db
docker exec -it airflow-scheduler ping crm_db
docker exec -it airflow-scheduler ping olap_db
```

#### Сброс и перезапуск

```bash
# Полный сброс Airflow с удалением данных
cd airflow
docker compose down -v
docker compose up -d

# Пересборка образов
docker compose up -d --build
```

### Таблица диагностики

| Симптом | Диагностика | Решение |
|---------|-------------|---------|
| Webserver не запускается | `docker compose logs airflow-webserver` | Проверить БД и Fernet key |
| Scheduler не выполняет задачи | `docker compose logs airflow-scheduler` | Проверить DAG и подключения |
| Нет доступа к БД | `ping <host>` из контейнера | Проверить network конфигурацию |
| Медленный запуск | `docker compose ps` | Увеличить timeout ожидания |

---

## Приложение А: Полный пример .env файлов

### app/.env

```bash
# OAuth2 AES Key 32 characters for AES-256
OAUTH2_AES_KEY=<generated-key>

# OAuth2 Salt
OAUTH2_SALT=<generated-key>

# Keycloak Client Secrets
KEYCLOAK_BIONICPRO_AUTH_CLIENT_SECRET=<generated-key>

# Database passwords
SENSORS_DB_PASSWORD=sensors_secure_pass
CRM_DB_PASSWORD=crm_secure_pass
CLICKHOUSE_PASSWORD=ch_secure_pass

# Other passwords
KEYCLOAK_ADMIN_PASSWORD=kc_admin_pass
LDAP_ADMIN_PASSWORD=ldap_admin_pass
REDIS_PASSWORD=redis_pass
MINIO_ROOT_USER=minio_user
MINIO_ROOT_PASSWORD=minio_pass
```

### airflow/.env

```bash
# Airflow Core
AIRFLOW__CORE__FERNET_KEY=<generated-key>
AIRFLOW__CORE__EXECUTOR=LocalExecutor
AIRFLOW__WEBSERVER__SECRET_KEY=<generated-key>

# Airflow Database
AIRFLOW_DB_HOST=postgres
AIRFLOW_DB_PORT=5432
AIRFLOW_DB_USER=airflow
AIRFLOW_DB_PASSWORD=airflow_secure_pass
AIRFLOW_DB_NAME=airflow

# Source Databases MUST MATCH app/.env
SENSORS_DB_HOST=sensors-db
SENSORS_DB_PORT=5432
SENSORS_DB_PASSWORD=sensors_secure_pass

CRM_DB_HOST=crm_db
CRM_DB_PORT=5432
CRM_DB_PASSWORD=crm_secure_pass

OLAP_DB_HOST=olap_db
OLAP_DB_PORT=9000

# Airflow Admin
AIRFLOW_ADMIN_USER=admin
AIRFLOW_ADMIN_PASSWORD=admin_secure_pass
```

---

## Приложение Б: Диаграмма последовательности запуска

```mermaid
sequenceDiagram
    participant U as Пользователь
    participant A as App docker-compose
    participant N as app-network
    participant F as Airflow docker-compose
    
    U->>A: cd app && docker compose up -d
    A->>N: Создание сети app-network
    A->>A: Запуск сервисов: sensors-db, crm_db, olap_db
    N-->>A: Сеть готова
    
    U->>F: cd airflow && docker compose up -d
    F->>N: Подключение к внешней сети app-network
    F->>F: Запуск сервисов: webserver, scheduler, triggerer
    F->>N: Установка связи с БД приложения
    N-->>F: Подключение установлено
    
    F->>A: Запрос к sensors-db
    A-->>F: Данные получены
    F->>A: Запрос к crm_db
    A-->>F: Данные получены
    F->>A: Запись в olap_db
    A-->>F: Данные сохранены
```

---

## Заключение

Документация описывает полную процедуру настройки сетевого взаимодействия между автономным модулем Airflow и основным приложением BionicPRO. Ключевые моменты:

1. **Порядок запуска**: Сначала App для создания сети `app-network`, затем Airflow
2. **Сетевая конфигурация**: `app-network` - внешняя сеть в Airflow
3. **Согласование паролей**: Пароли баз данных должны совпадать в обоих `.env` файлах
4. **Host имена**: Использовать Docker service names: `sensors-db`, `crm_db`, `olap_db`

Для успешного запуска следуйте инструкциям раздела 4 в указанном порядке.
