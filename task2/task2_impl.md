# Документация по реализации сервиса отчётов BionicPRO

> Комплексный документ, объединяющий переведённую документацию Airflow модуля с анализом соответствия требованиям задания.

---

## Часть 1. Перевод документации Airflow модуля

# Автономный модуль Airflow для BionicPRO

Автономное развертывание Apache Airflow для ETL-конвейера BionicPRO, независимое от основного монолита.

## Обзор проекта

ETL-конвейер BionicPRO — это система обработки данных на базе Apache Airflow, которая извлекает, преобразует и загружает данные из нескольких исходных баз данных в OLAP-базу данных ClickHouse для аналитики и отчётности.

### Назначение

- **Извлечение данных**: Получение данных из баз данных PostgreSQL (Sensors DB и CRM DB)
- **Преобразование данных**: Агрегация и объединение показаний датчиков с информацией о клиентах
- **Загрузка данных**: Сохранение обработанных данных в ClickHouse для высокопроизводительных аналитических запросов

### Архитектура

```mermaid
graph LR
    subgraph "Исходные базы данных"
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
3. **Загрузка**: Итоговый набор данных вставляется в таблицу `user_reports` ClickHouse

---

## Структура директорий

```
airflow/
├── dags/                      # Определения DAG
│   ├── .gitkeep
│   └── bionicpro_etl_dag.py  # Основной DAG ETL-конвейера
├── logs/                      # Логи выполнения задач
│   └── .gitkeep
├── tests/                     # Модульные тесты
│   └── test_bionicpro_etl_dag.py
├── docker-compose.yaml        # Конфигурация Docker Compose
├── requirements.txt           # Зависимости Python
├── .env.example               # Шаблон переменных окружения
└── .dockerignore              # Исключения для Docker-сборки
```

---

## Переменные окружения

### Ядро Airflow

| Переменная | По умолчанию | Описание |
|----------|---------|-------------|
| `AIRFLOW__CORE__FERNET_KEY` | (обязательно) | Ключ шифрования для конфиденциальных данных. Генерируется командой: `openssl rand -base64 32` |
| `AIRFLOW__CORE__EXECUTOR` | LocalExecutor | Тип исполнителя для выполнения задач |
| `AIRFLOW_WEBSERVER_PORT` | 8080 | Порт хоста для веб-интерфейса Airflow |
| `AIRFLOW_UID` | 50000 | ID пользователя для контейнера Airflow |

### База данных Airflow

| Переменная | По умолчанию | Описание |
|----------|---------|-------------|
| `AIRFLOW_DB_HOST` | postgres | Имя хоста контейнера PostgreSQL |
| `AIRFLOW_DB_PORT` | 5432 | Порт PostgreSQL |
| `AIRFLOW_DB_USER` | airflow | Имя пользователя базы данных |
| `AIRFLOW_DB_PASSWORD` | (обязательно) | Пароль базы данных |
| `AIRFLOW_DB_NAME` | airflow | Имя базы данных |

### Исходные базы данных (интеграция с BionicPRO)

| Переменная | По умолчанию | Описание |
|----------|---------|-------------|
| `SENSORS_DB_HOST` | sensors-db | Имя хоста PostgreSQL датчиков |
| `SENSORS_DB_PORT` | 5432 | Порт базы данных датчиков |
| `SENSORS_DB_PASSWORD` | (обязательно) | Пароль базы данных датчиков |
| `CRM_DB_HOST` | crm-db | Имя хоста PostgreSQL CRM |
| `CRM_DB_PORT` | 5432 | Порт базы данных CRM |
| `CRM_DB_PASSWORD` | (обязательно) | Пароль базы данных CRM |
| `OLAP_DB_HOST` | olap-db | Имя хоста ClickHouse |
| `OLAP_DB_PORT` | 9000 | Порт HTTP-интерфейса ClickHouse |

### Учётные данные администратора

| Переменная | По умолчанию | Описание |
|----------|---------|-------------|
| `AIRFLOW_ADMIN_USER` | admin | Имя пользователя администратора веб-интерфейса |
| `AIRFLOW_ADMIN_PASSWORD` | (обязательно) | Пароль администратора веб-интерфейса |

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
|---------|-------------|
| `docker-compose up -d` | Запуск всех сервисов |
| `docker-compose down` | Остановка всех сервисов (с сохранением данных) |
| `docker-compose down -v` | Остановка с удалением томов (сброс БД) |
| `docker-compose restart` | Перезапуск всех сервисов |
| `docker-compose logs -f` | Просмотр логов всех сервисов |
| `docker-compose logs -f <service>` | Просмотр логов конкретного сервиса |

### Доступные сервисы

| Сервис | Порт | Описание |
|---------|------|-------------|
| `postgres` | 5432 (внутренний) | База данных метаданных Airflow |
| `airflow-webserver` | 8080 | Веб-интерфейс и REST API |
| `airflow-scheduler` | - | Движок планирования DAG |
| `airflow-triggerer` | - | Обработчик отложенных задач |

### Вопросы масштабирования

- Текущая конфигурация использует `LocalExecutor` для развёртывания на одном узле
- Для многоузловой настройки переключитесь на `CeleryExecutor` и добавьте сервисы Redis/flower
- Webserver может масштабироваться горизонтально с балансировщиком нагрузки

---

## Подключения к базам данных

### Конфигурация подключений

Внешние подключения к базам данных настраиваются через переменные окружения в сети Docker Compose.

#### База данных датчиков (PostgreSQL)

```
Хост: sensors-db
Порт: 5432
База данных: sensors-data
Пользователь: sensors_user
Пароль: <из SENSORS_DB_PASSWORD>
```

#### База данных CRM (PostgreSQL)

```
Хост: crm-db
Порт: 5432
База данных: crm_db
Пользователь: crm_user
Пароль: <из CRM_DB_PASSWORD>
```

#### OLAP база данных (ClickHouse)

```
Хост: olap-db
Порт: 9000
База данных: default
```

### Сетевые требования

- Airflow должен находиться в той же сети Docker, что и исходные базы данных
- Для внешнего доступа (базы данных вне Docker) убедитесь, что правила брандмауэра разрешают подключения
- URI подключений следуют стандартным форматам:
  - PostgreSQL: `postgresql+psycopg2://user:password@host:port/dbname`
  - ClickHouse: `clickhouse://host:port/database`

