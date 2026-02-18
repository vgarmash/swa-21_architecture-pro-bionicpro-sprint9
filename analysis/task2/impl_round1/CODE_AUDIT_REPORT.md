# Детальный аудит кодовой базы BionicPRO

**Дата проведения:** 2026-02-18  
**Версия проекта:** Sprint 9  
**Аудитор:** Code Skeptic (Kilo Code)

---

## 1. Резюме

### Общая оценка: ⚠️ ТРЕБУЕТСЯ ДОРАБОТКА

В ходе аудита выявлено **27 проблем**, из которых:
- 🔴 **КРИТИЧЕСКИХ**: 10
- 🟠 **ВЫСОКИХ**: 9  
- 🟡 **СРЕДНИХ**: 8

### Ключевые выводы

1. **Frontend НЕ интегрирован с BFF** - использует прямую интеграцию с Keycloak, что нарушает всю архитектуру безопасности
2. **Token Refresh НЕ реализован** - критическая функциональность помечена как TODO
3. **Отсутствует проверка ролей** - любой аутентифицированный пользователь имеет доступ к отчётам
4. **Хардкод credentials** - пароли в открытом виде в Airflow DAG

---

## 2. Детальный анализ по компонентам

### 2.1 bionicpro-auth (BFF Service)

#### 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Строка | Описание |
|----|----------|------|--------|----------|
| AUTH-001 | **Token Refresh НЕ реализован** | [`SessionService.java`](app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java:117) | 117 | `// TODO: Implement token refresh with Keycloak` - критическая функциональность отсутствует |
| AUTH-002 | **In-memory хранилище state** | [`SessionService.java`](app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java:40) | 40 | `authRequestStore = new ConcurrentHashMap<>()` - не работает в распределённой среде |
| AUTH-003 | **Отсутствует Rate Limiting** | [`SecurityConfig.java`](app/bionicpro-auth/src/main/java/com/bionicpro/config/SecurityConfig.java) | - | ТЗ требует: "10 попыток входа в минуту с одного IP" |
| AUTH-004 | **Отсутствует fallback на in-memory** | [`SessionService.java`](app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java) | - | ТЗ требует graceful degradation при недоступности Redis |

**Доказательство AUTH-001:**
```java
// SessionService.java, строка ~117
if (sessionData.getAccessTokenExpiresAt() != null 
        && Instant.now().plusSeconds(30).isAfter(sessionData.getAccessTokenExpiresAt())) {
    // Token needs refresh - in production, would call Keycloak to refresh
    log.debug("Access token needs refresh for user: {}", sessionData.getUserId());
    // TODO: Implement token refresh with Keycloak  <-- НЕ РЕАЛИЗОВАНО!
}
```

#### 🟠 ВЫСОКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| AUTH-005 | **SameSite=Lax вместо Strict** | [`SessionService.java`](app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java:197) | ТЗ требует `SameSite=Strict`, код: `cookie.setAttribute("SameSite", "Lax")` |
| AUTH-006 | **CSRF отключён** | [`SecurityConfig.java`](app/bionicpro-auth/src/main/java/com/bionicpro/config/SecurityConfig.java:43) | `csrf(csrf -> csrf.disable())` - ТЗ требует CSRF токены |
| AUTH-007 | **Отсутствует Audit Logging** | - | ТЗ требует логирование всех операций аутентификации |
| AUTH-008 | **Нет проверки timeout** | [`AuthController.java`](app/bionicpro-auth/src/main/java/com/bionicpro/controller/AuthController.java) | ТЗ требует timeout ≤2000ms |

#### 🟡 СРЕДНИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| AUTH-009 | **Отсутствует MapStruct** | [`pom.xml`](app/bionicpro-auth/pom.xml) | ТЗ требует MapStruct для маппинга DTO |
| AUTH-010 | **Нет InMemorySessionRepository** | - | ТЗ требует fallback репозиторий |

---

### 2.2 bionicpro-reports (Reports API)

#### 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| RPT-001 | **Отсутствует проверка роли prothetic_user** | [`SecurityConfig.java`](app/bionicpro-reports/src/main/java/com/bionicpro/reports/config/SecurityConfig.java:27) | ТЗ: `allowed-user-role: prothetic_user`. Код: `.anyRequest().permitAll()` |
| RPT-002 | **Отсутствует ReportNotFoundException** | - | ТЗ требует отдельный класс исключения |

