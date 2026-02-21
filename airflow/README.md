# BionicPRO Standalone Airflow Module

A standalone Apache Airflow deployment for the BionicPRO ETL pipeline, decoupled from the main monolith.

## Project Overview

The BionicPRO ETL pipeline is an Apache Airflow-based data processing system that extracts, transforms, and loads data from multiple source databases into a ClickHouse OLAP database for analytics and reporting.

### Purpose

- **Data Extraction**: Pull data from PostgreSQL databases (Sensors DB and CRM DB)
- **Data Transformation**: Aggregate and merge sensor readings with customer information
- **Data Loading**: Store processed data in ClickHouse for high-performance analytical queries

### Architecture

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

### Data Flow

1. **Extract**: Two parallel tasks fetch data from Sensors DB and CRM DB
2. **Transform**: Data is aggregated and merged based on user_id
3. **Load**: Final dataset is inserted into ClickHouse `user_reports` table

---

## Directory Structure

```
airflow/
├── dags/                      # DAG definitions
│   ├── .gitkeep
│   └── bionicpro_etl_dag.py  # Main ETL pipeline DAG
├── logs/                      # Task execution logs
│   └── .gitkeep
├── tests/                     # Unit tests
│   └── test_bionicpro_etl_dag.py
├── docker-compose.yaml        # Docker Compose configuration
├── requirements.txt           # Python dependencies
├── .env.example               # Environment variables template
└── .dockerignore              # Docker build exclusions
```

---

## Environment Variables

### Airflow Core

| Variable | Default | Description |
|----------|---------|-------------|
| `AIRFLOW__CORE__FERNET_KEY` | (required) | Encryption key for sensitive data. Generate with: `openssl rand -base64 32` |
| `AIRFLOW__CORE__EXECUTOR` | LocalExecutor | Executor type for task execution |
| `AIRFLOW_WEBSERVER_PORT` | 8080 | Host port for Airflow Web UI |
| `AIRFLOW_UID` | 50000 | User ID for Airflow container |

### Airflow Database

| Variable | Default | Description |
|----------|---------|-------------|
| `AIRFLOW_DB_HOST` | postgres | PostgreSQL container hostname |
| `AIRFLOW_DB_PORT` | 5432 | PostgreSQL port |
| `AIRFLOW_DB_USER` | airflow | Database username |
| `AIRFLOW_DB_PASSWORD` | (required) | Database password |
| `AIRFLOW_DB_NAME` | airflow | Database name |

### Source Databases (BionicPRO Integration)

| Variable | Default | Description |
|----------|---------|-------------|
| `SENSORS_DB_HOST` | sensors-db | Sensors PostgreSQL hostname |
| `SENSORS_DB_PORT` | 5432 | Sensors database port |
| `SENSORS_DB_PASSWORD` | (required) | Sensors database password |
| `CRM_DB_HOST` | crm-db | CRM PostgreSQL hostname |
| `CRM_DB_PORT` | 5432 | CRM database port |
| `CRM_DB_PASSWORD` | (required) | CRM database password |
| `OLAP_DB_HOST` | olap-db | ClickHouse hostname |
| `OLAP_DB_PORT` | 9000 | ClickHouse HTTP interface port |

### Admin Credentials

| Variable | Default | Description |
|----------|---------|-------------|
| `AIRFLOW_ADMIN_USER` | admin | Web UI admin username |
| `AIRFLOW_ADMIN_PASSWORD` | (required) | Web UI admin password |

---

## Docker Compose Usage

### Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+

### Quick Start

1. **Copy environment template**

   ```bash
   cp airflow/.env.example airflow/.env
   ```

2. **Generate Fernet key**

   ```bash
   # Linux/macOS
   openssl rand -base64 32
   # Windows (PowerShell)
   [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
   ```

3. **Update `.env` file** with generated keys and passwords

4. **Start Airflow services**

   ```bash
   cd airflow
   docker-compose up -d
   ```

5. **Access Web UI**

   - URL: http://localhost:8080
   - Username: `admin` (or configured value)
   - Password: (as set in `.env`)

### Service Management

| Command | Description |
|---------|-------------|
| `docker-compose up -d` | Start all services |
| `docker-compose down` | Stop all services (preserve data) |
| `docker-compose down -v` | Stop and remove volumes (reset DB) |
| `docker-compose restart` | Restart all services |
| `docker-compose logs -f` | Follow logs from all services |
| `docker-compose logs -f <service>` | Follow logs from specific service |

### Available Services

| Service | Port | Description |
|---------|------|-------------|
| `postgres` | 5432 (internal) | Airflow metadata database |
| `airflow-webserver` | 8080 | Web UI and REST API |
| `airflow-scheduler` | - | DAG scheduling engine |
| `airflow-triggerer` | - | Deferred task handler |

### Scaling Considerations

- Currently configured with `LocalExecutor` for single-node deployment
- For multi-node setup, switch to `CeleryExecutor` and add Redis/flower services
- Webserver can be scaled horizontally with load balancer

---

## Database Connections

### Connection Configuration

External database connections are configured via environment variables in the Docker Compose network.

#### Sensors Database (PostgreSQL)

```
Host: sensors-db
Port: 5432
Database: sensors-data
User: sensors_user
Password: <from SENSORS_DB_PASSWORD>
```

