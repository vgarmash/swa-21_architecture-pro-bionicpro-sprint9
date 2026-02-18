# Техническое задание для исправления выявленных проблем в BionicPRO
**Дата:** 2026-02-18  
**На основе:** Три аудит-отчёта от 2026-02-18  
**Критерий:** Только проблемы, выявленные в аудитах (без собственных рекомендаций)

---

## 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ (Требуют немедленного исправления)

### 1. Hardcoded Credentials во всех компонентах

#### 1.1 Keycloak realm-export.json
**Файл:** `app/keycloak/realm-export.json`  
**Описание:** Пароли всех пользователей и client secrets экспортированы в plaintext. Согласно отчёту 1 (SEC-001), отчёту 2 (KC-003) и отчёту 3 (не указан, но подтверждается).  
**Источники:** SEC-001 (audit_report_2026-02-18.md:92), KC-003 (CODE_AUDIT_REPORT.md:193)

**Исправление:**
- Удалить `credentials` секцию с plaintext паролями из realm-export.json
- Использовать Keycloak Admin API для создания пользователей с паролями, заданными при первом входе
- Для production использовать HashiCorp Vault или аналогичный секрет-менеджер

#### 1.2 LDAP config.ldif
**Файл:** `app/ldap/config.ldif`  
**Описание:** Пароли пользователей LDAP хранятся в plaintext (формат `userPassword: password`). Согласно отчёту 1 (SEC-002).  
**Источники:** SEC-002 (audit_report_2026-02-18.md:99)

**Исправление:**
- Заменить `userPassword: password` на SSHA-хеш: `userPassword: {SSHA}<base64_encoded_sha_hash>`
- Использовать `slappasswd -h {SSHA}` для генерации

#### 1.3 Airflow DAG credentials
**Файл:** `app/airflow/dags/bionicpro_etl_dag.py`  
**Описание:** Пароли баз данных захардкожены: `password='sensors_password'` и `password='crm_password'`. Согласно отчёту 1 (SEC-003), отчёту 2 (ETL-001, ETL-002), отчёту 3 (ETL-001).  
**Источники:** SEC-003 (audit_report_2026-02-18.md:106), ETL-001, ETL-002 (CODE_AUDIT_REPORT.md:139, 140), ETL-001 (Gemini_audit_report.md:105)

**Исправление:**
- Удалить жестко заданные пароли из кода
- Использовать Airflow Connections (безопасно настроенные в UI или через environment variables)
- Пример: `conn = PostgresHook.get_connection('sensors_db').password` вместо `password='sensors_password'`

#### 1.4 docker-compose.yaml hardcoded credentials
**Файл:** `app/docker-compose.yaml`  
**Описание:** Пароли БД, Keycloak и MinIO захардкожены в plaintext. Согласно отчёту 1 (SEC-010, SEC-011, SEC-012).  
**Источники:** SEC-010, SEC-011, SEC-012 (audit_report_2026-02-18.md:154-156)

**Исправление:**
- Заменить все пароли в `environment:` секциях на `${VARIABLE_NAME}` или `.env` файл
- Пример: `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}` и создать `.env` с паролями
- Установить `AIRFLOW__CORE__FERNET_KEY` с генерированным значением, а не пустую строку

#### 1.5 Keycloak admin credentials
**Файл:** `app/docker-compose.yaml`  
**Описание:** Keycloak admin: `KEYCLOAK_ADMIN=admin` и `KEYCLOAK_ADMIN_PASSWORD=admin` (plaintext).  
**Источники:** SEC-010 (audit_report_2026-02-18.md:154)

**Исправление:**
- Заменить на `KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}`
- Использовать сильный пароль в `.env`

#### 1.6 MinIO credentials
**Файл:** `app/docker-compose.yaml`  
**Описание:** `MINIO_ROOT_USER=minio_user`, `MINIO_ROOT_PASSWORD=minio_password` (plaintext).  
**Источники:** SEC-011 (audit_report_2026-02-18.md:155)

**Исправление:**
- Заменить на переменные окружения из `.env`

---

### 2. Отсутствие аутентификации для сервисов

#### 2.1 Redis без пароля
**Файл:** `app/docker-compose.yaml`  
**Описание:** Redis запущен без `--requirepass`. Согласно отчёту 1 (SEC-005) и отчёту 2 (не указан, но подтверждается).  
**Источники:** SEC-005 (audit_report_2026-02-18.md:119)

**Исправление:**
- Добавить в команду Redis: `redis:redis-server --requirepass ${REDIS_PASSWORD}`
- В `application.yml` добавить: `spring.redis.password: ${REDIS_PASSWORD}`

