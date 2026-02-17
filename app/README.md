# Docker Compose для BionicPRO Auth System

## Архитектура системы

Система построена на основе BFF (Backend-for-Frontend) паттерна с использованием:
- **Keycloak 21.1** - OpenID Connect провайдер с поддержкой PKCE
- **BFF-сервис (bionicpro-auth)** - Spring Boot приложение на порту 8000
- **Redis** - хранение сессий
- **OpenLDAP 1.5.0** - LDAP User Federation
- **Яндекс ID** - внешний Identity Provider

---

## 🚀 Как запустить

### Требования

- Docker
- Docker Compose

### Структура проекта

```
.
├── docker-compose.yaml
├── .data/                          # Тома данных (создаются автоматически)
│   ├── postgres-keycloak-data/
│   ├── postgres-crm-data/
│   ├── clickhouse-data/
│   └── sensors-data/              # Данные PostgreSQL для сенсоров
├── keycloak/
│   └── realm-export.json           # Экспорт realm с PKCE и MFA
├── ldap/
│   └── config.ldif                 # Начальная LDIF-конфигурация
├── bionicpro-auth/                 # BFF-сервис
│   ├── Dockerfile
│   └── src/
├── crm-db/                         # PostgreSQL CRM
├── olap-db/                        # ClickHouse OLAP
├── sensors-db/                     # PostgreSQL для данных ЭМГ сенсоров
│   ├── init.sql                    # Скрипт инициализации БД
│   └── sensors.csv                 # Данные сенсоров
├── frontend/                       # React-приложение
└── redis/data/                    # Redis данные
```

### Порядок запуска

```bash
# 1. Запуск всех сервисов
docker-compose up -d

# 2. Ожидание инициализации (首次 запуск может занять 2-5 минут)
# Keycloak требует время для импорта realm и инициализации БД
echo "Ожидание инициализации сервисов..."
sleep 30

# 3. Проверка статуса сервисов
docker-compose ps
```

> ⚠️ **Важно**: При первом запуске дождитесь полного старта всех сервисов. Keycloak должен импортировать `realm-export.json` с настройками PKCE, MFA и LDAP User Federation.

---

## 🌐 URL сервисов и учётные данные

| Сервис           | URL                         | Логин / Пароль              |
|------------------|-----------------------------|------------------------------|
| **BionicPRO Auth (BFF)** | http://localhost:8000       | —                            |
| Keycloak         | http://localhost:8080       | `admin` / `admin`            |
| Realm            | `reports-realm`            | —                            |
| Frontend         | http://localhost:3000       | —                            |
| Redis            | localhost:6379              | —                            |
| OpenLDAP         | ldap://localhost:389        | `cn=admin,dc=example,dc=com` / `admin` |
| CRM DB           | localhost:5444 (PostgreSQL) | `crm_user` / `crm_password`  |
| OLAP DB          | http://localhost:8123       | — (ClickHouse HTTP)          |
| **Sensors DB**   | localhost:5436 (PostgreSQL) | `sensors_user` / `sensors_password` |
| MinIO Console    | http://localhost:9001       | `minio_user` / `minio_password` |

---

## 🔐 Конфигурация Keycloak

### Realm: reports-realm

При запуске автоматически импортируется из `keycloak/realm-export.json`:

### PKCE (OAuth 2.0 Proof Key for Code Exchange)
- **Method**: S256 (S256 Code Challenge Method)
- **Enabled**: true

### MFA/OTP (TOTP)
- **Алгоритм**: SHA256
- **Количество цифр**: 6
- **Период**: 30 секунд
- **Required Action**: CONFIGURE_TOTP

### OpenLDAP User Federation
- **Provider**: user-ldap
- **Vendor**: Other
- **Connection URL**: `ldap://openldap:389`
- **Bind DN**: `cn=admin,dc=example,dc=com`
- **Bind Credential**: `admin`
- **Users DN**: `dc=example,dc=com`
- **Search Scope**: Subtree
- **Timeout**: 2000ms

### Маппинг групп на роли
| LDAP Group | Keycloak Role |
|------------|---------------|
| cn=user | user |
| cn=administrator | administrator |
| cn=prothetic_user | prothetic_user |

### Яндекс ID Identity Provider
- **Alias**: yandex
- **Authorization URL**: https://oauth.yandex.ru/authorize
- **Token URL**: https://oauth.yandex.ru/token
- **User Info URL**: https://login.yandex.ru/info
- **Client ID**: (настраивается в Яндекс OAuth)
- **Client Secret**: (настраивается в Яндекс OAuth)
- **Consent Screen**: Enabled
- **Mapper**: email, name, first_name, last_name