#### CRM Database (PostgreSQL)

```
Host: crm-db
Port: 5432
Database: crm_db
User: crm_user
Password: <from CRM_DB_PASSWORD>
```

#### OLAP Database (ClickHouse)

```
Host: olap-db
Port: 9000
Database: default
```

### Network Requirements

- Airflow must be on the same Docker network as source databases
- For external access (databases outside Docker), ensure firewall rules allow connections
- Connection URIs follow standard formats:
  - PostgreSQL: `postgresql+psycopg2://user:password@host:port/dbname`
  - ClickHouse: `clickhouse://host:port/database`

---

## Deployment Steps

### Step 1: Environment Configuration

```bash
# Navigate to airflow directory
cd airflow

# Copy example environment file
cp .env.example .env
```

### Step 2: Generate Required Keys

```bash
# Generate Fernet key
openssl rand -base64 32

# Generate Webserver secret key
openssl rand -base64 32
```

### Step 3: Update Environment Variables

Edit `.env` file with:
- Fernet key
- Webserver secret key
- Database passwords
- Admin credentials

### Step 4: Start Services

```bash
docker-compose up -d
```

### Step 5: Verify Health

```bash
# Check service status
docker-compose ps

# Check webserver health
curl http://localhost:8080/health

# Check scheduler health
curl http://localhost:8974/health
```

### Step 6: Access Airflow UI

1. Open browser: http://localhost:8080
2. Login with admin credentials
3. Verify `bionicpro_etl_pipeline` DAG is visible

### Troubleshooting

| Issue | Solution |
|-------|----------|
| Webserver not starting | Check logs: `docker-compose logs airflow-webserver` |
| Database connection failed | Verify `.env` credentials and network connectivity |
| DAG not visible | Ensure DAG file is in `dags/` directory |
| Permission errors | Check `AIRFLOW_UID` matches host user |

---

## DAG Documentation

### BionicPRO ETL Pipeline

**File**: [`dags/bionicpro_etl_dag.py`](dags/bionicpro_etl_dag.py)

**Schedule**: Daily at 02:00 UTC (`0 2 * * *`)

**Tags**: `bionicpro`, `etl`, `analytics`

### Task Descriptions

#### 1. extract_sensors_data

Extracts EMG sensor data from the Sensors PostgreSQL database.

- **Source Table**: `emg_sensor_data`
- **Extracted Fields**: user_id, prosthesis_type, muscle_group, signal_frequency, signal_duration, signal_amplitude, signal_time
- **Filter**: Data for execution date only
- **Output**: CSV file at `/tmp/sensors_data.csv`
- **Returns**: Number of records extracted

#### 2. extract_crm_data

Extracts customer information from the CRM PostgreSQL database.

- **Source Table**: `customers`
- **Extracted Fields**: id, name, email, age, gender, country
- **Output**: CSV file at `/tmp/crm_data.csv`
- **Returns**: Number of records extracted

#### 3. transform_and_merge_data

Aggregates sensor data and merges with customer information.

- **Aggregations**: mean/max/min signal amplitude, mean frequency, total duration
- **Join**: Merged on `user_id` with CRM data (left join)
- **Output**: CSV file at `/tmp/merged_data.csv`
- **Returns**: Number of records in final dataset

#### 4. load_to_olap

Loads processed data into ClickHouse OLAP database.

- **Target Table**: `user_reports`
- **Engine**: MergeTree
- **Partitioning**: By (user_id, report_date)
- **Returns**: Number of records inserted

### Task Dependencies

```
extract_sensors_data ─┐
                      ├─> transform_and_merge_data ─> load_to_olap
extract_crm_data ──────┘
```

### Default Arguments

- `owner`: bionicpro
- `depends_on_past`: false
- `retries`: 3
- `retry_delay`: 5 minutes
- `email_on_failure`: false

---

## Development

### Adding New DAGs

1. Create new Python file in `dags/` directory:

   ```python
   from airflow import DAG
   # ... your DAG definition
   ```

2. DAG will be automatically detected on next scheduler refresh (typically every 30 seconds)

3. In Airflow UI, unpause the new DAG

### Running Tests

```bash
# Run all tests
pytest airflow/tests/

# Run specific test file
pytest airflow/tests/test_bionicpro_etl_dag.py

# Run with verbose output
pytest -v airflow/tests/
```

### Installing Additional Python Packages

Edit `requirements.txt` and rebuild containers:

```bash
# Add package to requirements.txt
echo "new-package>=1.0.0" >> requirements.txt

# Rebuild containers
docker-compose up -d --build
```

Or install during runtime (not recommended for production):

```bash
docker-compose exec airflow-webserver pip install new-package
```

---

## Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| apache-airflow | >=2.8.0 | Core workflow engine |
| psycopg2-binary | >=2.9.0 | PostgreSQL connectivity |
| pandas | >=2.0.0 | Data processing |
| clickhouse-driver | >=0.2.0 | ClickHouse connectivity |
| pytest | >=7.0.0 | Unit testing |

---

## Related Documentation

- [BionicPRO Main README](../README.md)
- [Application Docker Compose](../app/docker-compose.yaml)
- [BionicPRO Architecture](../analysis/arch/initial/architecture.md)

---

## License

Internal BionicPRO project use only.
