Ниже представлен обобщённый план действий для команды разработчиков BionicPRO. Документ объединяет все уникальные проблемы из трёх предоставленных отчётов, группирует их по компонентам и приоритетам, а также даёт конкретные технические рекомендации.

---

# 🚀 BionicPRO — Обобщённый план действий (Пост-аудит)

**Дата:** 2026-02-18
**Цель:** Устранение критических, высокоприоритетных и архитектурных проблем, выявленных в ходе комплексного аудита кодовой базы.

## 1. Условные обозначения

*   **🔴 P0 (Критический):** Блокирует релиз. Требует немедленного решения (1-2 дня).
*   **🟠 P1 (Высокий):** Нарушает безопасность, архитектуру или ключевую функциональность. Должен быть исправлен до следующего спринта (3-5 дней).
*   **🟡 P2 (Средний):** Улучшает качество кода, тестирование и стабильность. План на 1-2 недели.
*   **🟢 P3 (Низкий):** Улучшения и технический долг. Можно выполнять в фоне.

---

## 2. 🔴 Фаза 1: НЕМЕДЛЕННЫЕ ДЕЙСТВИЯ (P0 — Critical)

### 2.1. Безопасность: Секреты и Доступ

*   **[AUTH-007, ETL-001, ETL-002, SEC-003, CQ-014, CQ-015] Убрать хардкодные credentials:**
    *   **Где:** `bionicpro_etl_dag.py`
    *   **Что делать:**
        1.  Удалить пароли (`sensors_password`, `crm_password`) из кода DAG.
        2.  Создать **Airflow Connections** (`sensors_db`, `crm_db`) через Web UI или CLI.
        3.  Использовать `PostgresHook` для получения параметров подключения.
*   **[DC-003, KC-003, SEC-001, SEC-002, SEC-010, SEC-011, SEC-012, SEC-013] Вынести все secrets из конфигураций:**
    *   **Где:** `docker-compose.yaml`, `realm-export.json`, `config.ldif`, `.env` файлы.
    *   **Что делать:**
        1.  Переместить все пароли (admin/admin, пароли БД, client secrets) в переменные окружения в файле `.env`.
        2.  Настроить Docker Compose для чтения переменных из этого файла.
        3.  Для production рассмотреть HashiCorp Vault или Docker Secrets.
        4.  **Никогда не коммитить** файлы с реальными паролями в Git.
*   **[FE-001, FE-002, FE-003, FE-004] Переписать Frontend на работу через BFF:**
    *   **Где:** `frontend/src/App.tsx`, `ReportPage.tsx`, `.env`
    *   **Что делать:**
        1.  Удалить библиотеку `@react-keycloak/web`.
        2.  Удалить всю логику работы с `keycloak.token`.
        3.  Настроить `REACT_APP_AUTH_URL` (BFF URL) в `.env`.
        4.  Все запросы к `/reports` направлять через BFF, а не напрямую в `reports-api`. BFF сам добавит необходимые cookie.
*   **[KC-001] Создать confidential клиент в Keycloak для BFF:**
    *   **Где:** Keycloak Admin Console / `realm-export.json`
    *   **Что делать:**
        1.  Создать нового клиента с `Client ID = bionicpro-auth`.
        2.  Установить `Access Type = confidential`.
        3.  Включить `Standard Flow` (для Authorization Code).
        4.  Сгенерировать и сохранить Client Secret (в `.env`!).
        5.  Удалить прямую интеграцию фронтенда с Keycloak.
*   **[AUTH-004, KC-002] Настроить Redis и Keycloak:**
    *   **Где:** `docker-compose.yaml`, `realm-export.json`
    *   **Что делать:**
        1.  **Redis:** Добавить `--requirepass <сильный_пароль>` в команду запуска Redis. Добавить пароль в `application.yml` сервиса `bionicpro-auth`.
        2.  **Keycloak:** В настройках Realm, в разделе Authentication, сделать MFA (TOTP) **обязательным** (`Required`), а не опциональным.
        3.  **ClickHouse:** Установить пароль в переменную `CLICKHOUSE_PASSWORD` и убрать `::/0` из `users.xml`, заменив на `127.0.0.1` или IP подсети Docker.
