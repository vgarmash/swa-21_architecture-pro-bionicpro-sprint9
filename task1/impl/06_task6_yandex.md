# Техническое задание: Задача 6. Интеграция Яндекс ID как Identity Provider

## 1. Название задачи и её цели

### Название
**Настройка Яндекс ID как внешнего Identity Provider в Keycloak**

### Цели
1. Реализовать аутентификацию через Яндекс ID (OAuth 2.0/OIDC)
2. Настроить механизм consent (согласие пользователя)
3. Обеспечить получение и сохранение данных профиля из Яндекса
4. Интегрировать Identity Brokering с BFF сервисом

---

## 2. Функциональные требования

### 2.1. Настройка Яндекс ID в Keycloak

#### 2.1.1. Регистрация приложения в Яндекс ID
- **Требование**: Создать приложение в Яндекс Console
- **URL**: https://console.yandex.ru/
- **Параметры приложения**:

| Параметр | Значение |
|----------|----------|
| Название | BionicPRO Auth |
| Callback URL | http://localhost:8080/realms/reports-realm/broker/yandex/endpoint |
| Доступы | Яндекс ID (OpenID) |
| Email | Получение адреса электронной почты |
| Avatar | Получение аватарки пользователя |

- **Client ID**: Получить из Яндекс Console
- **Client Secret**: Получить из Яндекс Console

#### 2.1.2. Добавление Identity Provider в Keycloak
- **Требование**: Настроить Яндекс как Identity Broker
- **Путь**: Identity Providers → Add provider → OpenID Connect v1.0
- **Параметры**:

| Параметр | Значение |
|----------|----------|
| Alias | yandex |
| Display Name | Yandex ID |
| Client ID | [из Яндекс Console] |
| Client Secret | [из Яндекс Console] |
| Authorization URL | https://oauth.yandex.ru/authorize |
| Token URL | https://oauth.yandex.ru/token |
| User Info URL | https://login.yandex.ru/info |
| Logout URL | https://oauth.yandex.ru/revoke_token |
| Backchannel Logout | Enabled |
| Scopes | openid email profile |

#### 2.1.3. Trust Email
- **Требование**: Настроить доверие к email от Яндекса
- **Значение**: Enabled (т.к. Яндекс верифицирует email)

### 2.2. Настройка Consent (Согласие пользователя)

#### 2.2.1. Consent Screen
- **Требование**: Keycloak должен запрашивать согласие пользователя
- **Параметры**:

| Параметр | Значение |
|----------|----------|
| Consent Required | ON |
| Consent Required For Default Scopes | ON |
| Display Client Scopes | ON |
| Stored Consent Required | ON |

#### 2.2.2. Настраиваемые разрешения
- **Требование**: Пользователь должен дать согласие на использование данных
- **Запрашиваемые scopes**:
  - `openid` - идентификация
  - `email` - email адрес
  - `profile` - имя, аватар

### 2.3. Маппинг атрибутов

#### 2.3.1. User Attribute Mappers
- **Требование**: Настроить соответствие атрибутов Яндекса и Keycloak
- **Путь**: Identity Provider → Mappers → Add mapper

| Keycloak Attribute | Яндекс Attribute | Mapper Type |
|-------------------|------------------|---------------|
| email | email | Hardcoded attribute |
| firstName | first_name | Hardcoded attribute |
| lastName | last_name | Hardcoded attribute |
| username | login | Hardcoded attribute |
| avatar | default_avatar_id | Hardcoded attribute |

### 2.4.firstBrokerLoginFlow

#### 2.4.1. Настройка First Broker Login Flow
- **Требование**: При первом входе через Яндекс создать/связать аккаунт
- **Flow**: 
  1. Яндекс ID аутентификация (альтернативный)
  2. Create User If Unique (условный)
  3. Confirm Link Existing Account (альтернативный)
  4. Terms and Conditions (условный)
  5. OTP (если настроен в Задаче 5)

### 2.5. Сохранение данных профиля

#### 2.5.1. Получение данных профиля
- **Требование**: После аутентификации получить данные из Яндекса
- **Endpoint**: `https://login.yandex.ru/info`
- **Параметры**:
  - `format=json`
  - `id_token` или access_token

**Ответ Яндекса**:
```json
{
  "id": "123456789",
  "login": "username",
  "first_name": "Иван",
  "last_name": "Иванов",
  "default_email": "username@yandex.ru",
  "emails": ["username@yandex.ru"],
  "default_avatar_id": "12345"
}
```

#### 2.5.2. Сохранение в БД
- **Требование**: Сохранить данные профиля в Keycloak user attributes
- **Атрибуты**:
  - `yandex_id` = ID из Яндекса
  - `yandex_login` = login
  - `yandex_avatar` = avatar URL
  - `yandex_email` = verified email

---

## 3. Нефункциональные требования

### 3.1. Производительность
- **Время аутентификации**: < 3 секунд (включая редирект)
- **Token refresh**: < 500ms

