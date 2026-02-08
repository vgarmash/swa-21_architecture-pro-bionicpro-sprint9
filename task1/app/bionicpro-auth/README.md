# BionicPRO Auth BFF Service

BFF (Backend for Frontend) сервис для управления аутентификацией пользователей в системе BionicPRO с использованием PKCE (Proof Key for Code Exchange) и OAuth2.

## Принцип работы сервиса

Сервис действует как посредник между фронтендом и Keycloak для обеспечения безопасной аутентификации пользователей. Он реализует следующий процесс:

1. **Инициация аутентификации**: При запросе на /auth/login сервис генерирует PKCE параметры (code verifier и code challenge), сохраняет code verifier во временной сессии и перенаправляет пользователя на Keycloak для аутентификации.
2. **Обработка callback**: После успешной аутентификации Keycloak перенаправляет пользователя обратно на сервис с authorization code. Сервис использует этот код вместе с code verifier для получения access и refresh токенов от Keycloak.
3. **Управление сессиями**: Полученные токены шифруются и сохраняются в Redis. Сервис создает сессию с идентификатором, который используется для управления доступом к защищенным ресурсам.
4. **Обновление токенов**: При необходимости сервис может обновлять access токены с использованием refresh токенов, которые хранятся в зашифрованном виде.
5. **Выход пользователя**: При выходе пользователя сервис инвалидирует сессию и перенаправляет пользователя на Keycloak для завершения сессии.

## Ключевые особенности

- **PKCE для безопасности**: Использует Proof Key for Code Exchange (PKCE) для защиты от authorization code injection атак
- **OAuth2 с Keycloak**: Интеграция с Keycloak для управления аутентификацией и авторизацией пользователей
- **Redis для сессий**: Использует Redis для хранения информации о сессиях пользователей
- **JWT токены**: Работает с JWT токенами для аутентификации и авторизации
- **Шифрование токенов**: Токены хранятся в зашифрованном виде для безопасности
- **Поддержка refresh токенов**: Автоматическое обновление access токенов
- **Конфигурируемая безопасность**: Настройка cookie и других параметров безопасности

## Основные параметры конфигурации

### Основные параметры сервиса

| Параметр | Описание | Пример значения |
|----------|----------|-----------------|
| `server.port` | Порт, на котором запускается сервис | `8081` |
| `spring.data.redis.host` | Хост Redis сервера | `redis` |
| `spring.data.redis.port` | Порт Redis сервера | `6379` |
| `spring.session.store-type` | Тип хранения сессий | `redis` |
| `spring.session.timeout` | Время жизни сессии | `10m` |

### Параметры Keycloak

| Параметр | Описание | Пример значения |
|----------|----------|-----------------|
| `bff.keycloak.issuer-uri` | URI Keycloak issuer | `http://keycloak:8080/realms/reports-realm` |
| `bff.keycloak.token-uri` | URI для получения токенов | `http://keycloak:8080/realms/reports-realm/protocol/openid-connect/token` |
| `bff.keycloak.logout-uri` | URI для выхода | `http://keycloak:8080/realms/reports-realm/protocol/openid-connect/logout` |
| `spring.security.oauth2.client.registration.keycloak.client-id` | ID клиента Keycloak | `bionicpro-auth-backend` |
| `spring.security.oauth2.client.registration.keycloak.client-secret` | Секрет клиента Keycloak | `your-client-secret` |

### Параметры фронтенда

| Параметр | Описание | Пример значения |
|----------|----------|-----------------|
| `bff.frontend.redirect-uri` | URI перенаправления после аутентификации | `http://localhost:3000` |

### Параметры сессий

| Параметр | Описание | Пример значения |
|----------|----------|-----------------|
| `bff.session.cookie-domain` | Домен cookie сессии | `localhost` |
| `bff.session.cookie-path` | Путь cookie сессии | `/` |

### Параметры шифрования

| Параметр | Описание | Пример значения |
|----------|----------|-----------------|
| `bff.encryption.key` | Ключ шифрования для токенов | `your-encryption-key` |

### Параметры безопасности

| Параметр | Описание | Пример значения |
|----------|----------|-----------------|
| `server.servlet.session.cookie.name` | Имя cookie сессии | `BIONICPRO_SESSION` |
| `server.servlet.session.cookie.http-only` | Флаг HttpOnly для cookie | `true` |
| `server.servlet.session.cookie.secure` | Флаг Secure для cookie | `true` |
| `server.servlet.session.cookie.same-site` | SameSite политика cookie | `strict` |