*   **[RPT-001, SEC-001] Исправить авторизацию в Reports API:**
    *   **Где:** `bionicpro-reports/src/main/java/.../config/SecurityConfig.java`
    *   **Что делать:**
        1.  Заменить `.anyRequest().permitAll()` на `.anyRequest().authenticated()`.
        2.  Добавить проверку роли: `.requestMatchers("/api/v1/reports/**").hasRole("prothetic_user")`.

### 2.2. Архитектура: Ключевая Функциональность

*   **[AUTH-001, CQ-004, SEC-019] Реализовать автоматический Token Refresh:**
    *   **Где:** `bionicpro-auth/src/main/java/.../service/SessionService.java`
    *   **Что делать:**
        1.  Убрать `// TODO`.
        2.  В методе `validateAndRefreshSession`, используя `refreshToken` из `SessionData`, вызвать endpoint `/token` Keycloak.
        3.  Обновить `accessToken`, `refreshToken` и время их истечения в `SessionData`.
        4.  Сохранить обновлённую сессию в Redis.
*   **[ARCH-001] Создать недостающие классы согласно ТЗ:**
    *   **Где:** `bionicpro-auth`
    *   **Что делать (Создать интерфейсы + реализации):**
        *   `AuthService` / `AuthServiceImpl`
        *   `SessionService` интерфейс (выделить из существующего класса)
        *   `InMemorySessionRepository` (как fallback при недоступности Redis)
        *   `CookieUtil`
        *   `TokenEncryptor`
*   **[AUTH-003, SEC-018] Внедрить Rate Limiting:**
    *   **Где:** `bionicpro-auth/.../config/SecurityConfig.java`
    *   **Что делать:**
        1.  Добавить зависимость `spring-boot-starter-aop` и `bucket4j` (или Resilience4j).
        2.  Настроить лимиты: 10 запросов в минуту на `/login`, 5 запросов в минуту на `/refresh`.
*   **[FE-003] Добавить API Gateway / Proxy в BFF:**
    *   **Где:** `bionicpro-auth/.../config/SecurityConfig.java`
    *   **Что делать:**
        1.  Настроить проксирование (`apiProxySecurityFilterChain`) для маршрутов `/api/v1/reports/**` на внутренний сервис `bionicpro-reports`. Это позволит фронту ходить на `/api/v1/reports` своего хоста, а BFF будет перенаправлять запрос.

---

## 3. 🟠 Фаза 2: СРОЧНЫЕ УЛУЧШЕНИЯ (P1 — High)

### 3.1. Безопасность и Корректность

*   **[SEC-016, AUTH-002] Session Rotation на все запросы:**
    *   **Где:** `bionicpro-auth/.../filter/SessionRotationFilter.java`
    *   **Что делать:** Изменить фильтр так, чтобы он вызывал `sessionService.rotateSession()` для **каждого** аутентифицированного запроса, а не только для `/status` и `/refresh`.
*   **[AUTH-005, SEC-017] Исправить SameSite атрибут cookie:**
    *   **Где:** `bionicpro-auth/.../config/RedisConfig.java` (и в месте создания cookie)
    *   **Что делать:** Заменить `SameSite=Lax` на `SameSite=Strict` для обеспечения максимальной защиты от CSRF.
*   **[SEC-006, SEC-008] Исправить CORS и сетевую доступность:**
    *   **Где:** `frontend/nginx.conf`, `olap-db/users.xml`
    *   **Что делать:**
        1.  В `nginx.conf` заменить `Access-Control-Allow-Origin '*'` на конкретный домен фронтенда.
        2.  В `users.xml` заменить `::/0` на IP-адреса доверенных сетей (например, подсети Docker).
*   **[AUTH-007] Добавить отзыв Refresh Token при logout:**
    *   **Где:** `bionicpro-auth/.../controller/AuthController.java` (метод `logout`)
    *   **Что делать:** Вызвать endpoint `logout` или `revoke` Keycloak, чтобы инвалидировать Refresh Token на стороне IdP.
*   **[RPT-004] Исправить SQL-запросы на параметризированные:**
    *   **Где:** `bionicpro-reports/.../repository/ReportRepository.java`
    *   **Что делать:** Убедиться, что все запросы используют `?` и передают параметры в `jdbcTemplate.query()`, чтобы исключить риск SQL Injection.

### 3.2. Архитектура и Модели Данных

