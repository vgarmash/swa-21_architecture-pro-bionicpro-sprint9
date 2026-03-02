## Краткое описание реализованного решения

Реализован механизм Change Data Capture (CDC) для разделения потоков операций: запросы на выгрузку данных больше не влияют на транзакционные операции в CRM.

## Новая архитектура и компоненты

### Структура модуля CDC:
- `/app/cdc/` - корневая директория CDC модуля
- `/app/cdc/docker-compose.yaml` - описание сервисов Kafka, Zookeeper, Kafka Connect с Debezium
- `/app/cdc/debezium/` - конфигурации Debezium коннекторов
- `/app/cdc/clickhouse/` - SQL скрипты для ClickHouse

### Компоненты:
1. **Zookeeper** (порт 2181) - управление Kafka
2. **Kafka** (порт 9092) - брокер сообщений
3. **Kafka Connect + Debezium** (порт 8083) - захват изменений из PostgreSQL

## Инструкция по запуску

### 1. Запуск всей системы
```bash
cd /app
docker-compose up -d
```

### 2. Проверка готовности Kafka Connect
```bash
curl http://localhost:8083/
```

### 3. Регистрация Debezium коннекторов
```bash
# Коннектор для customers
curl -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:8083/connectors/ -d '{
  "name": "customers-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "tasks.max": "1",
    "database.hostname": "crm_db",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "crm_db",
    "table.include.list": "public.customers",
    "topic.prefix": "crm",
    "plugin.name": "pgoutput",
    "publication.name": "dbz_publication",
    "slot.name": "debezium_slot",
    "schema.history.internal.kafka.bootstrap.servers": "${env:BOOTSTRAP_SERVERS}",
    "schema.history.internal.kafka.topic": "schema-changes.customers",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false",
    "include.schema.changes": "true",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false",
    "transforms.unwrap.delete.handling.mode": "rewrite"
  }
}'

# Коннектор для emg_sensor_data
curl -X POST -H "Accept:application/json" -H "Content-Type:application/json" \
  http://localhost:8083/connectors/ -d '{
  "name": "sensors-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "tasks.max": "1",
    "database.hostname": "sensors-db",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "sensors-data",
    "table.include.list": "public.emg_sensor_data",
    "topic.prefix": "sensors",
    "plugin.name": "pgoutput",
    "publication.name": "dbz_publication_sensors",
    "slot.name": "debezium_slot_sensors",
    "schema.history.internal.kafka.bootstrap.servers": "${env:BOOTSTRAP_SERVERS}",
    "schema.history.internal.kafka.topic": "schema-changes.sensors",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false",
    "include.schema.changes": "true",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false",
    "transforms.unwrap.delete.handling.mode": "rewrite"
  }
}'
```

### 4. Создание таблиц в ClickHouse
Выполни SQL из `/app/cdc/clickhouse/init.sql` в ClickHouse (через порт 8123).

## API эндпоинты

### Существующие (Airflow ETL):
- `GET /api/v1/reports` - последний отчёт (с кешированием)
- `GET /api/v1/reports/{userId}` - отчёт пользователя
- `GET /api/v1/reports/{userId}/history` - история

### Новые (CDC):
- `GET /api/v1/reports/cdc` - последний отчёт из CDC
- `GET /api/v1/reports/cdc/{userId}` - отчёт пользователя из CDC
- `GET /api/v1/reports/cdc/{userId}/history` - история из CDC

## Проверка работоспособности

1. **Проверка Kafka топиков:**
```bash
docker exec -it app-kafka-1 kafka-topics --list --bootstrap-server localhost:9092
```

2. **Проверка коннекторов:**
```bash
curl http://localhost:8083/connectors/
```

3. **Проверка данных в ClickHouse:**
```sql
SELECT * FROM user_reports_cdc LIMIT 10;
```

4. **Тестирование API:**
```bash
curl http://localhost:8081/api/v1/reports/cdc/1
```

## Примечания

- CDC данные поступают в реальном времени из PostgreSQL
- Материализованное представление user_reports_cdc объединяет данные из customers и emg_sensor_data
- Существующий ETL процесс Airflow не модифицирован
- Все тесты в bionicpro-reports проходят