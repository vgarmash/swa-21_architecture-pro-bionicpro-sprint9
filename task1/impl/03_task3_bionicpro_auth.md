# Техническое задание: Задача 3. Сервис bionicpro-auth (BFF)

## 1. Название задачи и её цели

### Название
**Разработка сервиса bionicpro-auth - Backend for Frontend для безопасной аутентификации**

### Цели
1. Перенести механизм аутентификации из фронтенда в выделенный бэкенд-сервис
2. Изолировать access/refresh токены от клиентского приложения
3. Обеспечить безопасное хранение и управление токенами на сервере
4. Реализовать сессионную аутентификацию через HTTP-only куки
5. Поддержать автоматическое обновление токенов
6. Реализовать ротацию сессий для защиты от session fixation атак

---

## 2. Выбор языка и технологий

### Язык программирования
**Java 17+** с фреймворком **Spring Boot 3.x**

### Обоснование выбора
- Spring Security - лучшая в отрасли библиотека для OAuth2/OIDC
- Поддержка PKCE "из коробки"
- Встроенные механизмы сессий и шифрования
- Отличная экосистема и документация
- Единый стек с существующим кодом проекта (из git history видно наличие bionicpro-auth)

### Основные зависимости
- Spring Boot 3.2.0+
- Spring Security OAuth2 Client
- Spring Session (для распределённых сессий)
- Spring Data Redis (для кэширования токенов)
- Lombok
- MapStruct (для маппинга DTO)

---

## 3. Функциональные требования

### 3.1. Аутентификация и авторизация

#### 3.1.1. Интеграция с Keycloak через PKCE
- **Требование**: Сервис должен реализовать полный OAuth 2.0 Authorization Code Flow с PKCE
- **Keycloak URL**: `http://localhost:8080`
- **Realm**: `reports-realm`
- **Client ID**: `bionicpro-auth` (создать новый confidential клиент)
- **Client Secret**: Сгенерировать в Keycloak admin console

#### 3.1.2. Управление токенами
- **Требование**: Сервис получает и хранит токены от Keycloak
- **Access Token**: 
  - Время жизни: 2 минуты (настраивается в Keycloak)
  - Хранение: оперативная память сервера или распределённый кэш
- **Refresh Token**:
  - Время жизни: 30 минут (настраивается в Keycloak)
  - Хранение: защищённое хранилище (encrypted в Redis) или зашифрованном виде в памяти
- **Привязка**: Access token и refresh token привязываются к session ID

#### 3.1.3. Сессионное управление
- **Требование**: Сервис управляет сессиями пользователей
- **Session ID**: UUID v4, генерируется сервисом
- **Session Cookie**:
  - Имя: `BIONICPRO_SESSION`
  - Флаги: `HttpOnly`, `Secure`, `SameSite=Lax`
  - Время жизни: 30 минут (больше чем access token)
- **Session Storage**: Redis для распределённого хранения

#### 3.1.4. Автоматическое обновление токенов
- **Требование**: При истечении access token сервис автоматически обновляет его через refresh token
- **Условие**: Проверка перед каждым запросом к защищённому ресурсу
- **Логика**:
  1. Получить session ID из куки
  2. Найти сессию в хранилище
  3. Проверить срок действия access token
  4. Если истёк - использовать refresh token для получения новой пары токенов
  5. Обновить данные в сессии

#### 3.1.5. Ротация сессий (Session Fixation Protection)
- **Требование**: При каждом успешном запросе генерировать новый session ID
- **Алгоритм**:
  1. Проверить валидность текущей сессии
  2. Сгенерировать новый session ID
  3. Переместить данные в новую сессию
  4. Инвалидировать старую сессию
  5. Отправить новую куку в ответе

### 3.2. API эндпоинты

#### 3.2.1. Эндпоинт инициации аутентификации
- **Метод**: `GET`
- **Путь**: `/api/auth/login`
- **Параметры**: 
  - `redirectUri` (optional) - URL для редиректа после аутентификации
- **Ответ**: 
  - `302 Found` с Location на Keycloak authorization endpoint
  - Включает state и code_verifier в сессии

#### 3.2.2. Callback обработка
- **Метод**: `GET`
- **Путь**: `/api/auth/callback`
- **Параметры**:
  - `code` - authorization code от Keycloak
  - `state` - state parameter для валидации
  - `session_state` - state от Keycloak
- **Ответ**: 
  - При успехе: `302 Found` на `/api/auth/status` с установленной сессионной кукой
  - При ошибке: `302 Found` на error page

#### 3.2.3. Проверка статуса аутентификации
- **Метод**: `GET`
- **Путь**: `/api/auth/status`
- **Аутентификация**: Требуется сессионная кука
- **Ответ**:
```json
{
  "authenticated": true,
  "userId": "user-id-from-keycloak",
  "username": "username",
  "roles": ["user", "prothetic_user"],
  "sessionExpiresAt": "2024-01-01T12:30:00Z"
}
```