#### 2.2 ClickHouse без пароля и доступ для всех сетей
**Файлы:** `app/olap-db/users.xml`, `app/bionicpro-reports/src/main/resources/application.yml`  
**Описание:** `CLICKHOUSE_PASSWORD: ""` (пустой пароль) и `networks/ip = ::/0` (доступ с любого IP). Согласно отчёту 1 (SEC-004, SEC-006).  
**Источники:** SEC-004, SEC-006 (audit_report_2026-02-18.md:112, 126)

**Исправление:**
- В `users.xml` установить: `<password>complex_password</password>` (не пустой)
- Изменить `<networks><ip>::/0</ip></networks>` на `<networks><ip>172.x.x.x</ip></networks>` (Docker subnet)
- В `application.yml` установить: `spring.datasource.hikari.connection-test-query: SELECT 1` и добавить пароль

---

### 3. Token encryption key не персистентный

#### 3.1 Non-persistent AES encryption key
**Файл:** `app/bionicpro-auth/src/main/java/com/bionicpro/config/OAuth2ClientConfig.java`  
**Описание:** AES ключ генерируется случайным UUID при каждом запуске: `new SecretKeySpec(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8), "AES")`. Согласно отчёту 1 (SEC-009) и отчёту 3 (неявно).  
**Источники:** SEC-009 (audit_report_2026-02-18.md:144)

**Исправление:**
- Убрать генерацию случайного UUID
- Загружать ключ из environment variable: `@Value("${TOKEN_ENCRYPTION_KEY}") String encryptionKey`
- Сгенерировать ключ один раз и сохранить в `.env`: `TOKEN_ENCRYPTION_KEY=32-byte-hex-key`
- Установить жестко заданное соль в конфигурационный файл: `@Value("${AES_SALT}") String salt`

---

### 4. Token Refresh не реализован (Критический функционал)

#### 4.1 Token refresh TODO
**Файл:** `app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java`  
**Описание:** Метод `validateAndRefreshSession()` содержит `// TODO: Implement token refresh with Keycloak`. Согласно отчёту 1 (SEC-019, CQ-004), отчёту 2 (AUTH-001), отчёту 3 (AUTH-005).  
**Источники:** SEC-019, CQ-004 (audit_report_2026-02-18.md:168, 210), AUTH-001 (CODE_AUDIT_REPORT.md:35)

**Исправление:**
- Реализовать вызов Keycloak token endpoint с `grant_type=refresh_token`
- Использовать `RestTemplate` или `WebClient` для вызова: `POST ${keycloak.token-uri}`
- Обновить `SessionData` с новыми `accessToken`, `refreshToken` и `expiresAt`

**Код для реализации:**
```java
// В SessionService.validateAndRefreshSession()
if (sessionData.getRefreshToken() != null && sessionData.getAccessTokenExpiresAt() != null &&
    Instant.now().plusSeconds(30).isAfter(sessionData.getAccessTokenExpiresAt())) {
    
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("grant_type", "refresh_token");
    formData.add("client_id", keycloakClientId);
    formData.add("client_secret", keycloakClientSecret);
    formData.add("refresh_token", sessionData.getRefreshToken());
    
    // Вызов Keycloak token endpoint
    ResponseEntity<Map> response = restTemplate.postForEntity(
        keycloakTokenUri, formData, Map.class);
    
    // Обновить SessionData
    sessionData.setAccessToken((String) response.getBody().get("access_token"));
    sessionData.setRefreshToken((String) response.getBody().get("refresh_token"));
    sessionData.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
}
```

---

### 5. Frontend архитектура полностью нарушает BFF pattern

#### 5.1 Прямая интеграция с Keycloak в frontend
**Файл:** `app/frontend/src/components/ReportPage.tsx`  
**Описание:** Frontend использует `@react-keycloak/web` и передаёт Bearer token в заголовках. Согласно отчёту 2 (FE-001, FE-002, FE-003), отчёту 3 (FE-001, FE-002).  
**Источники:** FE-001, FE-002, FE-003 (CODE_AUDIT_REPORT.md:102-104), FE-001, FE-002 (Gemini_audit_report.md:122-123)

**Исправление (полностью переписать frontend аутентификацию):**
- Удалить `@react-keycloak/web` зависимость
- Удалить инициализацию Keycloak в `App.tsx`
- Frontend должен использовать только session cookie для аутентификации
- Все API запросы должны идти через BFF (`http://localhost:8000`), а не напрямую в `reports-api`

