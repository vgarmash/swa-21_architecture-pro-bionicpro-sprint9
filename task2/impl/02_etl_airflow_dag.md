# План разработки ETL-процесса на Apache Airflow

## 1. Настройка Apache Airflow

### 1.1 Добавление сервисов в docker-compose.yaml

```yaml
services:
  # ... существующие сервисы ...
  
  airflow-webserver:
    image: apache/airflow:2.8.1
    environment:
      AIRFLOW__CORE__EXECUTOR: LocalExecutor
      AIRFLOW__CORE__SQL_ALCHEMY_CONN: postgresql+psycopg2://airflow:airflow@airflow-db/airflow
      AIRFLOW__CORE__FERNET_KEY: ''
      AIRFLOW__CORE__DAGS_ARE_PAUSED_AT_CREATION: 'true'
      AIRFLOW__CORE__LOAD_EXAMPLES: 'false'
      AIRFLOW__API__AUTH_BACKENDS: 'airflow.api.auth.backend.basic_auth'
      AIRFLOW_HOME: /opt/airflow
    volumes:
      - ./airflow/dags:/opt/airflow/dags
      - ./airflow/logs:/opt/airflow/logs
    ports:
      - "8090:8080"
    depends_on:
      - airflow-db
    networks:
      - app-network

  airflow-db:
    image: postgres:14
    environment:
      POSTGRES_DB: airflow
      POSTGRES_USER: airflow
      POSTGRES_PASSWORD: airflow
    volumes:
      - ./airflow/db-data:/var/lib/postgresql/data
    networks:
      - app-network
```

### 1.2 Сетевые настройки

Добавить все сервисы в общую сеть `app-network` для взаимодействия.

---

## 2. Структура DAG

### 2.1 Диаграмма потока данных

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Sensors DB     │     │    CRM DB       │     │  ClickHouse     │
│  (PostgreSQL)   │     │  (PostgreSQL)   │     │    (OLAP)       │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Apache Airflow DAG                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │   Extract    │    │   Extract    │    │   Transform   │   │
│  │  Sensors     │───▶│     CRM      │───▶│   & Merge     │   │
│  │   Data       │    │    Data      │    │    Data       │   │
│  └──────────────┘    └──────────────┘    └───────┬──────┘   │
│                                                   │            │
│                                                   ▼            │
│                                            ┌──────────────┐   │
│                                            │    Load      │   │
│                                            │  to OLAP     │   │
│                                            └──────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Реализация DAG (Python)

### 3.1 Файл: airflow/dags/bionicpro_etl_dag.py