#### 3.2.4. Logout
- **Метод**: `POST`
- **Путь**: `/api/auth/logout`
- **Аутентификация**: Требуется сессионная кука
- **Действия**:
  - Инвалидировать сессию в Redis
  - Инвалидировать refresh token в Keycloak (отзыв)
  - Удалить сессионную куку
- **Ответ**: `200 OK` или `302 Found` на главную страницу

#### 3.2.5. Обновление сессии
- **Метод**: `POST`
- **Путь**: `/api/auth/refresh`
- **Аутентификация**: Требуется сессионная кука
- **Ответ**: Обновлённая сессионная кука (ротация)

#### 3.2.6. Проксирование запросов
- **Метод**: `*`
- **Путь**: `/api/**` (кроме auth endpoints)
- **Аутентификация**: Требуется сессионная кука
- **Функция**: 
  - Проверить валидность сессии
  - Добавить access token в Authorization header
  - Перенаправить запрос к backend API

### 3.3. Защита от атак

#### 3.3.1. CSRF защита
- **Требование**: Реализовать CSRF токены для state parameter
- **Реализация**: Использовать Spring Security CSRF token

#### 3.3.2. Rate limiting
- **Требование**: Ограничить количество попыток аутентификации
- **Лимиты**:
  - 10 попыток входа в минуту с одного IP
  - 5 попыток refresh token в минуту

#### 3.3.3. Валидация state parameter
- **Требование**: Валидация state для предотвращения CSRF
- **Реализация**: Хранить state в сессии, сравнивать при callback

---

## 4. Нефункциональные требования

### 4.1. Производительность
- **Время отклика**: 
  - Эндпоинты аутентификации: < 200ms (без учета редиректа на Keycloak)
  - Проксированные API: < 500ms (с учётом latency backend API)
- **Пропускная способность**: 1000 RPS
- **Потребление памяти**: < 512MB для 10,000 активных сессий

### 4.2. Надёжность
- **Доступность**: 99.9% (допустимый downtime: 8.76 часов/год)
- **Graceful degradation**: При недоступности Redis использовать in-memory storage
- **Резервное копирование**: Конфигурация в YAML-файлах

### 4.3. Безопасность
- **Шифрование**: Refresh token хранится в зашифрованном виде (AES-256)
- **Куки**: HttpOnly, Secure, SameSite=Lax
- **Audit logging**: Логирование всех операций аутентификации
- **Токены**: Access token не передаётся фронтенду

### 4.4. Масштабируемость
- **Горизонтальное масштабирование**: Stateless сервис, сессии в Redis
- **Вертикальное масштабирование**: Поддержка multi-instance развёртывания

---

## 5. Архитектурные решения

### 5.1. Компонентная диаграмма

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (SPA)                          │
│                  React App + react-keycloak-web                 │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP + Cookie (HttpOnly)
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    bionicpro-auth (BFF)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │   Auth      │  │  Session    │  │   Token     │              │
│  │ Controller  │  │  Service    │  │  Service    │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
│         │                │                │                     │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐              │
│  │   OAuth2    │  │   Redis     │  │  Keycloak   │              │
│  │   Client    │  │  Session    │  │   Client    │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
└─────────────────────────────┬───────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌──────────────┐  ┌──────────────────┐
│   Keycloak      │  │    Redis     │  │   Backend API    │
│   (IdP)         │  │   Session    │  │   (Reports)      │
└─────────────────┘  └──────────────┘  └──────────────────┘
```

### 5.2. Структура проекта

```
bionicpro-auth/
├── pom.xml
├── src/main/java/com/bionicpro/auth/
│   ├── BionicproAuthApplication.java
│   ├── config/
│   │   ├── KeycloakProperties.java
│   │   ├── RedisProperties.java
│   │   ├── SecurityConfig.java
│   │   └── SessionConfig.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   └── SessionController.java
│   ├── dto/
│   │   ├── AuthStatusResponse.java
│   │   └── LoginRequest.java
│   ├── model/
│   │   ├── SessionData.java
│   │   └── TokenData.java
│   ├── repository/
│   │   ├── SessionRepository.java
│   │   └── InMemorySessionRepository.java (fallback)
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── AuthServiceImpl.java
│   │   ├── SessionService.java
│   │   └── SessionServiceImpl.java
│   └── util/
│       ├── CookieUtil.java
│       └── TokenEncryptor.java
└── src/main/resources/
    └── application.yml
```

### 5.3. Конфигурация (application.yml)

```yaml
server:
  port: 8000