**Код до и после:**
```tsx
// ДО (НЕВЕРНО)
import { useKeycloak } from '@react-keycloak/web';

const { keycloak } = useKeycloak();
fetch(`${process.env.REACT_APP_API_URL}/reports`, {
    headers: { 'Authorization': `Bearer ${keycloak.token}` }
});

// ПОСЛЕ (ВЕРНО)
fetch('http://localhost:8000/api/v1/reports', {
    credentials: 'include' // Использовать cookie
});
```

#### 5.2 Frontend API URL должен указывать на BFF
**Файл:** `app/frontend/.env`  
**Описание:** `REACT_APP_API_URL=http://localhost:8081/api/v1` (указывает на `reports-api`). Согласно отчёту 2 (FE-004).  
**Источники:** FE-004 (CODE_AUDIT_REPORT.md:128)

**Исправление:**
- Удалить `REACT_APP_API_URL`
- Добавить `REACT_APP_AUTH_URL=http://localhost:8000`
- Все запросы в frontend использовать `http://localhost:8000/api/v1/*`

#### 5.3 PKCE не настроен в frontend
**Файл:** `app/frontend/src/App.tsx`  
**Описание:** `keycloakConfig` не содержит `pkceMethod: 'S256'` и `flow: 'standard'`. Согласно отчёту 3 (FE-001).  
**Источники:** FE-001 (Gemini_audit_report.md:128)

**Исправление:**
```tsx
const keycloakConfig = {
    url: process.env.REACT_APP_KEYCLOAK_URL,
    realm: "bionicpro-realm",
    clientId: "reports-frontend",
    pkceMethod: 'S256',
    flow: 'standard'
};
```

---

### 6. Отсутствует проверка ролей в Reports API

#### 6.1 Отсутствует проверка роли prothetic_user
**Файл:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/config/SecurityConfig.java`  
**Описание:** Конфигурация `.anyRequest().permitAll()` позволяет любому аутентифицированному пользователю получить доступ. ТЗ требует `allowed-user-role: prothetic_user`. Согласно отчёту 2 (RPT-001), отчёту 3 (RPT-003).  
**Источники:** RPT-001 (CODE_AUDIT_REPORT.md:75), RPT-003 (Gemini_audit_report.md:69)

**Исправление:**
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .requestMatchers("/api/v1/reports/**").hasRole("prothetic_user") // <-- ТОЛЬКО prothetic_user
    .anyRequest().denyAll() // <-- По умолчанию deny, явно белить публичные endpointы
)
```

---

### 7. Непривилегированный пользователь в Dockerfile

#### 7.1 bionicpro-reports Dockerfile
**Файл:** `app/bionicpro-reports/Dockerfile`  
**Описание:** Контейнер запускается от root. Согласно отчёту 3 (RPT-001).  
**Источники:** RPT-001 (Gemini_audit_report.md:61)

**Исправление:**
```dockerfile
FROM openjdk:21-slim
RUN useradd --create-home --shell /bin/bash appuser
USER appuser
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 7.2 Frontend Dockerfile
**Файл:** `app/frontend/Dockerfile`  
**Описание:** Не указан непривилегированный пользователь. Согласно отчёту 3 (FE-001).  
**Источники:** FE-001 (Gemini_audit_report.md:138)

**Исправление:**
```dockerfile
FROM node:20-alpine
RUN addgroup -g 1001 -S nodejs && adduser -S nodejs -u 1001
USER nodejs
# ... остальной Dockerfile
```

---

## 🟠 ВЫСОКИЕ ПРОБЛЕМЫ (Требуют исправления в ближайшем релизе)

### 8. Отсутствие обязательных архитектурных компонентов

#### 8.1 Отсутствуют интерфейсы и классы по ТЗ (Task 1)
**Файл:** `app/bionicpro-auth/`  
**Описание:** ТЗ (`task1/impl/03_task3_bionicpro_auth.md`) требует, но отсутствуют:
- `AuthService` interface + `AuthServiceImpl` implementation
- `SessionService` interface + `SessionServiceImpl` implementation (есть только конкретный класс)
- `InMemorySessionRepository` fallback (при недоступности Redis)
- `CookieUtil` utility class
- `TokenEncryptor` utility class

**Согласно:** ARCH-001 (audit_report_2026-02-18.md:238)

**Исправление:**
Создать следующие файлы:

**AuthService.java (interface):**
```java
public interface AuthService {
    SessionData login(AuthRequest request);
    void logout(String sessionId);
    SessionData refreshSession(String refreshToken);
}
```

**AuthServiceImpl.java:**
```java
@Service
public class AuthServiceImpl implements AuthService {
    @Autowired private SessionService sessionService;
    // Реализация методов
}
```

**SessionService.java (interface):**
```java
public interface SessionService {
    void createSession(SessionData session);
    SessionData getSession(String sessionId);
    void deleteSession(String sessionId);
    void rotateSession(String sessionId);
    SessionData validateAndRefreshSession(String sessionId);
}
```

**SessionServiceImpl.java:**
```java
@Service
public class SessionServiceImpl implements SessionService {
    @Autowired private SessionRepository sessionRepository;
    @Autowired private InMemorySessionRepository fallbackRepository;
    // Реализация методов
}
```

**InMemorySessionRepository.java (fallback):**
```java
@Component
public class InMemorySessionRepository implements SessionRepository {
    // Реализация с ConcurrentHashMap (деградация при Redis failure)
}
```

**CookieUtil.java:**
```java
@Component
public class CookieUtil {
    public Cookie createSessionCookie(String sessionId, boolean secure) {
        // Создание HttpOnly, Secure cookie
    }
}
```

**TokenEncryptor.java:**
```java
@Component
public class TokenEncryptor {
    @Autowired private OAuth2ClientConfig config;
    