```python
"""
BionicPRO ETL DAG для агрегации данных о работе протезов
"""
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.postgres_operator import PostgresOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from airflow.providers.http.operators.http import SimpleHttpOperator

# Параметры по умолчанию
default_args = {
    'owner': 'bionicpro',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 3,
    'retry_delay': timedelta(minutes=5),
}

# Определение DAG
dag = DAG(
    'bionicpro_etl_pipeline',
    default_args=default_args,
    description='ETL pipeline для агрегации данных протезов',
    schedule_interval='0 2 * * *',  # Запуск каждый день в 2:00
    catchup=False,
    tags=['bionicpro', 'etl', 'analytics'],
)

# ============================================================
# Таск 1: Извлечение данных из Sensors DB
# ============================================================
def extract_sensors_data(**context):
    """Извлечение данных ЭМГ-сенсоров из PostgreSQL"""
    import psycopg2
    from airflow.providers.postgres.hooks.postgres import PostgresHook
    
    # Подключение к sensors-db
    pg_hook = PostgresHook(postgres_conn_id='sensors_db_conn')
    
    # SQL запрос для извлечения данных
    sql = """
        SELECT 
            user_id,
            prosthesis_type,
            muscle_group,
            signal_frequency,
            signal_duration,
            signal_amplitude,
            signal_time
        FROM emg_sensor_data
        WHERE signal_time >= '{{ ds }}'
          AND signal_time < '{{ tomorrow_ds }}'
    """
    
    # Извлечение данных
    df = pg_hook.get_pandas_df(sql)
    
    # Сохранение во временный файл для передачи следующим таскам
    df.to_csv('/tmp/sensors_data.csv', index=False)
    
    return df.shape[0]

extract_sensors = PythonOperator(
    task_id='extract_sensors_data',
    python_callable=extract_sensors_data,
    dag=dag,
)

# ============================================================
# Таск 2: Извлечение данных из CRM DB
# ============================================================
def extract_crm_data(**context):
    """Извлечение данных о клиентах из CRM PostgreSQL"""
    import psycopg2
    from airflow.providers.postgres.hooks.postgres import PostgresHook
    
    # Подключение к crm-db
    pg_hook = PostgresHook(postgres_conn_id='crm_db_conn')
    
    # SQL запрос
    sql = """
        SELECT 
            id as user_id,
            name,
            email,
            age,
            gender,
            country
        FROM customers
    """
    
    df = pg_hook.get_pandas_df(sql)
    df.to_csv('/tmp/crm_data.csv', index=False)
    
    return df.shape[0]

extract_crm = PythonOperator(
    task_id='extract_crm_data',
    python_callable=extract_crm_data,
    dag=dag,
)

# ============================================================
# Таск 3: Трансформация и объединение данных
# ============================================================
def transform_and_merge_data(**context):
    """Трансформация и объединение данных sensors и CRM"""
    import pandas as pd
    
    # Чтение данных
    sensors_df = pd.read_csv('/tmp/sensors_data.csv')
    crm_df = pd.read_csv('/tmp/crm_data.csv')
    
    # Агрегация по пользователю
    aggregated = sensors_df.groupby(['user_id', 'prosthesis_type', 'muscle_group']).agg({
        'signal_amplitude': ['mean', 'max', 'min', 'count'],
        'signal_frequency': 'mean',
        'signal_duration': 'sum'
    }).reset_index()
    
    # Упрощение названий колонок
    aggregated.columns = [
        'user_id', 'prosthesis_type', 'muscle_group',
        'avg_signal_amplitude', 'max_signal_amplitude', 'min_signal_amplitude', 'total_sessions',
        'avg_signal_frequency', 'total_usage_hours'
    ]
    
    # Преобразование duration из секунд в часы
    aggregated['total_usage_hours'] = aggregated['total_usage_hours'] / 3600
    
    # Объединение с данными CRM
    merged_df = aggregated.merge(crm_df, on='user_id', how='left')
    
    # Добавление даты отчёта
    merged_df['report_date'] = context['ds']
    
    # Сохранение
    merged_df.to_csv('/tmp/merged_data.csv', index=False)
    
    return merged_df.shape[0]

transform_data = PythonOperator(
    task_id='transform_and_merge_data',
    python_callable=transform_and_merge_data,
    dag=dag,
)

# ============================================================
# Таск 4: Загрузка в ClickHouse (OLAP)
# ============================================================
def load_to_olap(**context):
    """Загрузка агрегированных данных в ClickHouse"""
    import pandas as pd
    from clickhouse_driver import Client
    
    # Чтение данных
    df = pd.read_csv('/tmp/merged_data.csv')
    
    # Подключение к ClickHouse
    client = Client(
        host='olap_db',
        port=9000,
        database='default'
    )
    
    # Создание таблицы, если не существует
    create_table_sql = """
    CREATE TABLE IF NOT EXISTS user_reports (
        user_id UInt32,
        report_date Date,
        total_sessions UInt32,
        avg_signal_amplitude Float32,
        max_signal_amplitude Float32,
        min_signal_amplitude Float32,
        avg_signal_frequency Float32,
        total_usage_hours Float32,
        prosthesis_type String,
        muscle_group String,
        customer_name String,
        customer_email String,
        customer_age UInt8,
        customer_gender String,
        customer_country String,
        created_at DateTime DEFAULT now()
    ) ENGINE = MergeTree()
    ORDER BY (user_id, report_date)
    """
    client.execute(create_table_sql)
    
    # Вставка данных
    data = df.to_dict('records')
    client.execute(
        'INSERT INTO user_reports VALUES',
        data
    )
    
    return len(data)

load_to_olap = PythonOperator(
    task_id='load_to_olap',
    python_callable=load_to_olap,
    dag=dag,
)

# ============================================================
# Определение зависимостей
# ============================================================
[extract_sensors, extract_crm] >> transform_data >> load_to_olap
```

