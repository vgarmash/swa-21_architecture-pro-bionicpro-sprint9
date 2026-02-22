-- ClickHouse CDC Consumer Tables Schema
-- This script creates KafkaEngine tables to consume CDC events from Debezium
-- and MaterializedViews to transform and store data in the user_reports format

-- =============================================================================
-- Kafka Engine Tables for CDC Events Consumption
-- =============================================================================

-- Kafka table to consume customers CDC events from topic crm.public.customers
CREATE TABLE customers_cdc_kafka
(
    _topic String,
    _partition Int32,
    _offset Int64,
    _timestamp_ms Int64,
    -- Customer fields from Debezium CDC
    id Int32,
    user_id UInt32,
    name String,
    email String,
    age UInt8,
    gender String,
    country String,
    prosthesis_type String,
    created_at DateTime
)
ENGINE = Kafka()
SETTINGS
    kafka_broker_list = 'kafka:29092',
    kafka_topic_list = 'crm.public.customers',
    kafka_group_name = 'clickhouse-cdc-consumers',
    kafka_format = 'JSONEachRow',
    kafka_max_block_size = 65536,
    kafka_commit_every_batch = 1;

-- Kafka table to consume emg_sensor_data CDC events from topic sensors.public.emg_sensor_data
CREATE TABLE sensors_cdc_kafka
(
    _topic String,
    _partition Int32,
    _offset Int64,
    _timestamp_ms Int64,
    -- EMG sensor data fields from Debezium CDC
    id Int32,
    user_id UInt32,
    session_id String,
    signal_amplitude Float32,
    signal_frequency Float32,
    muscle_group String,
    session_duration Float32,
    recorded_at DateTime
)
ENGINE = Kafka()
SETTINGS
    kafka_broker_list = 'kafka:29092',
    kafka_topic_list = 'sensors.public.emg_sensor_data',
    kafka_group_name = 'clickhouse-cdc-consumers',
    kafka_format = 'JSONEachRow',
    kafka_max_block_size = 65536,
    kafka_commit_every_batch = 1;

-- =============================================================================
-- Target Tables for Aggregated CDC Data (user_reports format)
-- =============================================================================

-- Table to store aggregated customer data with sensor statistics
CREATE TABLE user_reports_cdc
(
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
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (user_id, report_date)
SETTINGS index_granularity = 8192;

-- =============================================================================
-- MaterializedView to Process and Join CDC Data
-- =============================================================================

-- MaterializedView that consumes customers CDC events and joins with aggregated sensor data
CREATE MATERIALIZED VIEW user_reports_cdc_mv
ENGINE = SummingMergeTree()
ORDER BY (user_id, report_date)
POPULATE AS
SELECT
    c.user_id,
    toDate(c.created_at) AS report_date,
    count(s.id) AS total_sessions,
    avg(s.signal_amplitude) AS avg_signal_amplitude,
    max(s.signal_amplitude) AS max_signal_amplitude,
    min(signal_amplitude) AS min_signal_amplitude,
    avg(s.signal_frequency) AS avg_signal_frequency,
    sum(s.session_duration) / 3600.0 AS total_usage_hours,
    c.prosthesis_type AS prosthesis_type,
    s.muscle_group AS muscle_group,
    c.name AS customer_name,
    c.email AS customer_email,
    c.age AS customer_age,
    c.gender AS customer_gender,
    c.country AS customer_country,
    now() AS created_at
FROM customers_cdc_kafka c
LEFT JOIN (
    SELECT
        user_id,
        id,
        signal_amplitude,
        signal_frequency,
        muscle_group,
        session_duration
    FROM sensors_cdc_kafka
) s ON c.user_id = s.user_id
GROUP BY
    c.user_id,
    toDate(c.created_at),
    c.prosthesis_type,
    s.muscle_group,
    c.name,
    c.email,
    c.age,
    c.gender,
    c.country;

-- =============================================================================
-- Alternative: Buffer tables for raw CDC data before aggregation
-- =============================================================================

-- Buffer table for customers raw data
CREATE TABLE customers_cdc_buffer
(
    id Int32,
    user_id UInt32,
    name String,
    email String,
    age UInt8,
    gender String,
    country String,
    prosthesis_type String,
    created_at DateTime,
    _timestamp DateTime DEFAULT now()
)
ENGINE = MergeTree()
ORDER BY (user_id, id)
SETTINGS index_granularity = 8192;

-- Buffer table for sensor raw data
CREATE TABLE sensors_cdc_buffer
(
    id Int32,
    user_id UInt32,
    session_id String,
    signal_amplitude Float32,
    signal_frequency Float32,
    muscle_group String,
    session_duration Float32,
    recorded_at DateTime,
    _timestamp DateTime DEFAULT now()
)
ENGINE = MergeTree()
ORDER BY (user_id, id)
SETTINGS index_granularity = 8192;

-- MaterializedView to populate customers buffer
CREATE MATERIALIZED VIEW customers_cdc_buffer_mv
ENGINE = MergeTree()
ORDER BY (user_id, id)
POPULATE AS
SELECT
    id,
    user_id,
    name,
    email,
    age,
    gender,
    country,
    prosthesis_type,
    created_at,
    now() AS _timestamp
FROM customers_cdc_kafka;

-- MaterializedView to populate sensors buffer
CREATE MATERIALIZED VIEW sensors_cdc_buffer_mv
ENGINE = MergeTree()
ORDER BY (user_id, id)
POPULATE AS
SELECT
    id,
    user_id,
    session_id,
    signal_amplitude,
    signal_frequency,
    muscle_group,
    session_duration,
    recorded_at,
    now() AS _timestamp
FROM sensors_cdc_kafka;

-- =============================================================================
-- Notes:
-- =============================================================================
-- 1. Topics must exist in Kafka before creating KafkaEngine tables
-- 2. The JSONEachRow format is used for Debezium CDC events with unwrapped values
-- 3. Adjust kafka_max_block_size based on your throughput requirements
-- 4. user_reports_cdc uses ReplacingMergeTree to handle upserts based on created_at
-- 5. Use ALTER TABLE MATERIALIZED VIEW ... REFRESH to manually refresh MV if needed