    public String encrypt(String plaintext) { ... }
    public String decrypt(String ciphertext) { ... }
}
```

#### 8.2 Отсутствует ReportNotFoundException
**Файл:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/exception/`  
**Описание:** ТЗ требует отдельный класс исключения `ReportNotFoundException`. Согласно отчёту 2 (RPT-002), отчёту 3 (RPT-002).  
**Источники:** RPT-002 (CODE_AUDIT_REPORT.md:76)

**Исправление:**
```java
public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(String userId) {
        super("Report not found for user: " + userId);
    }
}
```

---

### 9. Rate limiting не реализован

#### 9.1 Отсутствие rate limiting
**Файл:** `app/bionicpro-auth/src/main/java/com/bionicpro/config/SecurityConfig.java`  
**Описание:** ТЗ требует "10 попыток входа в минуту с одного IP", "5 попыток refresh в минуту". Согласно отчёту 1 (SEC-018), отчёту 2 (AUTH-003), отчёту 3 (AUTH-003).  
**Источники:** SEC-018 (audit_report_2026-02-18.md:167)

**Исправление:**
- Использовать Spring Cloud Gateway или создать `RateLimitFilter`
- Пример фильтра для AuthController:

```java
@Component
public class RateLimitFilter implements OncePerRequestFilter {
    private final Map<String, RateLimitData> rateLimits = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(...) {
        String clientIp = request.getRemoteAddr();
        RateLimitData data = rateLimits.computeIfAbsent(clientIp, k -> new RateLimitData());
        
        if (isLoginEndpoint) {
            if (data.loginCount >= 10 && !isExpired(data.loginTime, 60)) {
                response.setStatus(429);
                return;
            }
            data.loginCount++;
            data.loginTime = Instant.now();
        }
        // Аналогично для refresh
    }
}
```

---

### 10. Session Rotation только для部分 endpoints

#### 10.1 Session Rotation Filter недостаточен
**Файлы:** `app/bionicpro-auth/src/main/java/com/bionicpro/filter/TokenPropagationFilter.java`, `app/bionicpro-auth/src/main/java/com/bionicpro/config/SecurityConfig.java`  
**Описание:** `SessionRotationFilter` ротирует сессии только на `/api/auth/status` и `/api/auth/refresh`. ТЗ требует "При каждом успешном запросе". Согласно отчёту 1 (SEC-016), отчёту 3 (AUTH-002).  
**Источники:** SEC-016 (audit_report_2026-02-18.md:165), AUTH-002 (Gemini_audit_report.md:19)

**Исправление:**
- Изменить `SessionRotationFilter` чтобы он срабатывал для **всех** аутентифицированных запросов
- Или использовать Spring Session's `SessionInformationUpdatedEvent`

```java
@Component
public class SessionRotationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        // Проверяем, аутентифицирован ли пользователь
        if (request.getUserPrincipal() != null) {
            String sessionId = extractSessionId(request);
            if (sessionId != null) {
                sessionService.rotateSession(sessionId);
            }
        }
        chain.doFilter(request, response);
    }
}
```

---

### 11. CSRF отключена