spring:
  application:
    name: bionicpro-auth
  session:
    store-type: redis
    redis:
      namespace: bionicpro:session
  data:
    redis:
      host: localhost
      port: 6379

keycloak:
  server-url: http://localhost:8080
  realm: reports-realm
  client-id: bionicpro-auth
  client-secret: ${KEYCLOAK_CLIENT_SECRET}
  redirect-uri: http://localhost:8000/api/auth/callback

auth:
  session:
    timeout-minutes: 30
    cookie-name: BIONICPRO_SESSION
  token:
    access-token-timeout-minutes: 2
    refresh-token-timeout-minutes: 30
```

---

## 6. Модель данных

### 6.1. SessionData

```java
public class SessionData {
    private String sessionId;           // UUID
    private String userId;              // Keycloak user ID
    private String username;            // username
    private List<String> roles;         // Keycloak roles
    private String accessToken;         //encrypted
    private String refreshToken;        //encrypted
    private String codeVerifier;        // for PKCE
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastAccessedAt;
}
```

### 6.2. TokenData

```java
public class TokenData {
    private String accessToken;
    private String refreshToken;
    private Instant accessTokenExpiresAt;
    private Instant refreshTokenExpiresAt;
    private String tokenType;           // Bearer
    private String scope;
}
```

---

## 7. API-спецификация

### 7.1. Эндпоинт инициации аутентификации

**GET** `/api/auth/login`

**Query Parameters**:

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|-----------|
| redirectUri | String | Нет | URL для редиректа после успешной аутентификации |

**Response**: `302 Found`
```
Location: http://localhost:8080/realms/reports-realm/protocol/openid-connect/auth?
  client_id=bionicpro-auth&
  redirect_uri=http://localhost:8000/api/auth/callback&
  response_type=code&
  scope=openid profile email&
  state=...&
  code_challenge=...&
  code_challenge_method=S256
