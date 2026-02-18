"""
BionicPRO ETL DAG для агрегации данных о работе протезов
"""
from datetime import datetime, timedelta
import os
from airflow import DAG
from airflow.operators.python import PythonOperator

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
    import pandas as pd
    import psycopg2
    
    # Подключение к sensors-db
    conn = psycopg2.connect(
        host='sensors-db',
        port=5432,
        dbname='sensors-data',
        user='sensors_user',
        password=os.environ.get('SENSORS_DB_PASSWORD', '')
    )
    
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
    df = pd.read_sql(sql, conn)
    conn.close()
    
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
    import pandas as pd
    import psycopg2
    
    # Подключение к crm-db
    conn = psycopg2.connect(
        host='crm_db',
        port=5432,
        dbname='crm_db',
        user='crm_user',
        password=os.environ.get('CRM_DB_PASSWORD', '')
    )
    
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
    
    df = pd.read_sql(sql, conn)
    conn.close()
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