### 3.2. Надёжность
- **Availability**: 99.9%
- **Graceful degradation**: При недоступности Яндекса - fallback на локальную аутентификацию

### 3.3. Безопасность
- **State parameter**: Обязательная валидация
- **Redirect URI**: Точное совпадение (без wildcards)
- **Token storage**: Refresh token хранится на сервере (не у клиента)

---

## 4. Архитектурные решения

### 4.1. Поток аутентификации через Яндекс ID

```
┌──────────┐                               ┌──────────┐
│  Client  │                               │ Keycloak │
└────┬─────┘                               └────┬─────┘
     │                                           │
     │  1. Login with Yandex                     │
     ├─────────────────────────────────────────►│
     │                                           │
     │  2. Redirect to Yandex OAuth              │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  3. User authenticates in Yandex         │
     │──────────────────────────────────────────►│
     │                                           │
     │  4. Yandex Consent Screen                │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  5. User consents                        │
     │──────────────────────────────────────────►│
     │                                           │
     │  6. Redirect with code                    │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  7. Exchange code for tokens             │
     │──────────────────────────────────────────►│
     │                                           │
     │  8. Get user profile from Yandex         │
     │──────────────────────────────────────────►│
     │                                           │
     │  9. Create/Link user in Keycloak         │
     │◄──────────────────────────────────────────┤
     │                                           │
     │  10. Issue tokens + Consent saved        │
     │◄──────────────────────────────────────────┤
```

### 4.2. Компонентная диаграмма

```
┌─────────────────────────────────────────────────────────────────┐
│                         Keycloak                                 │
│  ┌─────────────────┐  ┌──────────────────┐                     │
│  │  Identity      │  │   Broker        │                     │
│  │  Provider     │◄─┤   Protocol       │                     │
│  │  (Yandex)    │  │   Mappers        │                     │
│  └────────┬────────┘  └──────────────────┘                     │
           │                                                     │
           ▼                                                     │
┌─────────────────────────────────────────────────────────────────┐
│                    Yandex OAuth 2.0                              │
│  ┌─────────────────┐  ┌──────────────────┐                     │
│  │  Authorization │  │   User Info     │                     │
│  │  Endpoint      │  │   Endpoint      │                     │
│  └─────────────────┘  └──────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Модель данных

### 5.1. Keycloak Identity Provider Entity

**Таблица**: `identity_provider`

| Поле | Тип | Описание |
|------|-----|----------|
| realm_id | VARCHAR | ID realm |
| provider_id | VARCHAR | "yandex" |
| provider_alias | VARCHAR | "yandex" |
| enabled | BOOLEAN | true |
| store_token | BOOLEAN | false (refresh token) |
| add_read_token_role_on_create | BOOLEAN | false |
| config | TEXT | JSON конфигурации |

### 5.2. Identity Provider Mapper

**Таблица**: `identity_provider_mapper`

| Поле | Тип | Описание |
|------|-----|----------|
| id | UUID | ID mapper |
| identity_provider_id | UUID | FK на identity_provider |
| name | VARCHAR | Имя маппера |
| identity_provider_alias | VARCHAR | "yandex" |
| mapper_type | VARCHAR | "hardcoded" |
| config | TEXT | JSON конфигурации |

### 5.3. User Attributes для Яндекса

| Атрибут | Тип | Описание |
|---------|-----|----------|
| yandex_id | String | ID пользователя в Яндексе |
| yandex_login | String | Логин Яндекса |
| yandex_email | String | Email (верифицированный) |
| yandex_avatar | String | URL аватарки |

---

## 6. API-спецификация

### 6.1. Authorization Endpoint Яндекса

**URL**: `https://oauth.yandex.ru/authorize`

**Parameters**:
| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| response_type | string | Да | "code" |
| client_id | string | Да | ID приложения |
| redirect_uri | string | Да | Callback URL |
| scope | string | Да | "openid email profile" |
| state | string | Рекомендуется | CSRF protection |
| force_confirm | boolean | Нет | Показать экран согласия |

### 6.2. Token Endpoint Яндекса

**URL**: `https://oauth.yandex.ru/token`

**Request**:
```
POST https://oauth.yandex.ru/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=AUTHORIZATION_CODE&
client_id=CLIENT_ID&
client_secret=CLIENT_SECRET
```

**Response**:
```json
{
  "access_token": "...",
  "expires_in": 8760,
  "refresh_token": "...",
  "token_type": "bearer"
}
```

### 6.3. User Info Endpoint Яндекса

**URL**: `https://login.yandex.ru/info`

**Request**:
```
GET https://login.yandex.ru/info?format=json
Authorization: Bearer ACCESS_TOKEN
```

**Response**:
```json
{
  "id": "123456789",
  "login": "username",
  "first_name": "Иван",
  "last_name": "Иванов",
  "default_email": "username@yandex.ru",
  "emails": ["username@yandex.ru"],
  "default_avatar_id": "12345",
  "is_avatar_empty": false
}
```

