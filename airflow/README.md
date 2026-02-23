# Автономный модуль Airflow для BionicPRO

Автономное развертывание Apache Airflow для ETL-конвейера BionicPRO, отделенное от основного монолита.

## Обзор проекта

ETL-конвейер BionicPRO — это система обработки данных на базе Apache Airflow, которая извлекает, преобразует и загружает данные из нескольких исходных баз данных в OLAP-базу данных ClickHouse для аналитики и отчетности.

### Назначение

- **Извлечение данных**: Получение данных из баз данных PostgreSQL (Sensors DB и CRM DB)
- **Преобразование данных**: Агрегация и объединение показаний датчиков с информацией о клиентах
- **Загрузка данных**: Сохранение обработанных данных в ClickHouse для высокопроизводительных аналитических запросов

### Архитектура

```mermaid
graph LR
    subgraph "Source Databases"
        SDB[Sensors DB<br/>PostgreSQL]
        CRM[CRM DB<br/>PostgreSQL]
    end
    
    subgraph "Airflow"
        DAG[ETL DAG]
    end
    
    subgraph "OLAP"
        CH[ClickHouse]
    end
    
    SDB -->|extract_sensors_data| DAG
    CRM -->|extract_crm_data| DAG
    DAG -->|load_to_olap| CH
```

### Поток данных

1. **Извлечение**: Две параллельные задачи получают данные из Sensors DB и CRM DB
2. **Преобразование**: Данные агрегируются и объединяются по user_id
3. **Загрузка**: Финальный набор данных вставляется в таблицу ClickHouse `user_reports`

---

## Структура каталогов

```
airflow/
├── dags/                      # Определения DAG
│   ├── .gitkeep
│   └── bionicpro_etl_dag.py  # Основной ETL-конвейер DAG
├── logs/                      # Логи выполнения задач
│   └── .gitkeep
├── tests/                     # Модульные тесты
│   └── test_bionicpro_etl_dag.py
├── docker-compose.yaml        # Конфигурация Docker Compose
├── requirements.txt           # Зависимости Python
├── .env.example               # Шаблон переменных окружения
└── .dockerignore              # Исключения для сборки Docker
```

---

## Переменные окружения

### Airflow Core

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `AIRFLOW__CORE__FERNET_KEY` | (required) | Ключ шифрования для конфиденциальных данных. Сгенерировать: `openssl rand -base64 32` |
| `AIRFLOW__CORE__EXECUTOR` | LocalExecutor | Тип исполнителя для выполнения задач |
| `AIRFLOW_WEBSERVER_PORT` | 8080 | Порт хоста для веб-интерфейса Airflow |
| `AIRFLOW_UID` | 50000 | ID пользователя для контейнера Airflow |

### База данных Airflow

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `AIRFLOW_DB_HOST` | postgres | Имя хоста контейнера PostgreSQL |
| `AIRFLOW_DB_PORT` | 5432 | Порт PostgreSQL |
| `AIRFLOW_DB_USER` | airflow | Имя пользователя базы данных |
| `AIRFLOW_DB_PASSWORD` | (required) | Пароль базы данных |
| `AIRFLOW_DB_NAME` | airflow | Имя базы данных |

### Исходные базы данных (Интеграция BionicPRO)

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `SENSORS_DB_HOST` | sensors-db | Имя хоста PostgreSQL датчиков |
| `SENSORS_DB_PORT` | 5432 | Порт базы данных датчиков |
| `SENSORS_DB_PASSWORD` | (required) | Пароль базы данных датчиков |
| `CRM_DB_HOST` | crm-db | Имя хоста PostgreSQL CRM |
| `CRM_DB_PORT` | 5432 | Порт базы данных CRM |
| `CRM_DB_PASSWORD` | (required) | Пароль базы данных CRM |
| `OLAP_DB_HOST` | olap-db | Имя хоста ClickHouse |
| `OLAP_DB_PORT` | 9000 | Порт HTTP-интерфейса ClickHouse |

### Учетные данные администратора

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `AIRFLOW_ADMIN_USER` | admin | Имя пользователя администратора веб-интерфейса |
| `AIRFLOW_ADMIN_PASSWORD` | (required) | Пароль администратора веб-интерфейса |