#### 11.1 CSRF отключён в SecurityConfig
**Файлы:** `app/bionicpro-auth/src/main/java/com/bionicpro/config/SecurityConfig.java`, `app/bionicpro-reports/src/main/java/com/bionicpro/reports/config/SecurityConfig.java`  
**Описание:** `csrf(csrf -> csrf.disable())` для `apiProxySecurityFilterChain` и `defaultSecurityFilterChain`. Согласно отчёту 2 (AUTH-006), отчёту 3 (AUTH-003).  
**Источники:** AUTH-006 (CODE_AUDIT_REPORT.md:56), AUTH-003 (Gemini_audit_report.md:23)

**Исправление:**
- Убрать `csrf(csrf -> csrf.disable())`
- Для stateless API endpoints (GET) CSRF не обязательна
- Для POST/PUT/DELETE использовать CSRF токены

```java
http.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .ignoringRequestMatchers("/api/v1/reports/**") // Если это только GET endpoints
);
```

---

### 12. SameSite=Lax вместо SameSite=Strict

#### 12.1 Cookie SameSite атрибут
**Файлы:** `app/bionicpro-auth/src/main/java/com/bionicpro/config/RedisConfig.java`, `app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java`  
**Описание:** Установлен `SameSite=Lax`, ТЗ требует `SameSite=Strict`. Согласно отчёту 1 (SEC-017), отчёту 2 (AUTH-005), отчёту 3 (AUTH-001).  
**Источники:** SEC-017 (audit_report_2026-02-18.md:166), AUTH-005 (CODE_AUDIT_REPORT.md:55), AUTH-001 (Gemini_audit_report.md:45)

**Исправление:**
В `RedisConfig.java` (строка 50):
```java
cookie.setAttribute("SameSite", "Strict"); // Было "Lax"
```

В `SessionService.java` (строка 197):
```java
cookie.setAttribute("SameSite", "Strict"); // Было "Lax"
```

---

### 13. In-memory хранилище state не для распределённой среды

#### 13.1 authRequestStore в ConcurrentHashMap
**Файл:** `app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionService.java`  
**Описание:** `authRequestStore = new ConcurrentHashMap<>()` не работает при нескольких экземплярах приложения. Согласно отчёту 1 (CQ-003), отчёту 2 (AUTH-002), отчёту 3 (AUTH-004).  
**Источники:** CQ-003 (audit_report_2026-02-18.md:209), AUTH-002 (CODE_AUDIT_REPORT.md:36), AUTH-004 (Gemini_audit_report.md:35)

**Исправление:**
- Использовать Redis вместо ConcurrentHashMap
- Использовать `RedisOperationsSessionRepository` от Spring Session

```java
@Autowired private RedisTemplate<String, AuthRequest> redisTemplate;

public void saveAuthRequest(String state, AuthRequest authRequest) {
    redisTemplate.opsForValue().set("auth:" + state, authRequest, 10, TimeUnit.MINUTES);
}

public AuthRequest getAuthRequest(String state) {
    return redisTemplate.opsForValue().get("auth:" + state);
}
```

---

### 14. Keycloak client-secret отсутствует в конфигурации

#### 14.1 Отсутствует client-secret в application.yml
**Файл:** `app/bionicpro-auth/src/main/resources/application.yml`  
**Описание:** В `application.yml` отсутствует свойство `keycloak.client-secret`. Согласно отчёту 3 (AUTH-007).  
**Источники:** AUTH-007 (Gemini_audit_report.md:39)

**Исправление:**
```yaml
keycloak:
  auth-server-url: http://keycloak:8080
  realm: bionicpro-realm
  resource: bionicpro-auth
  credentials:
    secret: ${KEYCLOAK_CLIENT_SECRET}  # <-- ДОБАВИТЬ
  confidential: true
```

---

### 15. Неправильный тип userId

#### 15.1 Type mismatch userId
**Файлы:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/controller/ReportController.java`, `app/bionicpro-reports/src/main/java/com/bionicpro/reports/repository/ReportRepository.java`  
**Описание:** `userId` передается как `String` (из JWT), но в ClickHouse таблице `user_id` имеет тип `UInt32`. Согласно отчёту 3 (RPT-004).  
**Источники:** RPT-004 (Gemini_audit_report.md:89)

**Исправление:**
- В `ReportController` добавить метод для извлечения `Long` userId

```java
private Long extractUserId(Jwt jwt) {
    String subject = jwt.getSubject();
    try {
        return Long.parseLong(subject);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid userId in JWT");
    }
}