---

## Шаги развёртывания

### Шаг 1: Конфигурация окружения

```bash
# Переход в директорию airflow
cd airflow

# Копирование примера файла окружения
cp .env.example .env
```

### Шаг 2: Генерация необходимых ключей

```bash
# Генерация ключа Fernet
openssl rand -base64 32

# Генерация секретного ключа Webserver
openssl rand -base64 32
```

### Шаг 3: Обновление переменных окружения

Отредактируйте файл `.env` с указанием:
- Ключа Fernet
- Секретного ключа Webserver
- Паролей баз данных
- Учётных данных администратора

### Шаг 4: Запуск сервисов

```bash
docker-compose up -d
```

### Шаг 5: Проверка работоспособности

```bash
# Проверка статуса сервисов
docker-compose ps

# Проверка состояния webserver
curl http://localhost:8080/health

# Проверка состояния scheduler
curl http://localhost:8974/health
```

### Шаг 6: Доступ к интерфейсу Airflow

1. Откройте браузер: http://localhost:8080
2. Войдите с учётными данными администратора
3. Убедитесь, что DAG `bionicpro_etl_pipeline` отображается

### Устранение неполадок

| Проблема | Решение |
|-------|----------|
| Webserver не запускается | Проверьте логи: `docker-compose logs airflow-webserver` |
| Ошибка подключения к БД | Проверьте учётные данные в `.env` и сетевое подключение |
| DAG не отображается | Убедитесь, что файл DAG находится в директории `dags/` |
| Ошибки прав доступа | Проверьте, что `AIRFLOW_UID` соответствует пользователю хоста |

---

## Документация DAG

### ETL-конвейер BionicPRO

**Файл**: [`dags/bionicpro_etl_dag.py`](dags/bionicpro_etl_dag.py)

**Расписание**: Ежедневно в 02:00 UTC (`0 2 * * *`)

**Теги**: `bionicpro`, `etl`, `analytics`

### Описания задач

#### 1. extract_sensors_data

Извлекает данные EMG-датчиков из базы данных PostgreSQL датчиков.

- **Исходная таблица**: `emg_sensor_data`
- **Извлекаемые поля**: user_id, prosthesis_type, muscle_group, signal_frequency, signal_duration, signal_amplitude, signal_time
- **Фильтр**: Данные только за дату выполнения
- **Результат**: CSV-файл по пути `/tmp/sensors_data.csv`
- **Возвращает**: Количество извлечённых записей

