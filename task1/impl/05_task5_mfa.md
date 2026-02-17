# Техническое задание: Задача 5. Настройка многофакторной аутентификации (MFA)

## 1. Название задачи и её цели

### Название
**Настройка Mandatory OTP-аутентификации в Keycloak**

### Цели
1. Обеспечить дополнительный уровень безопасности для всех пользователей
2. Настроить обязательную OTP-аутентификацию (Time-based One-Time Password)
3. Интегрировать поддерживаемые приложения (Google Authenticator, FreeOTP, Authy)
4. Обеспечить соответствие требованиям безопасности для медицинских данных

---

## 2. Функциональные требования

### 2.1. Настройка OTP в Keycloak

#### 2.1.1. Включение OTP-механизма
- **Требование**: Активировать OTP-аутентификацию в realm settings
- **Путь**: Authentication → Flows → OTP
- **Настройки**:

| Параметр | Значение |
|----------|----------|
| OTP Type | Time based |
| OTP Algorithm | SHA256 |
| Digits | 6 |
| Initial Counter | 0 |
| Step | 30 |
| Look Ahead Window | 1 |

#### 2.1.2. Настройка OTP Policy
- **Требование**: Настроить политику OTP для realm
- **Путь**: Realm Settings → Security Defenses → Password Policy
- **Добавить policy**: OTP Policy

| Параметр | Значение |
|----------|----------|
| OTP Hashing Iterations | 1 |
| Minimum OTP Length | 6 |
| Digits | 6 |
| Counter | 0 |
| Period | 30 |
| Algorithm | SHA256 |

#### 2.1.3. Обязательная OTP для всех пользователей
- **Требование**: Все пользователи должны использовать OTP
- **Реализация**: Настроить required action для realm

### 2.2. Конфигурация Authentication Flows

#### 2.2.1. Browser Flow (модификация)
- **Требование**: Добавить OTP после успешного входа
- **Текущий flow**: Username Password Form → OTP Form
- **Модификация**:

```
1. Username Password Form (альтернативный)
   ↓ success
2. OTP Form (обязательный) ← ДОБАВИТЬ
   ↓ success
3. Accept Terms Session (условный)
   ↓ success
4. Cookie
   ↓ success
5. Deny Access (альтернативный)
```

#### 2.2.2. Direct Grant Flow (отключение или добавление OTP)
- **Требование**: Если используется - добавить OTP
- **Рекомендация**: Отключить direct access grants (уже сделано в Задаче 2)

### 2.3. Required Actions

#### 2.3.1. Configure OTP
- **Требование**: Настроить required action для принудительной настройки OTP
- **Путь**: Authentication → Required Actions
- **Действия**:
  1. Включить "Configure OTP" required action
  2. Сделать его default для новых пользователей

#### 2.3.2. Конфигурация для существующих пользователей
- **Требование**: Существующие пользователи должны настроить OTP
- **Сценарий**: 
  - При следующем входе пользователь будет перенаправлен на настройку OTP
  - Пользователь должен отсканировать QR-код в приложении
  - После настройки пользователь может продолжить работу

### 2.4. Интеграция с приложениями

#### 2.4.1. Поддерживаемые приложения
- **Google Authenticator** (iOS, Android)
- **FreeOTP** (iOS, Android)
- **Microsoft Authenticator** (iOS, Android)
- **Authy** (iOS, Android, Desktop)

#### 2.4.2. TOTP стандарт
- **Стандарт**: RFC 6238 (Time-based One-Time Password)
- **Параметры**:
  - Алгоритм: HMAC-SHA-256
  - Длина: 6 цифр
  - Период: 30 секунд

---

## 3. Нефункциональные требования

### 3.1. Производительность
- **Время валидации OTP**: < 50ms
- **Время генерации QR**: < 100ms

### 3.2. Надёжность
- **Availability**: 99.9%
- **Graceful degradation**: При недоступности OTP валидатора - fallback на другой механизм не предусмотрен (MFA обязателен)

### 3.3. Безопасность
- **TOTP стандарт**: RFC 6238 compliant
- **Секрет**: Генерируется случайно (криптографически стойкий)
- **Look-ahead window**: 1 период (30 сек)
- **Brute-force protection**: Ограничение попыток

---

## 4. Архитектурные решения

### 4.1. Поток аутентификации с OTP