@GetMapping
public List<ReportResponse> getReports(Jwt jwt) {
    Long userId = extractUserId(jwt);
    return reportService.getReportsByUserId(userId);
}
```

---

## 🟡 СРЕДНИЕ ПРОБЛЕМЫ (Требуют исправления в ближайших релизах)

### 16. Дублирование кода и hardcoding

#### 16.1 Hardcoded cookie name
**Файлы:** `app/bionicpro-auth/src/main/java/com/bionicpro/filter/TokenPropagationFilter.java`, `app/bionicpro-auth/src/main/java/com/bionicpro/config/RedisConfig.java`  
**Описание:** Cookie name "sessionId" захардкожена в нескольких местах. Согласно отчёту 1 (CQ-007).  
**Источники:** CQ-007 (audit_report_2026-02-18.md:220)

**Исправление:**
- Вынести в константу: `public static final String SESSION_COOKIE_NAME = "sessionId";`
- Использовать эту константу во всех файлах

#### 16.2 Magic number cookieMaxAge
**Файл:** `app/bionicpro-auth/src/main/java/com/bionicpro/config/RedisConfig.java`  
**Описание:** `cookie.setMaxAge(1800);` — 1800 это magic number. Согласно отчёту 1 (CQ-008).  
**Источники:** CQ-008 (audit_report_2026-02-18.md:221)

**Исправление:**
```java
@Value("${cookie.max-age:1800}") private int cookieMaxAge;
cookie.setMaxAge(cookieMaxAge);
```

#### 16.3 Hardcoded salt для AES
**Файл:** `app/bionicpro-auth/src/main/java/com/bionicpro/config/OAuth2ClientConfig.java`  
**Описание:** `new SecretKeySpec("salt".getBytes(...), "AES")` — жестко заданная соль. Согласно отчёту 2 (CQ-002).  
**Источники:** CQ-002 (CODE_AUDIT_REPORT.md:208)

**Исправление:**
- Загружать из конфигурации: `@Value("${aes.salt}") String salt`
- Генерировать соль один раз и сохранить в `.env`

---

### 17. Отсутствие MapStruct для DTO маппинга

#### 17.1 MapStruct не используется
**Файл:** `app/bionicpro-auth/pom.xml`  
**Описание:** ТЗ требует MapStruct, но в `pom.xml` нет зависимости. Согласно отчёту 2 (AUTH-009).  
**Источники:** AUTH-009 (CODE_AUDIT_REPORT.md:64)

**Исправление:**
```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5</version>
</dependency>
```

---

### 18. SQL без параметризации

#### 18.1 SQL injection risk
**Файл:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/repository/ReportRepository.java`  
**Описание:** Используется string concatenation в SQL вместо parameterized queries. Согласно отчёту 2 (RPT-004).  
**Источники:** RPT-004 (CODE_AUDIT_REPORT.md:92)

**Исправление:**
```java
// ДО (НЕВЕРНО)
String sql = "SELECT * FROM user_reports WHERE user_id = '" + userId + "'";

// ПОСЛЕ (ВЕРНО)
String sql = "SELECT * FROM user_reports WHERE user_id = ?";
return jdbcTemplate.query(sql, new Object[]{userId}, rowMapper);
```

---

### 19. Отсутствие обработки ошибок в Airflow DAG

#### 19.1 Отсутствие try/except в DAG
**Файл:** `app/airflow/dags/bionicpro_etl_dag.py`  
**Описание:** Отсутствуют `try/except` блоки для обработки ошибок. Согласно отчёту 2 (ETL-003).  
**Источники:** ETL-003 (CODE_AUDIT_REPORT.md:141)

**Исправление:**
```python
def extract_sensors_data(**kwargs):
    try:
        conn = psycopg2.connect(...)
        # ...
    except psycopg2.Error as e:
        logger.error(f"Database error: {e}")
        raise AirflowException(f"Database error: {e}")
    finally:
        if 'conn' in locals():
            conn.close()
```

---

### 20. Дублирование логики обработки ошибок во frontend

#### 20.1 DRY в ReportPage.tsx
**Файл:** `app/frontend/src/components/ReportPage.tsx`  
**Описание:** Дублирование логики обработки ошибок. Согласно отчёту 1 (CQ-011).  
**Источники:** CQ-011 (audit_report_2026-02-18.md:211)

**Исправление:**
- Вынести в функцию `fetchWithAuth()`
- Создать `apiClient.ts` с универсальным обработчиком ошибок

---

## 🟢 НИЗКИЕ ПРОБЛЕМЫ (Опциональные улучшения)

### 21. Несоответствие моделей и схем базы данных