#### 2. extract_crm_data

Извлекает информацию о клиентах из базы данных PostgreSQL CRM.

- **Исходная таблица**: `customers`
- **Извлекаемые поля**: id, name, email, age, gender, country
- **Результат**: CSV-файл по пути `/tmp/crm_data.csv`
- **Возвращает**: Количество извлечённых записей

#### 3. transform_and_merge_data

Агрегирует данные датчиков и объединяет с информацией о клиентах.

- **Агрегации**: среднее/макс/мин амплитуды сигнала, средняя частота, общая длительность
- **Объединение**: Слияние по `user_id` с данными CRM (левое соединение)
- **Результат**: CSV-файл по пути `/tmp/merged_data.csv`
- **Возвращает**: Количество записей в итоговом наборе

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

1. Создайте новый Python-файл в директории `dags/`:

   ```python
   from airflow import DAG
   # ... ваше определение DAG
   ```

2. DAG будет автоматически обнаружен при следующем обновлении планировщика (обычно каждые 30 секунд)

3. В интерфейсе Airflow снимите паузу с нового DAG

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

Или установка во время выполнения (не рекомендуется для продакшена):

```bash
docker-compose exec airflow-webserver pip install new-package
```

---

## Зависимости

| Пакет | Версия | Назначение |
|---------|---------|---------|
| apache-airflow | >=2.8.0 | Основной движок рабочих процессов |
| psycopg2-binary | >=2.9.0 | Подключение к PostgreSQL |
| pandas | >=2.0.0 | Обработка данных |
| clickhouse-driver | >=0.2.0 | Подключение к ClickHouse |
| pytest | >=7.0.0 | Модульное тестирование |

---

## Связанная документация

- [Основной README BionicPRO](../README.md)
- [Docker Compose приложения](../app/docker-compose.yaml)
- [Архитектура BionicPRO](../analysis/arch/initial/architecture.md)

---

## Лицензия

Только для внутреннего использования в проекте BionicPRO.

---

## Часть 2. Матрица соответствия требований

### Анализ выполнения требований задания 2

Ниже представлена детальная матрица соответствия между требованиями из `task2.md` и их реализацией в сервисе Airflow.

---

## Матрица соответствия требований

### Задача 1: Создать архитектуру решения для подготовки и получения отчётов

| Требование | Реализация | Статус | Детали |
|------------|------------|--------|--------|
| ETL-процесс с Apache Airflow | ✅ Полностью реализовано | **Выполнено** | DAG `bionicpro_etl_pipeline` реализует полный ETL-процесс |
| Объединение данных sensor-db и crm-db | ✅ Полностью реализовано | **Выполнено** | Задачи `extract_sensors_data` и `extract_crm_data` с последующим объединением в `transform_and_merge_data` |
| Витрина отчётности в OLAP БД | ✅ Полностью реализовано | **Выполнено** | Таблица `user_reports` в ClickHouse с движком MergeTree |
| Доступ через бэкенд-сервис API | ✅ Полностью реализовано | **Выполнено** | Сервис `bionicpro-reports` с API `/reports` |

### Задача 2: Разработать Airflow DAG и настроить его на запуск по расписанию

| Требование | Реализация | Статус | Детали |
|------------|------------|--------|--------|
| Извлечение данных из CRM | ✅ Полностью реализовано | **Выполнено** | Задача `extract_crm_data` извлекает данные из таблицы `customers` |
| Запись в OLAP базу | ✅ Полностью реализовано | **Выполнено** | Задача `load_to_olap` загружает данные в ClickHouse |
| Объединение данных телеметрии и CRM | ✅ Полностью реализовано | **Выполнено** | Задача `transform_and_merge_data` выполняет left join по `user_id` |
| Группировка аналитики по клиентам | ✅ Полностью реализовано | **Выполнено** | Агрегации: mean/max/min amplitude, mean frequency, total duration |
| Быстрый доступ по пользователям | ✅ Полностью реализовано | **Выполнено** | Секционирование таблицы по `(user_id, report_date)` |
| Расписание сбора данных | ✅ Полностью реализовано | **Выполнено** | Cron-выражение `0 2 * * *` — ежедневно в 02:00 UTC |

