# Docker Compose для Keycloak + CRM + OLAP + LDAP

## 🚀 Как запустить

Убедитесь, что установлены:

- Docker
- Docker Compose

Клонируйте/поместите проект и убедитесь, что структура папок соответствует:

```
.
├── docker-compose.yml
├── postgres-keycloak-data/     # будет создан автоматически
├── postgres-crm-data/          # будет создан автоматически
├── keycloak/
│   └── realm-export.json      # экспорт realm для импорта
├── ldap/
│   └── config.ldif            # начальная LDIF-конфигурация
├── crm-db/                     # SQL-скрипты инициализации CRM DB
├── olap-db/                    # скрипты инициализации ClickHouse
├── frontend/                   # React-приложение
└── Dockerfile
```

Запустите сервисы:

```bash
docker-compose up -d
```

> ⚠️ Первый запуск может занять несколько минут — особенно `olap_db` (ClickHouse) ожидает healthcheck.

---

## 🌐 Как открыть UI

| Сервис           | URL                         | Логин / Пароль (если есть)             |
|------------------|-----------------------------|----------------------------------------|
| Keycloak         | http://localhost:8080       | `admin` / `admin`                      |
| Realm            |                             | `reports-realm`                        |
| Client           |                             | `reports-frontend`                     |
| Frontend (React) | http://localhost:3000       | —                                      |
| MinIO Console    | http://localhost:9001       | `minio_user` / `minio_password`        |
| MinIO API        | http://localhost:9000       | —                                      |
| OpenLDAP         | ldap://localhost:389        | `cn=admin,dc=example,dc=com` / `admin` |
| CRM DB           | localhost:5444 (PostgreSQL) | `crm_user` / `crm_password`            |
| OLAP DB          | http://localhost:8123       | — (ClickHouse HTTP)                    |
| OLAP DB Port     | localhost:9431              | —                                      |

---

## 🔁 Перезапуск и остановка

**Перезапустить:**

```bash
docker-compose restart
```

**Остановить (без удаления данных):**

```bash
docker-compose down
```

**Остановить + удалить все тома** (⚠️ данные будут утеряны!):

```bash
docker-compose down -v
```

---

## 📝 Примечания

- Keycloak запускается в режиме `start-dev` и автоматически импортирует `realm-export.json` из `./keycloak/`.
- Для корректной работы фронтенда убедитесь, что:
    - backend доступен по `http://localhost:8000` (не включён в compose — возможно, внешний API).
    - `realm-export.json` содержит realm `reports-realm` и клиент `reports-frontend`.
- ClickHouse healthcheck проверяет доступность через `clickhouse-client` (в контейнере). Если зависает — проверьте
  скрипты в `./olap-db/`.

---

🎉 **Готово!**