---

## Использование Docker Compose

### Предварительные требования

- Docker Engine 20.10+
- Docker Compose 2.0+

### Быстрый старт

1. **Копирование шаблона окружения**

   ```bash
   cp airflow/.env.example airflow/.env
   ```

2. **Генерация ключа Fernet**

   ```bash
   # Linux/macOS
   openssl rand -base64 32
   # Windows (PowerShell)
   [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
   ```

3. **Обновление файла `.env`** сгенерированными ключами и паролями

4. **Запуск сервисов Airflow**

   ```bash
   cd airflow
   docker-compose up -d
   ```

5. **Доступ к веб-интерфейсу**

   - URL: http://localhost:8080
   - Имя пользователя: `admin` (или настроенное значение)
   - Пароль: (как указано в `.env`)

### Управление сервисами

| Команда | Описание |
|---------|----------|
| `docker-compose up -d` | Запуск всех сервисов |
| `docker-compose down` | Остановка всех сервисов (сохранение данных) |
| `docker-compose down -v` | Остановка и удаление томов (сброс БД) |
| `docker-compose restart` | Перезапуск всех сервисов |
| `docker-compose logs -f` | Просмотр логов всех сервисов |
| `docker-compose logs -f <service>` | Просмотр логов конкретного сервиса |

### Доступные сервисы

| Сервис | Порт | Описание |
|--------|------|----------|
| `postgres` | 5432 (внутренний) | База данных метаданных Airflow |
| `airflow-webserver` | 8080 | Веб-интерфейс и REST API |
| `airflow-scheduler` | - | Планировщик DAG |
| `airflow-triggerer` | - | Обработчик отложенных задач |

### Масштабирование

- В настоящее время настроено с `LocalExecutor` для одноузлового развертывания
- Для многоузловой настройки переключитесь на `CeleryExecutor` и добавьте сервисы Redis/flower
- Webserver можно масштабировать горизонтально с балансировщиком нагрузки

---

## Подключения к базам данных

### Конфигурация подключения

Внешние подключения к базам данных настраиваются через переменные окружения в сети Docker Compose.

#### База данных датчиков (PostgreSQL)

```
Host: sensors-db
Port: 5432
Database: sensors-data
User: sensors_user
Password: <из SENSORS_DB_PASSWORD>
```

#### База данных CRM (PostgreSQL)

```
Host: crm-db
Port: 5432
Database: crm_db
User: crm_user
Password: <из CRM_DB_PASSWORD>
```

#### OLAP база данных (ClickHouse)

```
Host: olap-db
Port: 9000
Database: default
```

### Требования к сети

- Airflow должен находиться в той же Docker-сети, что и исходные базы данных
- Для внешнего доступа (базы данных вне Docker) убедитесь, что файрвол разрешает подключения
- URI подключения следуют стандартным форматам:
  - PostgreSQL: `postgresql+psycopg2://user:password@host:port/dbname`
  - ClickHouse: `clickhouse://host:port/database`

---

## Шаги развертывания

### Шаг 1: Настройка окружения

```bash
# Переход в директорию airflow
cd airflow

# Копирование примера файла окружения
cp .env.example .env
```

### Шаг 2: Генерация требуемых ключей

```bash
# Генерация ключа Fernet
openssl rand -base64 32

# Генерация секретного ключа веб-сервера
openssl rand -base64 32
```

### Шаг 3: Обновление переменных окружения

Редактирование файла `.env` с:
- Ключом Fernet
- Секретным ключом веб-сервера
- Паролями баз данных
- Учетными данными администратора

### Шаг 4: Запуск сервисов

```bash
docker-compose up -d
```

### Шаг 5: Проверка работоспособности

```bash
# Проверка статуса сервисов
docker-compose ps

# Проверка работоспособности веб-сервера
curl http://localhost:8080/health

# Проверка работоспособности планировщика
curl http://localhost:8974/health
```

### Шаг 6: Доступ к интерфейсу Airflow

1. Открыть браузер: http://localhost:8080
2. Войти с учетными данными администратора
3. Проверить видимость DAG `bionicpro_etl_pipeline`

### Устранение неполадок

