# План разработки сервиса отчётов BionicPRO

## 1. Обзор архитектуры решения

### 1.1 Цель
Разработка сервиса отчётов для компании BionicPRO, позволяющего пользователям получать данные о работе своего протеза в виде отчётов.

### 1.2 Компоненты системы

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BionicPRO Report Service                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │   Frontend   │───▶│ BFF Service │───▶│Reports API  │───▶│  ClickHouse │  │
│  │  (React)    │    │(bionicpro-  │    │ (New)       │    │   (OLAP)    │  │
│  │             │    │   auth)     │    │             │    │             │  │
│  └─────────────┘    └──────┬──────┘    └──────┬──────┘    └─────────────┘  │
│                           │                   │                              │
│                           │                   │                              │
│                           ▼                   ▼                              │
│                    ┌─────────────┐    ┌─────────────┐                      │
│                    │  Keycloak    │    │   Apache    │                      │
│                    │   (SSO)      │    │   Airflow   │                      │
│                    └─────────────┘    └──────┬──────┘                      │
│                                               │                              │
│                    ┌─────────────┐            │                              │
│                    │   Sensors    │◀───────────┤                              │
│                    │     DB       │            │                              │
│                    │(PostgreSQL)  │            ▼                              │
│                    └─────────────┘    ┌─────────────┐    ┌─────────────┐    │
│                                       │CRM Database │    │  MinIO/S3   │    │
│                                       │(PostgreSQL) │    │ (Storage)   │    │
│                                       └─────────────┘    └─────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Источники данных

| База данных | Технология | Назначение |
|-------------|------------|------------|
| Sensors DB | PostgreSQL (port 5436) | Данные ЭМГ-сенсоров протезов |
| CRM DB | PostgreSQL (port 5444) | Данные о клиентах |
| OLAP DB | ClickHouse (port 8123) | Аналитическое хранилище |

### 1.4 Существующая инфраструктура

- **Keycloak** (port 8080) - Identity Provider с поддержкой PKCE и MFA
- **BFF Service** (bionicpro-auth, port 8000) - Spring Boot OAuth2 прокси
- **Redis** (port 6379) - хранилище сессий
- **Frontend** (React, port 3000) - SPA приложение

---

## 2. Функциональные требования

### 2.1 Основные требования

1. **ETL-процесс на Apache Airflow**
   - Извлечение данных из sensors-db (PostgreSQL)
   - Извлечение данных из CRM-db (PostgreSQL)
   - Трансформация и объединение данных
   - Загрузка в OLAP-базу (ClickHouse)

2. **API сервиса отчётов**
   - Java/Spring Boot REST API
   - Эндпоинт: `GET /reports`
   - Возвращает подготовленный отчёт по заданному пользователю
   - Запросы к OLAP-базе без сложных вычислений в реальном времени

3. **Безопасность**
   - Ограничение доступа: пользователь может получить только свой отчёт
   - Интеграция с Keycloak для аутентификации
   - Токенная авторизация (Bearer token)

4. **Frontend интеграция**
   - Кнопка получения отчёта
   - Вызов API генерации отчётов

---

## 3. Требования к нефункциональным характеристикам

### 3.1 Масштабируемость
- Горизонтальное масштабирование Airflow workers
- Stateless API сервис для возможности балансировки нагрузки
- Connection pooling для работы с БД

### 3.2 Производительность
- Время отклика API < 2 секунд для типичных запросов
- Асинхронная генерация отчётов через Airflow DAG
- Кэширование часто запрашиваемых отчётов

### 3.3 Надёжность
- Retry механизм в DAG для обработки сбоев
- Логирование всех операций
- Мониторинг через Airflow UI

### 3.4 Простота реализации
- Подключение к существующей OLAP базе ClickHouse
- Минимальные изменения в существующую инфраструктуру
- Использование проверенных технологий (Java/Spring Boot)

---

## 4. Структура витрины данных

### 4.1 Таблица user_reports (ClickHouse)

```sql
CREATE TABLE user_reports (
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
    created_at DateTime DEFAULT now()
) ENGINE = MergeTree()
ORDER BY (user_id, report_date);
```

### 4.2 Агрегации по пользователю
- Количество сессий использования
- Средняя/максимальная/минимальная амплитуда сигнала
- Средняя частота сигнала
- Общее время использования (часы)
- Тип протеза
- Группа мышц

---

## 5. Подзадачи

### Задача 1: Apache Airflow DAG
- [ ] Настройка Apache Airflow в docker-compose
- [ ] Создание DAG для ETL процесса
- [ ] Извлечение данных из sensors-db
- [ ] Извлечение данных из CRM-db
- [ ] Трансформация и объединение данных
- [ ] Загрузка в ClickHouse
- [ ] Настройка расписания запуска

### Задача 2: Reports API Service
- [ ] Создание нового Spring Boot микросервиса
- [ ] Подключение к ClickHouse
- [ ] Реализация эндпоинта GET /reports
- [ ] Интеграция с Keycloak
- [ ] Реализация авторизации (только свой отчёт)

### Задача 3: Frontend интеграция
- [ ] Добавление кнопки получения отчёта
- [ ] Вызов API эндпоинта
- [ ] Обработка ответа и загрузка файла

### Задача 4: Тестирование и документирование
- [ ] Unit тесты для API
- [ ] Интеграционные тесты
- [ ] Документация API
- [ ] Документация развёртывания

---

## 6. Технологический стек

| Компонент | Технология | Версия |
|-----------|------------|--------|
| ETL оркестратор | Apache Airflow | 2.x |
| API фреймворк | Spring Boot | 3.x |
| Язык | Java | 17+ |
| OLAP база | ClickHouse | 23.x |
| Сборщик | Maven | 3.x+ |
| Контейнеризация | Docker | Latest |

---

## 7. API контракт

### 7.1 Эндпоинт получения отчёта

**Request:**
```
GET /api/v1/reports
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "userId": 123,
  "reportDate": "2024-01-15",
  "totalSessions": 45,
  "avgSignalAmplitude": 0.75,
  "maxSignalAmplitude": 1.2,
  "minSignalAmplitude": 0.3,
  "avgSignalFrequency": 150.5,
  "totalUsageHours": 12.5,
  "prosthesisType": "upper_limb",
  "muscleGroup": "biceps"
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

**Response (403 Forbidden):**
```json
{
  "error": "Forbidden",
  "message": "You can only access your own report"
}
```

---

## 8. Следующие шаги

Перейдите к документу [02_etl_airflow_dag.md](02_etl_airflow_dag.md) для детального плана реализации ETL-процесса на Apache Airflow.