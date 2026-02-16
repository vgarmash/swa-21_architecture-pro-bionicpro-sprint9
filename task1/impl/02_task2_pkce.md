# Техническое задание: Задача 2. Внедрение PKCE для безопасной аутентификации

## 1. Название задачи и её цели

### Название
**Внедрение PKCE (Proof Key for Code Exchange) для фронтенд-приложения BionicPRO**

### Цели
1. Устранить уязвимость существующего OAuth 2.0 Code Grant потока
2. Защитить от атак по перехвату кода авторизации (code interception attack)
3. Обеспечить безопасную аутентификацию для SPA-приложений
4. Подготовить инфраструктуру для BFF-сервиса (Задача 3)

---

## 2. Функциональные требования

### 2.1. Настройка Keycloak

#### 2.1.1. Включение PKCE для клиента reports-frontend
- **Требование**: Включить обязательный PKCE для клиента `reports-frontend` в Keycloak
- **Значение**: Установить `Proof Key Code Exchange Code Challenge Method` = `S256` (SHA-256)
- **Обоснование**: S256 более безопасен чем plain

#### 2.1.2. Отключение небезопасных потоков
- **Требование**: Отключить `directAccessGrantsEnabled` для клиента `reports-frontend`
- **Обоснование**: Запретить Resource Owner Password Credentials Flow

#### 2.1.3. Настройка Redirect URIs
- **Требование**: Проверить и обновить список допустимых Redirect URIs
- **Значения**:
  - `http://localhost:3000/*`
  - `http://localhost:8080/*` (для dev)
  - При необходимости добавить продакшен URL

#### 2.1.4. Включение Support PKCE
- **Требование**: Включить `Support PKCE` в настройках realm
- **Путь**: Realm Settings → General → Front-channel → Support PKCE

### 2.2. Модификация фронтенд-приложения

#### 2.2.1. Обновление конфигурации Keycloak
- **Требование**: Добавить PKCE-параметры в конфигурацию keycloak-js
- **Изменения**:
  ```javascript
  const keycloakConfig = {
    url: process.env.REACT_APP_KEYCLOAK_URL,
    realm: process.env.REACT_APP_KEYCLOAK_REALM,
    clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID,
    pkceMethod: 'S256',  // Добавить
    flow: 'standard',    // Изменить с implicit на standard
  };
  ```

#### 2.2.2. Обновление библиотеки react-keycloak-web
- **Требование**: Убедиться что используется версия с поддержкой PKCE
- **Минимальная версия**: `@react-keycloak-web@3.4.0` или выше
- **Обоснование**: Более ранние версии могут не поддерживать полный PKCE-флоу

#### 2.2.3. Обработка ошибок PKCE
- **Требование**: Реализовать обработку ошибок, связанных с PKCE
- **Сценарии**:
  - `pkce_required` - PKCE обязателен
  - `pkce_invalid_verifier` - неверный code verifier
  - `invalid_code_challenge_method` - неподдерживаемый метод

#### 2.2.4. Обновление .env файла
- **Требование**: Убедиться что .env содержит правильные параметры
- **Текущие значения** (должны остаться):
  ```
  REACT_APP_API_URL=http://localhost:8000
  REACT_APP_KEYCLOAK_URL=http://localhost:8080
  REACT_APP_KEYCLOAK_REALM=reports-realm
  REACT_APP_KEYCLOAK_CLIENT_ID=reports-frontend
  ```

### 2.3. Тестирование

#### 2.3.1. Функциональное тестирование
- **Сценарий 1**: Успешная аутентификация с PKCE
  1. Пользователь открывает приложение
  2. Перенаправление на Keycloak login
  3. Ввод credentials
  4. Перенаправление обратно в приложение
  5. Приложение получает токены через code exchange
  6. Успешный вход в систему

- **Сценарий 2**: Отклонение аутентификации без PKCE
  1. Злоумышленник пытается использовать старый flow без PKCE
  2. Keycloak отклоняет запрос
  3. Ошибка отображается пользователю

---

## 3. Нефункциональные требования

### 3.1. Производительность
- **Время отклика**: Аутентификация не должна превышать 3 секунд (без учёта пользовательского ввода)
- **Нагрузка**: Система должна поддерживать 100 одновременных аутентификаций

### 3.2. Надёжность
- **Доступность**: Keycloak должен быть доступен 99.9% времени
- **Резервное копирование**: Конфигурация Keycloak должна экспортироваться