---

## 4. Airflow Connections

### 4.1 Настройка подключений в Airflow UI

| Connection ID | Type | Host | Port | Database |
|---------------|------|------|------|----------|
| sensors_db_conn | Postgres | sensors-db | 5432 | sensors-data |
| crm_db_conn | Postgres | crm_db | 5432 | crm_db |
| olap_db_conn | HTTP | olap_db | 8123 | - |

---

## 5. Расписание

### 5.1 Cron выражение
```
0 2 * * *  # Каждый день в 2:00 ночи
```

### 5.2 Альтернативные варианты

| Вариант | Cron | Описание |
|---------|------|----------|
| Ежедневно | `0 2 * * *` | Каждый день в 2:00 |
| Еженедельно | `0 2 * * 0` | Каждое воскресенье |
| Раз в час | `0 * * * *` | Каждый час |

---

## 6. Обработка ошибок и мониторинг

### 6.1 Retry политика
- 3 попытки при сбое
- Интервал между попытками: 5 минут

### 6.2 Уведомления
- Email при критических сбоях
- Логирование в Airflow UI

### 6.3 Мониторинг
- Airflow Web UI (port 8090)
- Airflow Logs

---

## 7. Следующие шаги

Перейдите к документу [03_reports_api_service.md](03_reports_api_service.md) для детального плана реализации API сервиса отчётов.

---

## 8. Критерии приёмки

### Функциональные требования
| № | Критерий | Условие проверки | Ожидаемый результат |
|---|----------|------------------|---------------------|
| 1 | DAG содержит таск extract_sensors_data | Проверить код DAG | Таск извлекает данные из sensors-db |
| 2 | DAG содержит таск extract_crm_data | Проверить код DAG | Таск извлекает данные из crm-db |
| 3 | DAG содержит таск transform_and_merge_data | Проверить код DAG | Таск выполняет трансформацию и объединение данных |
| 4 | DAG содержит таск load_to_olap | Проверить код DAG | Таск загружает данные в ClickHouse |
| 5 | DAG настроен на запуск по расписанию | Проверить параметр schedule | Расписание указано (например, `0 2 * * *`) |
| 6 | DAG использует PythonOperator | Проверить код DAG | Используются PythonOperator для выполнения Python-кода |
| 7 | DAG подключается к PostgreSQL (sensors-db) | Проверить настройки подключения | Есть настройки подключения к sensors-db |
| 8 | DAG подключается к PostgreSQL (crm-db) | Проверить настройки подключения | Есть настройки подключения к crm-db |
| 9 | DAG подключается к ClickHouse | Проверить настройки подключения | Есть настройки подключения к OLAP |
| 10 | DAG создаёт/обновляет таблицу user_reports | Проверить SQL-запросы | Присутствует создание/обновление витрины |

### Граничные условия
| № | Критерий | Условие проверки | Ожидаемый результат |
|---|----------|------------------|---------------------|
| 1 | Обработка пустых данных sensors | Проверить код | DAG корректно обрабатывает отсутствие данных |
| 2 | Обработка пустых данных CRM | Проверить код | DAG корректно обрабатывает отсутствие данных |
| 3 | Обработка ошибок подключения | Проверить обработку ошибок | DAG логирует и обрабатывает ошибки подключения |
| 4 | Идемпотентность | Проверить логику | DAG может безопасно запускаться повторно |

## 9. Тестовые спецификации

### 9.1 Юнит-тесты