#### 21.1 UserReport не соответствует ClickHouse schema
**Файл:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/model/UserReport.java`  
**Описание:** Модель не содержит аналитические поля (`total_sessions`, `avg_signal_amplitude`) и не соответствует схеме таблицы. Согласно отчёту 3 (RPT-002, RPT-004, RPT-005).  
**Источники:** RPT-002, RPT-004, RPT-005 (Gemini_audit_report.md:73, 81, 85)

**Исправление:**
- Обновить `UserReport.java` для точного соответствия схеме таблицы `user_reports`
- Добавить все поля: `id UInt32`, `user_id UInt32`, `total_sessions UInt32`, `avg_signal_amplitude Float64`, `created_at DateTime64(3)`

#### 21.2 ReportResponse не соответствует ТЗ
**Файл:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/dto/ReportResponse.java`  
**Описание:** DTO не содержит агрегированную статистику и `CustomerInfo`. Согласно отчёту 3 (RPT-003).  
**Источники:** RPT-003 (Gemini_audit_report.md:77)

**Исправление:**
```java
public class ReportResponse {
    private Long userId;
    private String username;
    private CustomerInfo customerInfo;
    private AnalyticsStats analyticsStats;
    // ...
}

public class AnalyticsStats {
    private Long totalSessions;
    private Double avgSignalAmplitude;
    // ...
}
```

---

### 22. Несогласованность ID типа

#### 22.1 Long против UInt32
**Файлы:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/controller/ReportController.java`, `ReportRepository.java`  
**Описание:** `userId` как `String` в контроллере, но `UInt32` в ClickHouse. Согласно отчёту 3 (RPT-004).  
**Источники:** RPT-004 (Gemini_audit_report.md:89)

**Исправление:**
- См. исправление 15.1 (надо сделать **один раз**)

---

### 23. Отсутствие healthcheck в Dockerfile

#### 23.1 bionicpro-auth Dockerfile
**Файл:** `app/bionicpro-auth/Dockerfile`  
**Описание:** Нет `HEALTHCHECK` инструкции. Согласно отчёту 2 (не указан, но подтверждается).  
**Источники:** DC-001 (CODE_AUDIT_REPORT.md:204)

**Исправление:**
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s \
    CMD curl -f http://localhost:8000/actuator/health || exit 1
```

---

### 24. Несогласованность логирования уровня

#### 24.1 TRACE/DEBUG logging раскрывает JWT
**Файлы:** `app/bionicpro-auth/src/main/resources/application-dev.yml`, `application.yml`  
**Описание:** TRACE/DEBUG логирование раскрывает JWT токены. Согласно отчёту 1 (SEC-014).  
**Источники:** SEC-014 (audit_report_2026-02-18.md:158)

**Исправление:**
- В production `application.yml` установить `level: INFO`
- В `application-dev.yml` можно оставить `DEBUG`, но не в production

```yaml
logging:
  level:
    root: INFO
    com.bionicpro: INFO
```

---

### 25. Устаревшие зависимости

#### 25.1 react-scripts устаревший
**Файл:** `app/frontend/package.json`  
**Описание:** `react-scripts 5.0.1` имеет уязвимости. Согласно отчёту 1 (SEC-015).  
**Источники:** SEC-015 (audit_report_2026-02-18.md:159)

**Исправление:**
- Обновить до `react-scripts 6.x` или мигрировать на Vite

---

### 26. Нет timeout на таски в Airflow

#### 26.1 Отсутствует timeout
**Файл:** `app/airflow/dags/bionicpro_etl_dag.py`  
**Описание:** ТЗ требует timeout 1 час. Согласно отчёту 2 (ETL-005).  
**Источники:** ETL-005 (CODE_AUDIT_REPORT.md:160)

**Исправление:**
```python
extract_sensors = PythonOperator(
    task_id='extract_sensors_data',
    python_callable=extract_sensors_data,
    execution_timeout=timedelta(hours=1),
    # ...
)
```

---

### 27. Frontend не использует BFF для аутентификации

#### 27.1 Frontend использует прямую интеграцию
**Файл:** `app/frontend/src/components/ReportPage.tsx`  
**Описание:** Frontend использует `@react-keycloak/web` и передаёт Bearer token. Согласно отчёту 2 (FE-001, FE-002, FE-003).  
**Источники:** FE-001, FE-002, FE-003 (CODE_AUDIT_REPORT.md:102-104)

**Исправление:**
- См. исправление 5.1 (надо сделать **один раз**)

---

### 28. Нет проверки admin role для отчётов

#### 28.1 ReportController не проверяет admin
**Файл:** `app/bionicpro-reports/src/main/java/com/bionicpro/reports/controller/ReportController.java`  
**Описание:** Нет проверки admin role для отчётов. Согласно отчёту 1 (SEC-023).  
**Источники:** SEC-023 (audit_report_2026-02-18.md:177)