---

## 7. Сценарии использования

### 7.1. Сценарий 1: Первый вход через Яндекс ID

```
1. Пользователь нажимает "Войти с Яндексом"
2. BFF редиректит на Keycloak с указанием IdP
3. Keycloak редиректит на Яндекс OAuth
4. Пользователь вводит credentials в Яндексе
5. Яндекс показывает экран согласия (consent)
6. Пользователь соглашается
7. Яндекс редиректит в Keycloak с code
8. Keycloak обменивает code на tokens
9. Keycloak получает user info от Яндекса
10. Keycloak создаёт нового пользователя
11. Keycloak запрашивает consent (если ещё не дан)
12. Keycloak возвращает токены
```

### 7.2. Сценарий 2: Повторный вход через Яндекс ID

```
1. Пользователь нажимает "Войти с Яндексом"
2. BFF редиректит на Keycloak с указанием IdP
3. Keycloak редиректит на Яндекс OAuth
4. Пользователь вводит credentials в Яндексе
5. Яндекс не показывает consent (уже дан ранее)
6. Яндекс редиректит в Keycloak с code
7. Keycloak находит существующего пользователя по yandex_id
8. Keycloak обновляет данные профиля
9. Keycloak возвращает токены
```

### 7.3. Сценарий 3: Link аккаунтов

```
1. Пользователь вошёл через локальный аккаунт
2. Пользователь хочет привязать Яндекс ID
3. Пользователь переходит в настройки профиля
4. Нажимает "Привязать Яндекс ID"
5. Аналогично Сценарию 1, но связывает с существующим аккаунтом
6. В профиле появляется linked identity
```

---

## 8. Зависимости от внешних систем

| Система | Версия | Назначение |
|---------|--------|------------|
| Яндекс ID | OAuth 2.0 | Внешний Identity Provider |
| Keycloak | 21.1+ | Identity Broker |
| BFF (bionicpro-auth) | 1.0+ | Интеграция с Keycloak |

---

## 9. Ограничения и допущения

### 9.1. Ограничения
1. **Яндекс account**: Требует верифицированного аккаунта
2. **Email verification**: Яндекс гарантирует верификацию email
3. **Profile data**: Ограниченный набор данных от Яндекса

### 9.2. Допущения
1. Яндекс OAuth endpoint доступен
2. Client credentials получены и валидны
3. Callback URL настроен корректно

---

## 10. Критерии приёмки

### 10.1. Функциональные критерии

| # | Критерий | Метод проверки |
|---|----------|----------------|
| 1 | Яндекс ID добавлен в Keycloak | Проверка Identity Providers |
| 2 | Аутентификация через Яндекс работает | Тестовая аутентификация |
| 3 | Consent screen отображается | Проверка при первом входе |
| 4 | Данные профиля сохраняются | Проверка user attributes |
| 5 | Email получен от Яндекса | Проверка в профиле пользователя |
| 6 | Повторный вход без consent | Тестовая аутентификация |
| 7 | BFF работает с Яндекс ID | Интеграционный тест |

### 10.2. Тестовые сценарии

**TC-6.1**: Первый вход через Яндекс ID
- Шаги: Нажать "Войти с Яндексом" → Аутентификация → Consent
- Ожидаемый результат: Создан новый пользователь, токены получены

**TC-6.2**: Проверка данных профиля
- Шаги: После входа проверить атрибуты пользователя
- Ожидаемый результат: email, firstName, lastName заполнены

**TC-6.3**: Consent уже дан
- Шаги: Повторный вход через Яндекс ID
- Ожидаемый результат: Consent screen не показывается

**TC-6.4**: Link аккаунтов
- Шаги: Существующий пользователь привязывает Яндекс
- Ожидаемый результат: Два identity linked

**TC-6.5**: Отзыв consent
- Шаги: Админ отзывает consent в Keycloak
- Ожидаемый результат: При следующем входе consent показан

---

## 11. Чек-лист выполнения

### Яндекс Console
- [ ] Создать приложение в Яндекс Console
- [ ] Настроить Callback URL
- [ ] Получить Client ID и Client Secret
- [ ] Включить необходимые доступы (email, profile)

### Keycloak
- [ ] Добавить Identity Provider (OpenID Connect)
- [ ] Настроить Client ID и Secret
- [ ] Настроить Authorization/Token/User Info URLs
- [ ] Включить Trust Email
- [ ] Настроить Consent Required
- [ ] Создать Attribute Mappers
- [ ] Проверить First Broker Login Flow

### BFF Integration
- [ ] Обновить login endpoint для поддержки IdP
- [ ] Протестировать полный флоу
- [ ] Проверить получение данных профиля

### Testing
- [ ] Тест первый вход
- [ ] Тест повторный вход
- [ ] Тест consent
- [ ] Тест link аккаунтов