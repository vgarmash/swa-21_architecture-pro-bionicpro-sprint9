# Task 6: Интеграция Яндекс ID как Identity Provider

## Дата выполнения
2026-02-17

## Цель
Добавить Яндекс ID как Identity Provider (IdP) в Keycloak для realm `reports-realm`.

## Выполненные работы

### 1. Обновление конфигурации Keycloak

Добавлена секция `identityProviders` в файл [`app/keycloak/realm-export.json`](app/keycloak/realm-export.json):

#### Конфигурация Identity Provider (OIDC)

```json
{
  "alias": "yandex",
  "displayName": "Yandex ID",
  "providerId": "oidc",
  "enabled": true,
  "linkOnly": false,
  "storeToken": false,
  "addReadTokenRoleOnCreate": false,
  "trustEmail": false,
  "skipOAuthConsent": false,
  "authorizeBy": "CONFIGURED",
  "config": {
    "clientId": "YANDEX_CLIENT_ID",
    "clientSecret": "YANDEX_CLIENT_SECRET",
    "authorizationUrl": "https://oauth.yandex.ru/authorize",
    "tokenUrl": "https://oauth.yandex.ru/token",
    "userInfoUrl": "https://login.yandex.ru/info",
    "issuer": "https://oauth.yandex.ru",
    "scopes": "login:email login:info",
    "useJwksUrl": "false",
    "backchannelSupported": "false",
    "disableUserInfo": "false",
    "connectionTimeout": "2000",
    "readTimeout": "2000",
    "clientAuthenticationMethod": "client_secret_post"
  }
}
```

### 2. Настроенные параметры подключения

| Параметр | Значение |
|----------|----------|
| Authorization URL | https://oauth.yandex.ru/authorize |
| Token URL | https://oauth.yandex.ru/token |
| UserInfo URL | https://login.yandex.ru/info |
| Client ID | YANDEX_CLIENT_ID (placeholder) |
| Client Secret | YANDEX_CLIENT_SECRET (placeholder) |
| Scopes | login:email login:info |
| Client Authentication Method | client_secret_post |

### 3. Consent Screen

- Параметр `skipOAuthConsent: false` - экран согласия пользователя включён
- Пользователь должен будет подтвердить доступ к своим данным при первом входе через Яндекс ID

### 4. Timeout

- `connectionTimeout: 2000` - 2000ms (требование ТЗ выполнено)
- `readTimeout: 2000` - 2000ms (требование ТЗ выполнено)

### 5. Маппинг атрибутов

Добавлены identity provider mappers для следующих атрибутов:

| Атрибут Keycloak | Атрибут Яндекса |
|------------------|-----------------|
| email | default_email |
| username | login |
| firstName | first_name |
| lastName | last_name |

```json
{
  "identityProviderMappers": [
    {
      "name": "yandex-email-mapper",
      "identityProviderAlias": "yandex",
      "identityProviderMapper": "oidc-user-attribute-idp-mapper",
      "config": {
        "user.attribute": "email",
        "claim.name": "default_email"
      }
    },
    {
      "name": "yandex-username-mapper",
      "identityProviderAlias": "yandex",
      "identityProviderMapper": "oidc-user-attribute-idp-mapper",
      "config": {
        "user.attribute": "username",
        "claim.name": "login"
      }
    },
    {
      "name": "yandex-firstname-mapper",
      "identityProviderAlias": "yandex",
      "identityProviderMapper": "oidc-user-attribute-idp-mapper",
      "config": {
        "user.attribute": "firstName",
        "claim.name": "first_name"
      }
    },
    {
      "name": "yandex-lastname-mapper",
      "identityProviderAlias": "yandex",
      "identityProviderMapper": "oidc-user-attribute-idp-mapper",
      "config": {
        "user.attribute": "lastName",
        "claim.name": "last_name"
      }
    }
  ]
}
```

## Соответствие требованиям ТЗ

| Требование | Статус | Примечание |
|-----------|--------|------------|
| OIDC Identity Broker | ✅ | Используется OIDC протокол |
| Consent screen | ✅ | `skipOAuthConsent: false` |
| Маппинг атрибутов | ✅ | Настроены 4 маппера |
| Timeout ≤2000ms | ✅ | Установлено 2000ms |

## Настройка Client ID и Client Secret

Для использования интеграции необходимо:

1. Зарегистрировать приложение на https://oauth.yandex.ru/
2. Получить Client ID и Client Secret
3. Обновить значения в `app/keycloak/realm-export.json`:
   - Заменить `YANDEX_CLIENT_ID` на полученный Client ID
   - Заменить `YANDEX_CLIENT_SECRET` на полученный Client Secret
4. Настроить Redirect URI в Яндекс OAuth: `http://localhost:8080/realms/reports-realm/broker/yandex/endpoint`

## Файлы проекта

- [`app/keycloak/realm-export.json`](app/keycloak/realm-export.json) - Основной файл конфигурации Keycloak с добавленными identity providers и mappers