```
┌──────────┐                               ┌──────────┐
│  Client  │                               │ Keycloak │
└────┬─────┘                               └────┬─────┘
     │                                           │
     │  1. Login Request                        │
     ├─────────────────────────────────────────►│
     │                                           │
     │  2. Username/Password Form               │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  3. Credentials                          │
     │──────────────────────────────────────────►│
     │                                           │
     │  4. OTP Form (если не настроен - QR)     │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  5. OTP Code                             │
     │──────────────────────────────────────────►│
     │                                           │
     │  6. Success + Tokens                     │
     │◄──────────────────────────────────────────┤
```

### 4.2. Компонентная диаграмма

```
┌─────────────────────────────────────────────────────────────────┐
│                         Keycloak                                 │
│  ┌─────────────────┐  ┌──────────────────┐                     │
│  │ Authentication  │  │   OTP Policy    │                     │
│  │    Flows       │  │                  │                     │
│  │ - Browser      │  │ - Algorithm:SHA1│                     │
│  │ - Direct Grant │  │ - Digits: 6     │                     │
│  └────────┬────────┘  │ - Period: 30   │                     │
           │            └──────────────────┘                     │
           ▼                                                     │
┌─────────────────────────────────────────────────────────────────┐
│                      TOTP Provider                               │
│  ┌─────────────────┐  ┌──────────────────┐                     │
│  │  Credential    │  │   TOTP          │                     │
│  │  Manager       │  │   Generator     │                     │
│  └─────────────────┘  └──────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────────┐
│               User's Device (Authenticator App)                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  TOTP Algorithm (HMAC-SHA1)                                ││
│  │  Secret Key (Base32)                                       ││
│  │  Display: 123 456 (changes every 30s)                      ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Модель данных

### 5.1. OTP Credential в Keycloak

**Таблица**: `credential`

| Поле | Тип | Описание |
|------|-----|----------|
| id | UUID | ID credential |
| user_id | UUID | ID пользователя |
| type | VARCHAR | "otp" |
| user_label | VARCHAR | Метка (обычно "totp") |
| secret_data | TEXT | Зашифрованный Base32 secret |
| credential_data | TEXT | JSON с настройками (алгоритм, период) |
| priority | INT | Приоритет |
| created_date | BIGINT | Дата создания |
| user_id | UUID | Внешний ключ на пользователя |

---

## 6. API-спецификация

### 6.1. REST API Keycloak для OTP

#### Получение QR-кода для настройки
- **Метод**: `GET`
- **Путь**: `/auth/realms/{realm}/protocol/openid-connect/auth/device`
- **Заголовки**: Authorization: Bearer {access_token}
- **Ответ**:
```json
{
  "user_code": "",
  "device": "Google Authenticator",
  "qrcode_url": "otpauth://totp/...",
  "verification_uri": "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2"
}
```

#### Аутентификация с OTP
- **Метод**: `POST`
- **Путь**: `/auth/realms/{realm}/login-actions/authenticate`
- **Тело**:
```json
{
  "username": "user1",
  "password": "password123",
  "otp": "123456"
}
```

---

## 7. Сценарии использования

### 7.1. Сценарий 1: Первый вход с OTP

```
1. Пользователь вводит username/password на Keycloak
2. Keycloak проверяет credentials (успешно)
3. Keycloak видит, что OTP не настроен
4. Keycloak перенаправляет на настройку OTP
5. Пользователь видит QR-код
6. Пользователь сканирует QR-код в Google Authenticator
7. Пользователь вводит OTP код для подтверждения
8. Keycloak сохраняет OTP credential
9. Keycloak создаёт сессию и возвращает токены
```

### 7.2. Сценарий 2: Повторный вход с OTP

```
1. Пользователь вводит username/password на Keycloak
2. Keycloak проверяет credentials (успешно)
3. Keycloak видит, что OTP настроен
4. Keycloak запрашивает OTP код
5. Пользователь вводит текущий код из Google Authenticator
6. Keycloak валидирует OTP (успешно)
7. Keycloak создаёт сессию и возвращает токены
```

### 7.3. Сценарий 3: Потеря устройства с OTP

```
1. Пользователь потерял телефон с Google Authenticator
2. Пользователь обращается к администратору
3. Администратор сбрасывает OTP credential через Keycloak Admin Console
4. Пользователь при следующем входе настраивает OTP заново
```

---

## 8. Зависимости от внешних систем

| Система | Версия | Назначение |
|---------|--------|------------|
| Google Authenticator | Latest | TOTP клиент (iOS/Android) |
| FreeOTP | Latest | TOTP клиент (iOS/Android) |
| Keycloak | 21.1+ | Identity Provider с OTP |

---

## 9. Ограничения и допущения

### 9.1. Ограничения
1. **Offline OTP**: Не работает без синхронизации времени
2. **Device binding**: Привязка к конкретному устройству отсутствует
3. **Backup**: Нет встроенного backup механизма кодов

### 9.2. Допущения
1. Все пользователи имеют смартфоны с поддерживаемыми приложениями
2. Время на устройствах синхронизировано (NTP)
3. MFA обязательна для всех пользователей

---

## 10. Критерии приёмки

### 10.1. Функциональные критерии

| # | Критерий | Метод проверки |
|---|----------|----------------|
| 1 | OTP Policy настроена | Проверка в Realm Settings |
| 2 | Browser Flow включает OTP | Проверка Authentication Flows |
| 3 | Required Action включён | Проверка в Authentication |
| 4 | Новый пользователь настраивает OTP | Тестовая регистрация |
| 5 | Вход без OTP невозможен | Попытка входа без OTP |
| 6 | Google Authenticator работает | Тест с реальным приложением |
| 7 | FreeOTP работает | Тест с реальным приложением |

### 10.2. Тестовые сценарии

**TC-5.1**: Принудительная настройка OTP для нового пользователя
- Шаги: Создать пользователя → Первый вход
- Ожидаемый результат: Перенаправление на настройку OTP

**TC-5.2**: Аутентификация с валидным OTP
- Шаги: Ввести username/password + валидный OTP
- Ожидаемый результат: Успешный вход

**TC-5.3**: Аутентификация с невалидным OTP
- Шаги: Ввести username/password + невалидный OTP
- Ожидаемый результат: Ошибка "Invalid OTP"

**TC-5.4**: Сброс OTP администратором
- Шаги: Admin Console → Users → [User] → Credentials → Delete OTP
- Ожидаемый результат: Пользователь должен настроить OTP заново

**TC-5.5**: Проверка синхронизации времени
- Шаги: Изменить время на устройстве → Ввести OTP
- Обычный период (30 сек): Работает с look-ahead
- Более 30 сек разницы: Ожидаемый результат: Ошибка

---

## 11. Чек-лист выполнения

### Keycloak Configuration
- [ ] Настроить OTP Policy в Realm Settings
- [ ] Модифицировать Browser Flow - добавить OTP Form
- [ ] Включить "Configure OTP" Required Action
- [ ] Сделать "Configure OTP" Default Action

### Testing
- [ ] Протестировать первый вход нового пользователя
- [ ] Протестировать повторный вход с OTP
- [ ] Протестировать с Google Authenticator
- [ ] Протестировать с FreeOTP
- [ ] Протестировать сброс OTP
- [ ] Протестировать невалидный OTP

### Documentation
- [ ] Инструкция для пользователей по настройке OTP
- [ ] Инструкция для администраторов по сбросу OTP

---

## 12. Параметры конфигурации OTP Policy

```json
{
  "type": "totp",
  "algorithm": "SHA256",
  "digits": 6,
  "counter": 0,
  "period": 30,
  "supportedApplications": [
    "Google Authenticator",
    "FreeOTP",
    "Microsoft Authenticator",
    "Authy"
  ]
}
```

**Примечание**: Используется SHA256 вместо SHA1 для повышения безопасности.

---

## 13. Модификация Browser Flow

```
1. Cookie
2. Identity Provider Redirector
3. Username Password Form
4. OTP Form (ДОБАВИТЬ после Username Password Form)
5. Conditional OTP Form
6. Execute actions
```

---

## 14. UI

Используются стандартные экраны Keycloak для:
- Настройки OTP (сканирование QR-кода)
- Ввода одноразового пароля
- Consent screen (не требуется для OTP)

---

## 15. Критерии приёмки

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| T5.1 | RejectNoOTP | Вход без OTP отклоняется |
| T5.2 | AcceptOTP | Вход с валидным OTP успешен |
| T5.3 | FirstTimeSetup | При первом входе пользователь перенаправляется на настройку OTP |
| T5.4 | OTPAlgorithm | Проверка что используется SHA256 |