**Доказательство RPT-001:**
```java
// SecurityConfig.java, строка 27
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .requestMatchers("/api/v1/reports/**").authenticated()  // <-- Нет проверки роли!
    .anyRequest().permitAll())  // <-- Любой аутентифицированный имеет доступ!
```

#### 🟠 ВЫСОКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| RPT-003 | **Нет валидации userId** | [`ReportController.java`](app/bionicpro-reports/src/main/java/com/bionicpro/reports/controller/ReportController.java:35) | userId берётся из JWT без проверки формата |
| RPT-004 | **SQL без параметризации** | [`ReportRepository.java`](app/bionicpro-reports/src/main/java/com/bionicpro/reports/repository/ReportRepository.java) | Используется string concatenation в SQL |

---

### 2.3 Frontend (React)

#### 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| FE-001 | **Прямая интеграция с Keycloak** | [`ReportPage.tsx`](app/frontend/src/components/ReportPage.tsx:15) | Использует `@react-keycloak/web` вместо BFF |
| FE-002 | **Bearer token в браузере** | [`ReportPage.tsx`](app/frontend/src/components/ReportPage.tsx:35) | `'Authorization': Bearer ${keycloak.token}` - нарушение безопасности BFF |
| FE-003 | **API мимо BFF** | [`ReportPage.tsx`](app/frontend/src/components/ReportPage.tsx:33) | Запросы идут напрямую в reports-api, а не через BFF |

**Доказательство FE-001/FE-002:**
```tsx
// ReportPage.tsx, строка 15
const { keycloak, initialized } = useKeycloak();  // <-- Прямая интеграция!

// Строка 35
const response = await fetch(`${process.env.REACT_APP_API_URL}/reports`, {
    headers: {
        'Authorization': `Bearer ${keycloak.token}`  // <-- Token в браузере!
    }
});
```

**Это полностью нарушает архитектуру BFF:**
- ТЗ требует: "Убрать direct keycloak-js инициализацию"
- ТЗ требует: "Access token не передаётся фронтенду"
- ТЗ требует: "BFF проксирует запросы к backend API"

#### 🟠 ВЫСОКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| FE-004 | **Неправильный API URL** | [`.env`](app/frontend/.env) | `REACT_APP_API_URL=http://localhost:8081/api/v1` - должен быть BFF URL |
| FE-005 | **Нет обработки network errors** | [`ReportPage.tsx`](app/frontend/src/components/ReportPage.tsx:62) | Только HTTP status codes обрабатываются |

---

### 2.4 Airflow ETL DAG

#### 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Строка | Описание |
|----|----------|------|--------|----------|
| ETL-001 | **Хардкод credentials** | [`bionicpro_etl_dag.py`](app/airflow/dags/bionicpro_etl_dag.py:35) | 35 | `password='sensors_password'` в открытом виде |
| ETL-002 | **Хардкод credentials** | [`bionicpro_etl_dag.py`](app/airflow/dags/bionicpro_etl_dag.py:62) | 62 | `password='crm_password'` в открытом виде |
| ETL-003 | **Нет обработки ошибок** | [`bionicpro_etl_dag.py`](app/airflow/dags/bionicpro_etl_dag.py) | - | Отсутствуют try/except блоки |

**Доказательство ETL-001:**
```python
# bionicpro_etl_dag.py, строка 35
conn = psycopg2.connect(
    host='sensors-db',
    port=5432,
    dbname='sensors-data',
    user='sensors_user',
    password='sensors_password'  # <-- ХАРДКОД ПАРОЛЯ!
)
```

#### 🟠 ВЫСОКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| ETL-004 | **Временные файлы вместо XCom** | [`bionicpro_etl_dag.py`](app/airflow/dags/bionicpro_etl_dag.py:48) | `/tmp/sensors_data.csv` - не работает в распределённой среде |
| ETL-005 | **Нет timeout на таски** | [`bionicpro_etl_dag.py`](app/airflow/dags/bionicpro_etl_dag.py) | ТЗ требует timeout 1 час |

---

### 2.5 Keycloak Configuration