### Задача 3: Создайте бэкенд-часть приложения для API

| Требование | Реализация | Статус | Детали |
|------------|------------|--------|--------|
| Язык Java | ✅ Полностью реализовано | **Выполнено** | Модуль `bionicpro-reports` на Java/Spring Boot |
| API /reports | ✅ Полностью реализовано | **Выполнено** | [`ReportController.java`](../app/bionicpro-reports/src/main/java/com/bionicpro/reports/controller/ReportController.java) |
| Запрос из OLAP без вычислений | ✅ Полностью реализовано | **Выполнено** | Прямой запрос к ClickHouse через [`ReportRepository.java`](../app/bionicpro-reports/src/main/java/com/bionicpro/reports/repository/ReportRepository.java) |

### Задача 4: Реализуйте ограничение доступа к эндпоинту отчётности

| Требование | Реализация | Статус | Детали |
|------------|------------|--------|--------|
| Доступ только к собственному отчёту | ✅ Полностью реализовано | **Выполнено** | Проверка `userId` из токена авторизации в [`ReportService.java`](../app/bionicpro-reports/src/main/java/com/bionicpro/reports/service/ReportService.java) |

### Задача 5: Добавьте в UI кнопку получения отчёта

| Требование | Реализация | Статус | Детали |
|------------|------------|--------|--------|
| UI-код для вызова API | ✅ Полностью реализовано | **Выполнено** | Компонент [`ReportPage.tsx`](../app/frontend/src/components/ReportPage.tsx) |
| Проверка аутентификации | ✅ Полностью реализовано | **Выполнено** | SecurityConfig в reports сервисе требует авторизации |
| Генерация только собственного отчёта | ✅ Полностью реализовано | **Выполнено** | Валидация userId в ReportService |
| Запросы в OLAP базу | ✅ Полностью реализовано | **Выполнено** | Использование ClickHouse драйвера |
| Только за обработанный период | ✅ Полностью реализовано | **Выполнено** | Данные загружаются после ETL-процесса по расписанию |

---

## Часть 3. Технические детали реализации

### 3.1. Архитектура ETL-процесса

#### Структура DAG

```mermaid
graph TB
    subgraph "Извлечение"
        ES[extract_sensors_data]
        EC[extract_crm_data]
    end
    
    subgraph "Преобразование"
        TM[transform_and_merge_data]
    end
    
    subgraph "Загрузка"
        LO[load_to_olap]
    end
    
    ES --> TM
    EC --> TM
    TM --> LO
```

#### Параметры DAG

| Параметр | Значение | Обоснование |
|----------|----------|-------------|
| `dag_id` | `bionicpro_etl_pipeline` | Уникальный идентификатор |
| `schedule_interval` | `0 2 * * *` | Ночное выполнение для минимальной нагрузки |
| `catchup` | `False` | Отключение обработки исторических данных |
| `max_active_runs` | `1` | Предотвращение параллельных запусков |
| `retries` | `3` | Устойчивость к временным сбоям |
| `retry_delay` | `5 minutes` | Время между повторными попытками |

### 3.2. Структура витрины данных

#### Таблица user_reports в ClickHouse

```sql
CREATE TABLE user_reports (
    user_id UInt64,
    report_date Date,
    -- Данные клиента из CRM
    customer_name String,
    email String,
    age UInt8,
    gender String,
    country String,
    -- Агрегированные данные телеметрии
    avg_signal_amplitude Float64,
    max_signal_amplitude Float64,
    min_signal_amplitude Float64,
    avg_signal_frequency Float64,
    total_signal_duration Float64,
    -- Метаданные
    prosthesis_type String,
    primary_muscle_group String,
    records_count UInt64,
    created_at DateTime
)
ENGINE = MergeTree()
PARTITION BY (user_id, report_date)
ORDER BY (user_id, report_date);
```

#### Обоснование структуры

| Элемент | Решение | Обоснование |
|---------|---------|-------------|
| Движок | MergeTree | Оптимизация для аналитических запросов |
| Секционирование | (user_id, report_date) | Быстрый доступ по пользователю и дате |
| Порядок сортировки | user_id, report_date | Соответствие паттернам запросов API |

### 3.3. Интеграция с бэкенд-сервисом

#### API Endpoints

```
GET /api/reports/{userId}
GET /api/reports/{userId}?date={date}
```

#### Диаграмма последовательности запроса отчёта