*   **[RPT-002, ARCH-004] Создать `ReportNotFoundException`:**
    *   **Где:** `bionicpro-reports/.../exception/ReportNotFoundException.java`
    *   **Что делать:** Создать класс исключения и глобальный обработчик (`@ControllerAdvice`), который будет возвращать `404 Not Found`.
*   **[RPT-005, RPT-006, RPT-007] Синхронизировать модели данных:**
    *   **Где:** `bionicpro-reports`
    *   **Что делать:**
        1.  Обновить `UserReport.java` (JPA-сущность) в точном соответствии со схемой таблицы `user_reports` в ClickHouse (поля: `user_id`, `report_date`, `total_sessions`, `avg_signal_amplitude` и т.д.).
        2.  Обновить `ReportResponse.java` (DTO), чтобы он содержал структуру с `CustomerInfo` и агрегированными данными, как требует ТЗ.
        3.  Исправить `userReportRowMapper` и SQL-запросы в `ReportRepository` под новые модели.
*   **[AUTH-006] Включить CSRF защиту:**
    *   **Где:** `bionicpro-auth/.../config/SecurityConfig.java`
    *   **Что делать:** Убрать `csrf(csrf -> csrf.disable())`. Настроить `CookieCsrfTokenRepository` для работы с SPA.

### 3.3. Инфраструктура

*   **[DC-001, DC-002] Улучшить `docker-compose.yaml`:**
    *   **Где:** `docker-compose.yaml`
    *   **Что делать:**
        1.  Добавить **healthcheck** для Keycloak.
        2.  В `depends_on` для сервисов, которым нужна готовая БД, использовать `condition: service_healthy` вместо `service_started`.
        3.  Добавить сервисы `airflow-scheduler` и `airflow-worker` для полноценной работы Airflow.

---

## 4. 🟡 Фаза 3: ВАЖНЫЕ ЗАДАЧИ (P2 — Medium)

*   **[SEC-014] Убрать DEBUG и TRACE логи:** Переключить уровни логирования в `application-*.yml` на `INFO` или `WARN`.
*   **[SEC-015] Обновить зависимости:** Обновить `react-scripts` (или мигрировать на Vite) и другие устаревшие библиотеки.
*   **[CQ-001, CQ-002, CQ-003] Рефакторинг дублирования:** Вынести общие утилиты (логирование, константы) в отдельные классы. Убрать магические числа и строки.
*   **[AUTH-009, AUTH-010] Добавить MapStruct:** Подключить MapStruct для маппинга DTO и создать `InMemorySessionRepository` для graceful degradation.
*   **[TC-ALL-01] Написать интеграционные тесты:**
    *   **Где:** Все модули.
    *   **Что делать:** Использовать **Testcontainers** для написания тестов, проверяющих взаимодействие с реальными БД (PostgreSQL, ClickHouse) и Keycloak.
*   **[FE-005] Улучшить обработку ошибок на фронте:**
    *   **Где:** `frontend/src/components/ReportPage.tsx`
    *   **Что делать:** Добавить обработку сетевых ошибок (когда сервер недоступен) и показывать понятное сообщение пользователю.

---

## 5. 🟢 Фаза 4: УЛУЧШЕНИЯ И ТЕХДОЛГ (P3 — Low)

*   **[SEC-022] Убрать лишние пробросы портов:** В `docker-compose.yaml` убрать публикацию портов баз данных (`5432:5432`), если к ним не нужно подключаться с хоста.
*   **[CQ-010, CQ-013] Мелкие правки:**
    *   Сделать `RowMapper` final.
    *   Синхронизировать TS-интерфейсы на фронте с DTO на бэке.
*   **Улучшить Dockerfile:**
    *   В Dockerfile для Java-сервисов и фронтенда создать и использовать непривилегированного пользователя (не root).
    *   Добавить инструкцию `HEALTHCHECK` во все Dockerfile.
*   **Документация:**
    *   Добавить JavaDoc/JSDoc для сложных методов.
    *   Настроить генерацию OpenAPI (Swagger) спецификации для Reports API.
*   **Airflow:**
    *   Использовать `XCom` для передачи данных между тасками вместо записи в `/tmp`.
    *   Добавить явный `timeout` (execution_timeout) для задач DAG.

---

*Данный план является компиляцией трёх независимых аудитов. После выполнения всех пунктов рекомендуется провести повторное ревью.*