```

### 7.2. Callback обработка

**GET** `/api/auth/callback`

**Query Parameters**:

| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|-----------|
| code | String | Да | Authorization code от Keycloak |
| state | String | Да | State parameter для валидации |
| session_state | String | Да | Keycloak session state |

**Response**: `302 Found`
```
Location: http://localhost:3000/
Set-Cookie: BIONICPRO_SESSION=...; HttpOnly; Secure; SameSite=Lax; Path=/
```

**Error Response**: `302 Found`
```
Location: http://localhost:3000/login?error=auth_failed
```

### 7.3. Проверка статуса

**GET** `/api/auth/status`

**Headers**:
```
Cookie: BIONICPRO_SESSION=...
```

**Success Response**: `200 OK`
```json
{
  "authenticated": true,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "user1",
  "roles": ["user", "prothetic_user"],
  "sessionExpiresAt": "2024-01-01T12:30:00Z"
}
```

**Error Response**: `401 Unauthorized`
```json
{
  "error": "not_authenticated",
  "message": "User is not authenticated"
}
```

### 7.4. Logout

**POST** `/api/auth/logout`

**Headers**:
```
Cookie: BIONICPRO_SESSION=...
```

**Response**: `200 OK`
```
Set-Cookie: BIONICPRO_SESSION=; Expires=Thu, 01 Jan 1970 00:00:00 GMT
```

### 7.5. Обновление сессии

**POST** `/api/auth/refresh`

**Headers**:
```
Cookie: BIONICPRO_SESSION=...
```

**Response**: `200 OK`
```
Set-Cookie: BIONICPRO_SESSION=...; HttpOnly; Secure; SameSite=Lax
```

---

## 8. Сценарии использования

### 8.1. Сценарий 1: Первичная аутентификация

```
1. Пользователь открывает React приложение
2. Приложение делает GET /api/auth/status (без куки)
3. bionicpro-auth возвращает 401 + URL для логина
4. Приложение редиректит на /api/auth/login?redirectUri=/dashboard
5. bionicpro-auth генерирует state и code_verifier
6. bionicpro-auth сохраняет их в сессию (temporary)
7. bionicpro-auth редиректит на Keycloak с PKCE
8. Пользователь вводит credentials в Keycloak
9. Keycloak редиректит на /api/auth/callback?code=...&state=...
10. bionicpro-auth валидирует state
11. bionicpro-auth обменивает code на tokens через Keycloak
12. bionicpro-auth создаёт сессию с tokens
13. bionicpro-auth устанавливает HttpOnly куку
14. bionicpro-auth редиректит на /dashboard
```

### 8.2. Сценарий 2: Автоматическое обновление токена

```
1. Пользователь делает запрос к /api/reports (с кукой)
2. bionicpro-auth извлекает session ID из куки
3. bionicpro-auth получает SessionData из Redis
4. bionicpro-auth проверяет: access token истекает < 30 сек?
5. Да - использует refresh token для получения новой пары
6. bionicpro-auth обновляет SessionData в Redis
7. bionicpro-auth добавляет новый access token к запросу
8. bionicpro-auth проксирует запрос к backend API
9. bionicpro-auth возвращает ответ клиенту
```

### 8.3. Сценарий 3: Ротация сессии

```
1. Пользователь делает запрос к /api/auth/status
2. bionicpro-auth валидирует сессию (успешно)
3. bionicpro-auth генерирует новый session ID
4. bionicpro-auth копирует данные в новую сессию
5. bionicpro-auth удаляет старую сессию
6. bionicpro-auth устанавливает новую куку
7. bionicpro-auth возвращает ответ
```

---

## 9. Зависимости от внешних систем

| Система | Тип | Версия | Назначение |
|---------|-----|--------|------------|
| Keycloak | IdP | 21.1+ | Аутентификация, выпуск токенов |
| Redis | Cache/Session Store | 7.0+ | Хранение сессий и токенов |
| Frontend | Client | React 18 | SPA-приложение |

---

## 10. Ограничения и допущения

### 10.1. Ограничения
1. **Same Origin**: Frontend и bionicpro-auth должны быть на одном домене (или настроен CORS)
2. **HTTPS**: В production требуется HTTPS для Secure кук
3. **Browser Support**: Требуется браузер с поддержкой SameSite кук

### 10.2. Допущения
1. Keycloak настроен и доступен по указанному URL
2. Redis доступен и настроен
3. Frontend будет модифицирован для работы с BFF
4. Single-instance развёртывание для development

---

## 11. Критерии приёмки

### 11.1. Функциональные критерии

| # | Критерий | Метод проверки |
|---|----------|----------------|
| 1 | Инициация аутентификации через /api/auth/login | curl + проверка redirect |
| 2 | Callback обработка code от Keycloak | Интеграционный тест |
| 3 | Установка HttpOnly куки | Проверка Set-Cookie header |
| 4 | Проверка статуса /api/auth/status | curl с кукой |
| 5 | Логаут /api/auth/logout | Проверка инвалидации сессии |
| 6 | Автоматическое обновление токена | Integration test с истечением token |
| 7 | Ротация session ID | Проверка новой куки после запроса |

### 11.2. Нефункциональные критерии

| # | Критерий | Метод проверки |
|---|----------|----------------|
| 1 | Время отклика < 200ms | JMeter/curl тест |
| 2 | HttpOnly + Secure куки | Анализ Set-Cookie |
| 3 | Много-instance развёртывание | Запуск 2+ инстансов |
| 4 | Graceful degradation | Отключение Redis |

### 11.3. Тестовые сценарии

**TC-3.1**: Успешная аутентификация
- Шаги: GET /api/auth/login → Login → Callback
- Ожидаемый результат: Установлена сессионная кука, редирект на главную

**TC-3.2**: Проверка статуса без куки
- Шаги: GET /api/auth/status (без Cookie header)
- Ожидаемый результат: 401 Unauthorized

**TC-3.3**: Проверка статуса с кукой
- Шаги: GET /api/auth/status (с валидной кукой)
- Ожидаемый результат: 200 OK с данными пользователя

**TC-3.4**: Обновление токена
- Шаги: Дождаться истечения access token, сделать запрос
- Ожидаемый результат: Автоматическое обновление токена

**TC-3.5**: Логаут
- Шаги: POST /api/auth/logout
- Ожидаемый результат: Сессия удалена, кука инвалидирована

---

## 12. Чек-лист выполнения

### Backend (bionicpro-auth)
- [ ] Создать Spring Boot проект
- [ ] Настроить pom.xml с зависимостями
- [ ] Настроить application.yml
- [ ] Реализовать Keycloak OAuth2 Client конфигурацию
- [ ] Реализовать SessionService
- [ ] Реализовать AuthController с /login, /callback, /status, /logout
- [ ] Настроить Spring Security с HttpOnly куками
- [ ] Реализовать автоматическое обновление токенов
- [ ] Реализовать ротацию сессий
- [ ] Настроить Redis Session Store
- [ ] Добавить fallback на in-memory сессии
- [ ] Написать unit-тесты

### Keycloak
- [ ] Создать confidential клиент bionicpro-auth
- [ ] Настроить client credentials flow
- [ ] Установить access token lifetime = 2 минуты
- [ ] Настроить redirect URIs

### Frontend (модификация в рамках задачи 3)
- [ ] Убрать direct keycloak-js инициализацию
- [ ] Добавить axios с interceptors для куки
- [ ] Обновить вызовы API на bionicpro-auth

### Docker
- [ ] Добавить bionicpro-auth в docker-compose.yaml
- [ ] Настроить зависимости (keycloak → redis → auth)
- [ ] Проверить networking