#### 9.1.1 Тесты для таска extract_sensors_data

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_extract_sensors_data_success | MOCK подключение к sensors-db, таблица emg_sensor_data с записями: user_id="user-001", session_id="session-001", timestamp="2024-01-15T10:00:00Z", amplitude=45.5, frequency=25.3 | Возвращается DataFrame с колонками: user_id, session_id, timestamp, amplitude, frequency | MOCK базы данных PostgreSQL, таблица содержит данные | Тест проходит если DataFrame содержит корректные данные |
| 2 | test_extract_sensors_data_empty | MOCK подключение к sensors-db, пустая таблица emg_sensor_data | Возвращается пустой DataFrame с теми же колонками | MOCK базы данных PostgreSQL, таблица пуста | Тест проходит если возвращается пустой DataFrame с правильными колонками |
| 3 | test_extract_sensors_data_connection_error | MOCK подключение к sensors-db с ошибкой ConnectionException | Генерируется исключение, которое логируется | MOCK базы данных PostgreSQL недоступен | Тест проходит если исключение корректно обрабатывается и логируется |

#### 9.1.2 Тесты для таска extract_crm_data

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_extract_crm_data_success | MOCK подключение к crm-db, таблица customers с записями: customer_id="cust-001", user_id="user-001", name="Иван Иванов", prosthesis_type="biohand-pro", purchase_date="2024-01-01" | Возвращается DataFrame с колонками: customer_id, user_id, name, prosthesis_type, purchase_date | MOCK базы данных PostgreSQL, таблица содержит данные | Тест проходит если DataFrame содержит корректные данные |
| 2 | test_extract_crm_data_empty | MOCK подключение к crm-db, пустая таблица customers | Возвращается пустой DataFrame с теми же колонками | MOCK базы данных PostgreSQL, таблица пуста | Тест проходит если возвращается пустой DataFrame с правильными колонками |
| 3 | test_extract_crm_data_connection_error | MOCK подключение к crm-db с ошибкой ConnectionException | Генерируется исключение, которое логируется | MOCK базы данных PostgreSQL недоступен | Тест проходит если исключение корректно обрабатывается и логируется |

#### 9.1.3 Тесты для таска transform_and_merge_data

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_transform_data_with_valid_input | DataFrame sensors: [{"user_id":"user-001","session_id":"session-001","timestamp":"2024-01-15T10:00:00Z","amplitude":45.5,"frequency":25.3}], DataFrame CRM: [{"customer_id":"cust-001","user_id":"user-001","name":"Иван Иванов","prosthesis_type":"biohand-pro","purchase_date":"2024-01-01"}] | Объединённый DataFrame с агрегациями: user_id, total_sessions, avg_signal_amplitude, max_signal_amplitude, min_signal_amplitude, avg_signal_frequency, total_usage_hours, prosthesis_type | Оба DataFrame содержат данные | Тест проходит если данные объединены и агрегированы корректно |
| 2 | test_transform_data_with_empty_sensors | Пустой DataFrame sensors, DataFrame CRM с данными | Объединённый DataFrame где поля из sensors заполнены null/0 | DataFrame sensors пустой | Тест проходит если корректно обрабатываются null/0 значения |
| 3 | test_transform_data_with_empty_crm | DataFrame sensors с данными, пустой DataFrame CRM | Объединённый DataFrame где поля из CRM заполнены null | DataFrame CRM пустой | Тест проходит если корректно обрабатываются null значения |
| 4 | test_transform_data_aggregation | DataFrame sensors с 3 записями для одного user_id | DataFrame с одной строкой для user_id и агрегированными значениями | Несколько записей для одного пользователя | Тест проходит если агрегация считается правильно (SUM, AVG, MAX, MIN) |

#### 9.1.4 Тесты для таска load_to_olap

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_load_to_olap_valid_data | Объединённый DataFrame с данными | Данные успешно вставлены в ClickHouse | Подключение к ClickHouse активно, таблица user_reports существует | Тест проходит если данные вставлены без ошибок |
| 2 | test_load_to_olap_empty_data | Пустой DataFrame | Операция вставки не вызывается | DataFrame пустой | Тест проходит если не происходит попытка вставить пустые данные |
| 3 | test_load_to_olap_connection_error | Объединённый DataFrame с данными, MOCK ClickHouse с ошибкой | Генерируется исключение, которое логируется | Подключение к ClickHouse недоступно | Тест проходит если исключение корректно обрабатывается |