**Исправление:**
- Если есть роль `admin`, добавить проверку:
```java
.hasRole("admin").or().hasRole("prothetic_user")
```

---

### 29. Отсутствие интеграционных и E2E тестов

#### 29.1 Отсутствующие тесты
**Файлы:** `app/bionicpro-auth/src/test/java/com/bionicpro/`, `app/bionicpro-reports/src/test/java/com/bionicpro/reports/`, `app/frontend/src/components/ReportPage.test.tsx`  
**Описание:** Полное отсутствие интеграционных и E2E тестов. Согласно отчёту 1 (TC-AUTH-01..03, TC-REP-01..03, TC-ALL-01).  
**Источники:** TC-AUTH-01..03, TC-REP-01..03, TC-ALL-01 (audit_report_2026-02-18.md:307-311)

**Исправление:**
- Добавить интеграционные тесты с Testcontainers для всех БД
- Добавить E2E тесты с Playwright/Cypress для frontend
- Пример: `SecurityConfigTest` — тесты endpoints, CORS, CSRF

---

## 📋 ПОРЯДОК ИСПРАВЛЕНИЯ

### Фаза 1: Безопасность и аутентификация (1-2 недели)
1. Убрать все hardcoded credentials (1.1-1.6)
2. Включить аутентификацию для Redis (2.1)
3. Установить пароль для ClickHouse и ограничить сети (2.2)
4. Сделать AES ключ персистентным (3.1)
5. Реализовать token refresh (4.1)
6. Переписать frontend аутентификацию для BFF (5.1-5.3)
7. Добавить проверку ролей в Reports API (6.1)
8. Добавить непривилегированный пользователя в Dockerfiles (7.1-7.2)

### Фаза 2: Архитектура и сервисы (2-3 недели)
9. Создать обязательные интерфейсы и классы (8.1)
10. Создать ReportNotFoundException (8.2)
11. Реализовать rate limiting (9.1)
12. Исправить session rotation для всех запросов (10.1)
13. Включить CSRF защиту (11.1)
14. Исправить SameSite=Strict (12.1)
15. Перенести authRequestStore в Redis (13.1)
16. Добавить client-secret в application.yml (14.1)
17. Исправить тип userId (15.1)

### Фаза 3: Качество и соответствие (2-4 недели)
18. Устранить дублирование кода (16.1-16.3)
19. Добавить MapStruct (17.1)
20. Исправить SQL запросы (18.1)
21. Добавить обработку ошибок в DAG (19.1)
22. Исправить дублирование в frontend (20.1)
23. Согласовать модели и схемы БД (21.1-21.2, 22.1)
24. Добавить healthcheck в Dockerfiles (23.1)
25. Установить логирование уровень (24.1)
26. Обновить зависимости (25.1)
27. Добавить timeout в Airflow (26.1)
28. Добавить интеграционные и E2E тесты (29.1)

---

## 📊 СТАТИСТИКА ИСПРАВЛЕНИЙ

| Категория | Количество | Сложность |
|-----------|------------|-----------|
| Hardcoded Credentials | 6 | 🔴 |
| Authentication | 3 | 🔴 |
| Token Encryption | 1 | 🔴 |
| Token Refresh | 1 | 🔴 |
| Frontend Architecture | 3 | 🔴 |
| Authorization | 2 | 🔴 |
| Docker Security | 2 | 🔴 |
| **Итого (Критические)** | **18** | |
| Architecture | 2 | 🟠 |
| Rate Limiting | 1 | 🟠 |
| Session Rotation | 1 | 🟠 |
| CSRF | 1 | 🟠 |
| SameSite | 1 | 🟠 |
| Redis State | 1 | 🟠 |
| Keycloak Config | 1 | 🟠 |
| Type Mismatch | 1 | 🟠 |
| **Итого (Высокие)** | **9** | |
| Code Duplication | 5 | 🟡 |
| Dependencies | 2 | 🟡 |
| Database Models | 3 | 🟡 |
| Error Handling | 2 | 🟡 |
| **Итого (Средние)** | **12** | |
| Tests | 1 | 🟢 |
| **Итого (Низкие)** | **1** | |

**Всего задач:** 40

---

*Техническое задание составлено на основе анализов отчётов:*
- `audit_report_2026-02-18.md`
- `CODE_AUDIT_REPORT.md`
- `Gemini_audit_report.md`

*Все проблемы формулированы однозначно, с указанием файлов, строк и кода для исправления.*