### 3.3. Безопасность
- **Алгоритм**: Только S256 (SHA-256) для code challenge
- **Code verifier**: Минимальная длина 43 символа, максимальная 128
- **Code challenge**: Генерируется из code verifier через SHA-256 + base64url encoding

---

## 4. Архитектурные решения

### 4.1. PKCE Flow (Authorization Code with PKCE)

```
┌──────────┐                               ┌──────────┐
│   SPA    │                               │ Keycloak │
└────┬─────┘                               └────┬─────┘
     │                                           │
     │  1. /auth?code_challenge=...             │
     │     &code_challenge_method=S256          │
     │     &response_type=code                   │
     ├─────────────────────────────────────────►│
     │                                           │
     │  2. Login Page                            │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  3. User Credentials                      │
     │──────────────────────────────────────────►│
     │                                           │
     │  4. Redirect with code                    │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  5. /token                                │
     │     code_verifier=...                     │
     ├──────────────────────────────────────────►│
     │                                           │
     │  6. Access Token + Refresh Token          │
     │◄──────────────────────────────────────────┤
```

### 4.2. Компоненты для модификации

| Компонент | Файл | Изменение |
|-----------|------|-----------|
| Keycloak Client | `app/keycloak/realm-export.json` | Добавить `pkceCodeChallengeMethod`, отключить directAccessGrants |
| Frontend Config | `app/frontend/src/App.tsx` | Добавить pkceMethod: 'S256' |
| Frontend Package | `app/frontend/package.json` | Обновить версию react-keycloak-web |

---

## 5. Модель данных

### 5.1. Конфигурация Keycloak Realm

**Файл**: `app/keycloak/realm-export.json`

**Изменения в client**:
```json
{
  "clientId": "reports-frontend",
  "publicClient": true,
  "directAccessGrantsEnabled": false,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "pkceCodeChallengeMethod": "S256",
  "redirectUris": [
    "http://localhost:3000/*",
    "http://localhost:8080/*"
  ],
  "webOrigins": [
    "http://localhost:3000",
    "http://localhost:8080"
  ]
}
```

---

## 6. Зависимости

### 6.1. Системные зависимости
- Keycloak версии 21.1 или выше
- Node.js 18+ (для фронтенда)
- npm или yarn

### 6.2. NPM зависимости
- `@react-keycloak-web` версии 3.4.0+
- `keycloak-js` версии 21.0.0+

---

## 7. Ограничения и допущения

### 7.1. Ограничения
1. **Обратная совместимость**: После включения PKCE старые клиенты без PKCE не смогут аутентифицироваться
2. **Browser support**: PKCE поддерживается во всех современных браузерах (IE11 не поддерживается)

### 7.2. Допущения
1. Keycloak работает в режиме dev (для production потребуется дополнительная настройка SSL)
2. Фронтенд доступен по localhost:3000
3. Используется стандартный поток (standard flow), не implicit

---

## 8. Критерии приёмки

### 8.1. Функциональные критерии

| # | Критерий | Метод проверки |
|---|----------|----------------|
| 1 | PKCE включён в Keycloak | Проверка realm-export.json |
| 2 | directAccessGrants отключён | Проверка в Keycloak admin console |
| 3 | Фронтенд использует S256 | Проверка конфигурации keycloak-js |
| 4 | Успешная аутентификация | Ручное тестирование в браузере |
| 5 | Ошибка при отключённом PKCE | Попытка аутентификации без PKCE |

### 8.2. Тестовые сценарии

**TC-2.1**: Успешный вход с PKCE
- Ожидаемый результат: Пользователь успешно аутентифицирован, токены получены через code exchange

**TC-2.2**: Проверка code_challenge_method
- Ожидаемый результат: Method = S256

**TC-2.3**: Защита от атаки с перехватом кода
- Ожидаемый результат: Запрос без code_verifier отклоняется Keycloak

---

## 9. Чек-лист выполнения

- [ ] Обновить realm-export.json: включить PKCE для client reports-frontend
- [ ] Обновить realm-export.json: отключить directAccessGrantsEnabled
- [ ] Обновить App.tsx: добавить pkceMethod: 'S256'
- [ ] Проверить/обновить версию react-keycloak-web в package.json
- [ ] Пересобрать и запустить Keycloak с новой конфигурацией
- [ ] Пересобрать фронтенд-приложение
- [ ] Протестировать аутентификацию
- [ ] Документировать изменения