```mermaid
sequenceDiagram
    participant U as Пользователь
    participant F as Frontend
    participant A as Auth Service
    participant R as Reports Service
    participant C as ClickHouse
    
    U->>F: Нажатие кнопки Отчёт
    F->>A: Проверка токена
    A-->>F: Токен валиден + userId
    F->>R: GET /api/reports/userId
    R->>R: Проверка userId из токена
    R->>C: SELECT FROM user_reports
    C-->>R: Данные отчёта
    R-->>F: JSON-ответ
    F-->>U: Отображение отчёта
```

### 3.4. Механизм контроля доступа

#### Реализация ограничения доступа

```java
// ReportService.java - проверка прав доступа
public ReportResponse getReport(Long requestedUserId, Long authenticatedUserId) {
    if (!requestedUserId.equals(authenticatedUserId)) {
        throw new UnauthorizedAccessException(
            "Доступ запрещён: пользователь может запрашивать только свой отчёт"
        );
    }
    return reportRepository.findByUserId(requestedUserId);
}
```

### 3.5. Конфигурация подключений

#### Переменные окружения для подключения к БД

| Компонент | Переменная | Значение |
|-----------|------------|----------|
| Sensors DB | `SENSORS_DB_HOST` | `sensors-db` |
| Sensors DB | `SENSORS_DB_PORT` | `5432` |
| CRM DB | `CRM_DB_HOST` | `crm-db` |
| CRM DB | `CRM_DB_PORT` | `5432` |
| OLAP DB | `OLAP_DB_HOST` | `olap-db` |
| OLAP DB | `OLAP_DB_PORT` | `9000` |

### 3.6. Обработка граничных случаев

#### Сценарий: Данные ещё не обработаны Airflow

| Условие | Обработка |
|---------|-----------|
| Запрошенная дата > последней обработки | Возврат пустого отчёта или сообщения о недоступности |
| Нет данных для пользователя | `ReportNotFoundException` |
| Частичная обработка | Возврат доступных данных с указанием периода |

### 3.7. Мониторинг и логирование

#### Метрики Airflow

| Метрика | Описание |
|---------|----------|
| `dag_success` | Успешное завершение DAG |
| `dag_duration` | Время выполнения DAG |
| `task_success_rate` | Процент успешных задач |
| `records_processed` | Количество обработанных записей |

#### Логирование бэкенд-сервиса

```yaml
logging:
  level:
    com.bionicpro.reports: DEBUG
    org.springframework.security: INFO
```

---

## Часть 4. Верификация выполнения требований

### Чек-лист проверки перед отправкой

| Проверка | Статус | Комментарий |
|----------|--------|-------------|
| UI-код позволяет вызвать API для генерации отчётов | ✅ | [`ReportPage.tsx`](../app/frontend/src/components/ReportPage.tsx) реализует вызов `/api/reports` |
| Реализация не позволяет генерировать отчёт неаутентифицированному пользователю | ✅ | `SecurityConfig` требует авторизацию для `/api/reports/**` |
| Авторизованный пользователь может генерировать только свой отчёт | ✅ | Проверка `userId` в `ReportService.getReport()` |
| Приложение отправляет запросы в OLAP базу | ✅ | `ReportRepository` использует ClickHouse JDBC драйвер |
| Генерация только за обработанный Airflow период | ✅ | Данные доступны только после загрузки через `load_to_olap` |

---

## Заключение

Реализованный сервис отчётов BionicPRO полностью соответствует требованиям задания 2:

1. **Архитектура**: ETL-процесс на базе Apache Airflow интегрирует данные из Sensors DB и CRM DB, формируя витрину отчётности в ClickHouse.

2. **DAG и расписание**: DAG `bionicpro_etl_pipeline` выполняется ежедневно в 02:00 UTC, обрабатывая данные за предыдущий день.

3. **Бэкенд API**: Java-сервис `bionicpro-reports` предоставляет REST API для получения отчётов с прямой выборкой из OLAP.

4. **Контроль доступа**: Реализовано ограничение доступа — пользователь может запрашивать только собственный отчёт.

5. **UI интеграция**: Frontend-компонент `ReportPage` обеспечивает пользовательский интерфейс для генерации отчётов.

Все технические решения оптимизированы для производительности и соответствуют лучшим практикам разработки ETL-систем и микросервисной архитектуры.
