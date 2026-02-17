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