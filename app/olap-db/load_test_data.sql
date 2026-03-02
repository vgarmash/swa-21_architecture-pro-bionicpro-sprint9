-- Скрипт для загрузки тестовых данных из olap.csv
-- Выполняется вручную после создания таблиц:
-- docker exec app-olap_db clickhouse-client < /docker-entrypoint-initdb.d/load_test_data.sql

INSERT INTO emg_sensor_data
SELECT *
FROM file('/var/lib/clickhouse/user_files/olap.csv', 'CSV');

-- Проверка загруженных данных
SELECT count() as total_records FROM emg_sensor_data;
SELECT * FROM emg_sensor_data LIMIT 10;