#### 9.1.5 Тесты для структуры DAG

| № | Название теста | Входные данные | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|----------------|---------------------|-------------------|---------------------|
| 1 | test_dag_task_order | DAG структура | Правильная последовательность: extract_sensors_data → extract_crm_data → transform_and_merge_data → load_to_olap | DAG загружен в Airflow | Тест проходит если все зависимости между тасками правильные |
| 2 | test_dag_schedule | DAG с настройкой schedule_interval | Интервал `0 2 * * *` (каждый день в 2:00) | DAG загружен в Airflow | Тест проходит если расписание установлено корректно |
| 3 | test_dag_retries | DAG с настройкой retry | Настроены retry при сбоях (например, 3 попытки) | DAG загружен в Airflow | Тест проходит если retry настроены |
| 4 | test_dag_idempotency | Запуск DAG дважды с теми же данными | Результат не дублируется, данные обновляются | Таблица user_reports существует | Тест проходит если повторный запуск не создаёт дубликаты |

### 9.2 Интеграционные тесты

| № | Название теста | Компоненты | Ожидаемый результат | Условия выполнения | Критерии успешности |
|---|---------------|------------|---------------------|-------------------|---------------------|
| 1 | test_airflow_sensors_connection | Airflow ↔ sensors-db | Успешное подключение и извлечение данных | sensors-db запущена и доступна | Тест проходит если соединение установлено |
| 2 | test_airflow_crm_connection | Airflow ↔ crm-db | Успешное подключение и извлечение данных | crm-db запущена и доступна | Тест проходит если соединение установлено |
| 3 | test_airflow_clickhouse_connection | Airflow ↔ ClickHouse | Успешное подключение и запись данных | ClickHouse запущен и доступен | Тест проходит если соединение установлено |
| 4 | test_full_etl_pipeline | Airflow → sensors-db → crm-db → ClickHouse | Успешное выполнение всего DAG | Все БД доступны | Тест проходит если весь пайплайн завершается успешно |
| 5 | test_etl_data_quality | Данные в OLAP | Данные соответствуют ожидаемой схеме: user_id UInt32, report_date Date, total_sessions UInt32, avg_signal_amplitude Float32, и т.д. | DAG выполнен | Тест проходит если схема данных корректна |

### 9.3 Тестовые данные

#### 9.3.1 Данные для sensors-db (emg_sensor_data)

```python
# Тестовые данные для юнит-тестов
test_sensors_data = [
    {
        "user_id": "user-001",
        "session_id": "session-001",
        "timestamp": "2024-01-15T10:00:00Z",
        "amplitude": 45.5,
        "frequency": 25.3
    },
    {
        "user_id": "user-001",
        "session_id": "session-002",
        "timestamp": "2024-01-15T11:00:00Z",
        "amplitude": 50.2,
        "frequency": 28.1
    }
]
```

#### 9.3.2 Данные для crm-db (customers)

```python
# Тестовые данные для юнит-тестов
test_crm_data = [
    {
        "customer_id": "cust-001",
        "user_id": "user-001",
        "name": "Иван Иванов",
        "prosthesis_type": "biohand-pro",
        "purchase_date": "2024-01-01"
    }
]
```

#### 9.3.3 Ожидаемые агрегированные данные для ClickHouse

```python
# Ожидаемый результат трансформации
expected_aggregated_data = [
    {
        "user_id": 1,
        "report_date": "2024-01-15",
        "total_sessions": 2,
        "avg_signal_amplitude": 47.85,
        "max_signal_amplitude": 50.2,
        "min_signal_amplitude": 45.5,
        "avg_signal_frequency": 26.7,
        "total_usage_hours": 2.0,
        "prosthesis_type": "biohand-pro"
    }
]
```