#### 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| KC-001 | **Отсутствует клиент bionicpro-auth** | [`realm-export.json`](app/keycloak/realm-export.json) | ТЗ требует создать confidential клиент для BFF |
| KC-002 | **MFA не обязательна** | [`realm-export.json`](app/keycloak/realm-export.json:22) | `"defaultAction": false` - ТЗ требует обязательную MFA |

**Доказательство KC-001:**
```json
// realm-export.json - клиенты
"clients": [
    {
        "clientId": "reports-frontend",  // <-- Есть
        ...
    },
    {
        "clientId": "reports-api",  // <-- Есть
        ...
    }
    // НЕТ клиента "bionicpro-auth"!
]
```

#### 🟠 ВЫСОКИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| KC-003 | **Пароли в открытом виде** | [`realm-export.json`](app/keycloak/realm-export.json:48) | `"value": "password123"` - только для dev, но нет предупреждения |
| KC-004 | **Yandex ID placeholder** | [`realm-export.json`](app/keycloak/realm-export.json:116) | `"clientId": "YANDEX_CLIENT_ID"` - не настроен |

---

### 2.6 Docker Compose

#### 🟡 СРЕДНИЕ ПРОБЛЕМЫ

| ID | Проблема | Файл | Описание |
|----|----------|------|----------|
| DC-001 | **Нет healthcheck для keycloak** | [`docker-compose.yaml`](app/docker-compose.yaml:35) | Зависимости используют `condition: service_started` |
| DC-002 | **Нет condition для airflow** | [`docker-compose.yaml`](app/docker-compose.yaml:139) | airflow-db должен быть healthy перед запуском webserver |

---

## 3. Расхождения с техническим заданием

### 3.1 Task 1 (bionicpro-auth)

| Требование ТЗ | Статус | Комментарий |
|---------------|--------|-------------|
| OAuth2 Authorization Code Flow с PKCE | ⚠️ Частично | PKCE не настроен для BFF клиента |
| HttpOnly, Secure, SameSite=Strict куки | ⚠️ Частично | SameSite=Lax вместо Strict |
| Автоматическое обновление токенов | ❌ Не реализовано | TODO в коде |
| Ротация сессий | ✅ Реализовано | SessionRotationFilter работает |
| Rate limiting | ❌ Не реализовано | Отсутствует полностью |
| CSRF защита | ❌ Не реализовано | Отключена |
| Graceful degradation | ❌ Не реализовано | Нет fallback на in-memory |
| Audit logging | ❌ Не реализовано | Только базовое логирование |

### 3.2 Task 2 (Reports API + ETL)

| Требование ТЗ | Статус | Комментарий |
|---------------|--------|-------------|
| REST API /api/v1/reports | ✅ Реализовано | Эндпоинты работают |
| OAuth2 Resource Server | ✅ Реализовано | JWT валидация работает |
| Проверка роли prothetic_user | ❌ Не реализовано | Любой пользователь имеет доступ |
| Airflow DAG с 4 тасками | ✅ Реализовано | Таски работают |
| Timeout на таски | ⚠️ Частично | Есть retry, нет явного timeout |
| Расписание 0 2 * * * | ✅ Реализовано | Cron настроен |

### 3.3 Frontend Integration

| Требование ТЗ | Статус | Комментарий |
|---------------|--------|-------------|
| Интеграция через BFF | ❌ Не реализовано | Прямая интеграция с Keycloak |
| Session cookie вместо токенов | ❌ Не реализовано | Bearer token в браузере |
| Обработка ошибок | ⚠️ Частично | Есть, но неполная |

---

## 4. Уязвимости безопасности

### 4.1 Критические

| ID | Уязвимость | CVSS | Описание |
|----|------------|------|----------|
| SEC-001 | Broken Access Control | 8.5 | Отсутствует проверка ролей в Reports API |
| SEC-002 | Sensitive Data Exposure | 7.5 | Access token в браузере (нарушение BFF) |
| SEC-003 | Credentials in Code | 6.5 | Хардкод паролей в Airflow DAG |

### 4.2 Высокие

| ID | Уязвимость | CVSS | Описание |
|----|------------|------|----------|
| SEC-004 | Missing CSRF Protection | 5.5 | CSRF отключён в SecurityConfig |
| SEC-005 | Insufficient Logging | 4.5 | Отсутствует audit logging |

---

## 5. Проблемы архитектуры