| Проблема | Решение |
|----------|---------|
| Webserver не запускается | Проверить логи: `docker-compose logs airflow-webserver` |
| Ошибка подключения к базе данных | Проверить учетные данные в `.env` и сетевое подключение |
| DAG не отображается | Убедиться, что файл DAG находится в директории `dags/` |
| Ошибки разрешений | Проверить соответствие `AIRFLOW_UID` пользователю хоста |

---

## Документация DAG

### ETL-конвейер BionicPRO

**Файл**: [`dags/bionicpro_etl_dag.py`](dags/bionicpro_etl_dag.py)

**Расписание**: Ежедневно в 02:00 UTC (`0 2 * * *`)

**Теги**: `bionicpro`, `etl`, `analytics`

### Описание задач

#### 1. extract_sensors_data

Извлекает данные EMG-датчиков из базы данных Sensors PostgreSQL.

- **Исходная таблица**: `emg_sensor_data`
- **Извлекаемые поля**: user_id, prosthesis_type, muscle_group, signal_frequency, signal_duration, signal_amplitude, signal_time
- **Фильтр**: Только данные на дату выполнения
- **Вывод**: CSV-файл по адресу `/tmp/sensors_data.csv`
- **Возвращает**: Количество извлеченных записей

#### 2. extract_crm_data

Извлекает информацию о клиентах из базы данных CRM PostgreSQL.

- **Исходная таблица**: `customers`
- **Извлекаемые поля**: id, name, email, age, gender, country
- **Вывод**: CSV-файл по адресу `/tmp/crm_data.csv`
- **Возвращает**: Количество извлеченных записей

#### 3. transform_and_merge_data

Агрегирует данные датчиков и объединяет с информацией о клиентах.

- **Агрегации**: среднее/максимальное/минимальное значение амплитуды сигнала, средняя частота, общая продолжительность
- **Объединение**: Объединение по `user_id` с данными CRM (левое объединение)
- **Вывод**: CSV-файл по адресу `/tmp/merged_data.csv`
- **Возвращает**: Количество записей в финальном наборе данных

#### 4. load_to_olap

Загружает обработанные данные в OLAP-базу данных ClickHouse.

- **Целевая таблица**: `user_reports`
- **Движок**: MergeTree
- **Секционирование**: По (user_id, report_date)
- **Возвращает**: Количество вставленных записей

### Зависимости задач

```
extract_sensors_data ─┐
                      ├─> transform_and_merge_data ─> load_to_olap
extract_crm_data ──────┘
```

### Аргументы по умолчанию

- `owner`: bionicpro
- `depends_on_past`: false
- `retries`: 3
- `retry_delay`: 5 минут
- `email_on_failure`: false

---

## Разработка

### Добавление новых DAG

1. Создайте новый файл Python в директории `dags/`:

   ```python
   from airflow import DAG
   # ... ваше определение DAG
   ```

2. DAG будет автоматически обнаружен при следующем обновлении планировщика (обычно каждые 30 секунд)

3. В интерфейсе Airflow активируйте новый DAG

### Запуск тестов

```bash
# Запуск всех тестов
pytest airflow/tests/

# Запуск конкретного тестового файла
pytest airflow/tests/test_bionicpro_etl_dag.py

# Запуск с подробным выводом
pytest -v airflow/tests/
```

### Установка дополнительных пакетов Python

Отредактируйте `requirements.txt` и пересоберите контейнеры:

```bash
# Добавление пакета в requirements.txt
echo "new-package>=1.0.0" >> requirements.txt

# Пересборка контейнеров
docker-compose up -d --build
```

Или установите во время выполнения (не рекомендуется для продакшена):

```bash
docker-compose exec airflow-webserver pip install new-package
```

---

## Зависимости

| Пакет | Версия | Назначение |
|-------|--------|------------|
| apache-airflow | >=2.8.0 | Основной движок рабочего процесса |
| psycopg2-binary | >=2.9.0 | Подключение к PostgreSQL |
| pandas | >=2.0.0 | Обработка данных |
| clickhouse-driver | >=0.2.0 | Подключение к ClickHouse |
| pytest | >=7.0.0 | Модульное тестирование |

---

## Связанная документация

- [Осной README BionicPRO](../README.md)
- [Docker Compose приложения](../app/docker-compose.yaml)
