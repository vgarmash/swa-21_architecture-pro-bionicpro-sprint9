-- Таблица для сырых данных с датчиков ЭМГ
CREATE TABLE IF NOT EXISTS emg_sensor_data (
    user_id UInt32,
    prosthesis_type String,
    muscle_group String,
    signal_frequency UInt32,
    signal_duration UInt32,
    signal_amplitude Decimal(5,2),
    signal_time DateTime
) ENGINE = MergeTree()
ORDER BY (user_id, prosthesis_type, signal_time);

-- Таблица агрегированных отчетов пользователей (для API отчетов)
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
ORDER BY (user_id, report_date);

-- Таблица для CDC данных (Change Data Capture из Kafka Connect)
CREATE TABLE IF NOT EXISTS user_reports_cdc (
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
ORDER BY (user_id, report_date);