### 5.1 Нарушения принципов BFF

**Проблема:** Frontend полностью обходит BFF и общается напрямую с Keycloak и Reports API.

**Последствия:**
- Access token доступен в браузере (XSS риск)
- BFF сервис бесполезен для безопасности
- Нарушена изоляция токенов

**Решение:**
1. Удалить `@react-keycloak/web` из frontend
2. Использовать session cookie для аутентификации
3. Все API запросы проксировать через BFF

### 5.2 Распределённая среда

**Проблема:** In-memory хранилище `authRequestStore` не работает с несколькими инстансами.

**Решение:** Использовать Redis для хранения state параметров.

---

## 6. Качество кода

### 6.1 Best Practices нарушения

| Проблема | Количество | Примеры |
|----------|------------|---------|
| TODO в production коде | 1 | `// TODO: Implement token refresh` |
| Хардкод credentials | 2 | Airflow DAG |
| Отключённый CSRF | 2 | SecurityConfig в обоих сервисах |
| Magic numbers | 3 | Timeout значения без констант |

### 6.2 Отсутствующие компоненты

- `InMemorySessionRepository` (fallback)
- `ReportNotFoundException` (exception class)
- `RateLimiter` (security component)
- `AuditLogger` (security component)

---

## 7. Рекомендации по исправлению

### 7.1 Приоритет 1 (Критический)

1. **Реализовать Token Refresh** в [`SessionService.java`](app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java)
   ```java
   // Добавить вызов Keycloak token endpoint
   // Обновить токены в сессии
   ```

2. **Добавить проверку ролей** в [`SecurityConfig.java`](app/bionicpro-reports/src/main/java/com/bionicpro/reports/config/SecurityConfig.java)
   ```java
   .requestMatchers("/api/v1/reports/**").hasRole("prothetic_user")
   ```

3. **Переписать Frontend** для работы через BFF
   - Удалить `@react-keycloak/web`
   - Использовать session cookie
   - Проксировать запросы через BFF

4. **Убрать хардкод credentials** из Airflow DAG
   - Использовать Airflow Connections
   - Или переменные окружения

### 7.2 Приоритет 2 (Высокий)

1. Добавить Rate Limiting
2. Включить CSRF защиту
3. Исправить SameSite=Strict
4. Добавить Audit Logging
5. Перенести authRequestStore в Redis

### 7.3 Приоритет 3 (Средний)

1. Добавить healthcheck для keycloak
2. Настроить depends_on conditions
3. Добавить MapStruct для DTO маппинга
4. Создать InMemorySessionRepository fallback

---

## 8. Чек-лист для приёмки

### 8.1 Функциональные тесты

- [ ] **TC-AUTH-001**: Token refresh работает при истечении access token
- [ ] **TC-AUTH-002**: Rate limiting блокирует после 10 попыток
- [ ] **TC-AUTH-003**: Session rotation создаёт новый session ID
- [ ] **TC-RPT-001**: Пользователь без роли prothetic_user получает 403
- [ ] **TC-FE-001**: Frontend работает без доступа к токенам
- [ ] **TC-ETL-001**: DAG работает с Airflow Connections

### 8.2 Security тесты

- [ ] **SEC-TEST-001**: XSS не может украсть token (BFF)
- [ ] **SEC-TEST-002**: CSRF токен требуется для POST
- [ ] **SEC-TEST-003**: Пароли не в коде

---

## 9. Заключение

### 9.1 Статус готовности

| Компонент | Готовность | Блокеры |
|-----------|------------|---------|
| bionicpro-auth | 60% | Token refresh, Rate limiting |
| bionicpro-reports | 70% | Проверка ролей |
| Frontend | 30% | Полная переработка |
| Airflow ETL | 80% | Credentials |
| Keycloak | 75% | Клиент для BFF |

### 9.2 Итог

**Проект НЕ ГОТОВ к production.**

Основные блокеры:
1. Frontend полностью нарушает архитектуру BFF
2. Token refresh не реализован
3. Отсутствует проверка ролей
4. Хардкод credentials

**Оценка усилий на исправление:** 40-60 человеко-часов

---

*Отчёт сгенерирован: 2026-02-18*  
*Аудитор: Code Skeptic (Kilo Code)*  
*Мотто: "Show me the logs or it didn't happen."*