---

## ⚙️ Конфигурация BFF-сервиса (bionicpro-auth)

### Переменные окружения

```yaml
bionicpro-auth:
  environment:
    SPRING_PROFILES_ACTIVE: dev
    REDIS_HOST: redis
    REDIS_PORT: 6379
    KEYCLOAK_SERVER_URL: http://keycloak:8080
    KEYCLOAK_REALM: reports-realm
    KEYCLOAK_CLIENT_ID: bionicpro-auth
    KEYCLOAK_REDIRECT_URI: http://localhost:8000/api/auth/callback
```

### Особенности
- **Порт**: 8000
- **Spring Session**: Redis с namespace `bionicpro:session`
- **Куки**: Secure, HttpOnly, SameSite=Lax
- **OAuth2 Client**: PKCE с методом S256

---

## ✅ Проверка работоспособности

### 1. Проверка статуса контейнеров

```bash
docker-compose ps
```

Все сервисы должны быть в статусе `Up` или `running`.

### 2. Проверка Keycloak

```bash
# Проверка доступности Keycloak
curl -s http://localhost:8080/health/ready

# Проверка realm
curl -s http://localhost:8080/realms/reports-realm | jq -r '.realm'
```

### 3. Проверка BFF-сервиса

```bash
# Проверка здоровья
curl -s http://localhost:8000/actuator/health

# Проверка статуса аутентификации
curl -s http://localhost:8000/api/auth/status
```

### 4. Проверка Redis

```bash
# Подключение к Redis
docker exec -it redis redis-cli

# Проверка сессий
KEYS bionicpro:session:*
```

### 5. Проверка LDAP

```bash
# Подключение к LDAP
docker exec -it openldap ldapsearch -x -H ldap://localhost:389 \
  -D "cn=admin,dc=example,dc=com" -w admin -b "dc=example,dc=com"
```

---

## 🔁 Управление сервисами

### Перезапуск

```bash
docker-compose restart
```

### Остановка (данные сохраняются)

```bash
docker-compose down
```

### Остановка с удалением томов (⚠️ данные будут потеряны!)

```bash
docker-compose down -v
```

### Просмотр логов

```bash
# Все сервисы
docker-compose logs -f

# Конкретный сервис
docker-compose logs -f keycloak
docker-compose logs -f bionicpro-auth
docker-compose logs -f redis
```

---

## 🔧 Устранение проблем

### Keycloak не стартует

```bash
# Проверка логов
docker-compose logs keycloak

# Увеличение времени ожидания
docker-compose up -d keycloak_db
# Ожидание готовности БД
sleep 10
docker-compose up -d keycloak
```

### BFF-сервис не подключается к Redis

```bash
# Проверка Redis
docker-compose logs redis

# Проверка сети
docker network ls
docker network inspect app_default
```

### LDAP User Federation не работает

1. Проверьте, что Keycloak может подключиться к LDAP:
   ```bash
   docker exec -it keycloak bash
   # В контейнере:
   nc -zv openldap 389
   ```

2. Проверьте настройки User Federation в Keycloak Admin Console:
   - Realm → User Federation → user-ldap → Test connection

---

## 🗄️ Базы данных

### Sensors DB (PostgreSQL)

**sensors-db** - PostgreSQL база данных для хранения данных ЭМГ сенсоров протезов.

| Параметр | Значение |
|----------|----------|
| Внешний порт | 5436 |
| Внутренний порт | 5432 |
| База данных | sensors-data |
| Пользователь | sensors_user |
| Пароль | sensors_password |
| Таблица | emg_sensor_data (структура аналогична olap_db в ClickHouse) |
| Персистентность | ./.data/sensors-data |

#### Файлы инициализации

- `app/sensors-db/init.sql` - SQL-скрипт для создания таблиц и начальных данных
- `app/sensors-db/sensors.csv` - CSV-файл с данными ЭМГ сенсоров

#### Подключение

```bash
# Подключение к БД через psql
psql -h localhost -p 5436 -U sensors_user -d sensors-data

# Через docker
docker exec -it sensors-db psql -U sensors_user -d sensors-data
```

---

## 📝 Примечания

- **Keycloak** запускается в режиме `start-dev` с автоматическим импортом `realm-export.json`
- **BFF-сервис** использует Spring Session с Redis для управления сессиями
- **PKCE** включён по умолчанию для всех OAuth2 клиентов
- **MFA/OTP** требует настройки пользователем через required action CONFIGURE_TOTP
- **Яндекс ID** требует предварительной регистрации приложения в Яндекс OAuth

---

🎉 **Готово!**