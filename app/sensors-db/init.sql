-- Таблица данных ЭМГ сенсоров протезов
CREATE TABLE IF NOT EXISTS emg_sensor_data (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    prosthesis_type VARCHAR(100),
    muscle_group VARCHAR(100),
    signal_frequency INTEGER,
    signal_duration INTEGER,
    signal_amplitude DECIMAL(5,2),
    signal_time TIMESTAMP
);

-- Индекс для оптимизации запросов по user_id
CREATE INDEX IF NOT EXISTS idx_emg_sensor_data_user_id 
    ON emg_sensor_data(user_id);

-- Индекс для оптимизации запросов по prosthesis_type
CREATE INDEX IF NOT EXISTS idx_emg_sensor_data_prosthesis_type 
    ON emg_sensor_data(prosthesis_type);

-- Индекс для оптимизации запросов по signal_time
CREATE INDEX IF NOT EXISTS idx_emg_sensor_data_signal_time 
    ON emg_sensor_data(signal_time);

-- Составной индекс для часто используемых запросов
CREATE INDEX IF NOT EXISTS idx_emg_sensor_data_user_time 
    ON emg_sensor_data(user_id, signal_time);

\COPY emg_sensor_data(user_id, prosthesis_type, muscle_group, signal_frequency, signal_duration, signal_amplitude, signal_time) FROM '/docker-entrypoint-initdb.d/sensors.csv' WITH (FORMAT csv, HEADER true);
