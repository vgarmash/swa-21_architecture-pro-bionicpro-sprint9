# Техническое задание: Задача 4. Интеграция LDAP с Keycloak

## 1. Название задачи и её цели

### Название
**Развёртывание OpenLDAP и интеграция с Keycloak для федерации идентичностей**

### Цели
1. Развернуть LDAP-сервер OpenLDAP для хранения учётных данных европейского представительства
2. Настроить Keycloak для синхронизации пользователей с LDAP
3. Реализовать маппинг ролей LDAP на роли Keycloak
4. Обеспечить соответствие требованиям GDPR по локализации данных

---

## 2. Функциональные требования

### 2.1. Развёртывание OpenLDAP

#### 2.1.1. Docker-контейнер OpenLDAP
- **Требование**: Развернуть OpenLDAP в Docker-контейнере
- **Образ**: `osixia/openldap:1.5.0`
- **Порт**: `389` (LDAP), `636` (LDAPS)
- **Admin DN**: `cn=admin,dc=example,dc=com`
- **Пароль администратора**: `admin` (изменить в production)

#### 2.1.2. Структура каталога LDAP
- **Требование**: Создать иерархическую структуру для пользователей и групп
- **Base DN**: `dc=example,dc=com`
- **Организационные единицы**:
  - `ou=People` - пользователи
  - `ou=Groups` - группы (роли)

```ldif
dn: dc=example,dc=com
objectClass: top
objectClass: dcObject
objectClass: organization
o: BionicPRO EU
dc: example

dn: ou=People,dc=example,dc=com
objectClass: organizationalUnit
ou: People

dn: ou=Groups,dc=example,dc=com
objectClass: organizationalUnit
ou: Groups
```

#### 2.1.3. Пользователи LDAP
- **Требование**: Создать тестовых пользователей в LDAP
- **Пользователи**:

| UID | CN | SN | Email | Password | Group |
|-----|----|----|-------|----------|-------|
| john.doe | John Doe | Doe | john.doe@example.com | password | prothetic_user |
| jane.smith | Jane Smith | Smith | jane.smith@example.com | password | user |
| alex.johnson | Alex Johnson | Johnson | alex.johnson@example.com | password | prothetic_user |
| maria.garcia | Maria Garcia | Garcia | maria.garcia@example.com | password | administrator |

#### 2.1.4. Группы LDAP
- **Требование**: Создать группы для маппинга на роли Keycloak

```ldif
dn: cn=user,ou=Groups,dc=example,dc=com
objectClass: groupOfNames
cn: user
member: uid=jane.smith,ou=People,dc=example,dc=com

dn: cn=prothetic_user,ou=Groups,dc=example,dc=com
objectClass: groupOfNames
cn: prothetic_user
member: uid=john.doe,ou=People,dc=example,dc=com
member: uid=alex.johnson,ou=People,dc=example,dc=com

dn: cn=administrator,ou=Groups,dc=example,dc=com
objectClass: groupOfNames
cn: administrator
member: uid=maria.garcia,ou=People,dc=example,dc=com
```

### 2.2. Интеграция Keycloak с LDAP

#### 2.2.1. Добавление LDAP User Federation
- **Требование**: Настроить LDAP User Federation в Keycloak
- **Путь**: User Federation → Add provider → ldap
- **Параметры**:

| Параметр | Значение |
|----------|----------|
| Console Display Name | OpenLDAP EU |
| Priority | 1 |
| Import Users | true |
| Edit Mode | UNSYNCED |
| Sync Registrations | false |
| Vendor | other |
| Username LDAP attribute | uid |
| RDN LDAP attribute | uid |
| UUID LDAP attribute | entryUUID |
| User Object Classes | inetOrgPerson, organizationalPerson |
| Connection URL | ldap://openldap:389 |
| Users DN | ou=People,dc=example,dc=com |
| Authentication Type | simple |
| Bind DN | cn=admin,dc=example,dc=com |
| Bind Credential | admin |
| Search Scope | One Level |

#### 2.2.2. Маппинг атрибутов пользователя
- **Требование**: Настроить соответствие атрибутов LDAP и Keycloak

    | Keycloak Attribute | LDAP Attribute |
    |-------------------|----------------|
    | username | uid |
    | firstName | givenName |
    | lastName | sn |
    | email | mail |
    | enabled | (все пользователи активны) |

#### 2.2.3. Маппинг ролей LDAP
- **Требование**: Создать маппинг групп LDAP на роли Keycloak
- **Путь**: LDAP Provider → Mappers → Create

    **Mapper 1: LDAP Group to Keycloak Role**

    | Параметр | Значение |
    |----------|----------|
    | Name | group-mapper |
    | Mapper Type | group-ldap-mapper |
    | LDAP Groups DN | ou=Groups,dc=example,dc=com |
    | Group Name LDAP Attribute | cn |
    | Group Object Class | groupOfNames |
    | Membership LDAP Attribute | member |
    | Mode | READ_ONLY |
    | User Roles Retrieve Strategy | GET_GROUPS_FROM_USER_MEMBEROFATTRIBUTE |
    | Groups Path | / |
    
    **Mapper 2: LDAP Group to Realm Roles**

    | Параметр | Значение |
    |----------|----------|
    | Name | group-to-role-mapper |
    | Mapper Type | map-roles-ldap-mapper |
    | Roles LDAP Attribute | cn |
    | Role Name Object Class | groupOfNames |
    | Mode | IMPORT |
    
    **Role Mapping**:

    | LDAP Group | Keycloak Role |
    |------------|---------------|
    | user | user |
    | prothetic_user | prothetic_user |
    | administrator | administrator |

### 2.3. Тестирование синхронизации

#### 2.3.1. Ручная синхронизация
- **Требование**: Реализовать возможность ручной синхронизации
- **Путь**: Keycloak Admin Console → User Federation → OpenLDAP EU → Sync

#### 2.3.2. Автоматическая синхронизация
- **Требование**: Настроить периодическую синхронизацию
- **Интервал**: каждые 24 часа (настраивается в LDAP provider)

---

## 3. Нефункциональные требования

### 3.1. Производительность
- **Время синхронизации**: < 10 секунд для 1000 пользователей
- **LDAP response time**: < 100ms для типичных запросов

### 3.2. Надёжность
- **LDAP availability**: 99.9%
- **Graceful degradation**: При недоступности LDAP - аутентификация через Keycloak БД

### 3.3. Безопасность
- **LDAPS**: Рекомендуется использовать LDAPS (порт 636) в production
- **Password policy**: Настроить политику паролей в OpenLDAP
- **Bind credentials**: Хранить в Keycloak secure storage

---

## 4. Архитектурные решения

### 4.1. Компонентная диаграмма

```
┌─────────────────────────────────────────────────────────────────┐
│                         Keycloak                                │
│  ┌─────────────────┐  ┌──────────────────┐                      │
│  │  User Federation│  │   LDAP Mapper   │                      │
│  │    Provider     │◄─┤   - Users       │                      │
│  └────────┬────────┘  │   - Groups      │                      │
│           │           └──────────────────┘                      │
└───────────┼─────────────────────────────────────────────────────┘
            │ LDAP Protocol
            ▼
┌─────────────────────────────────────────────────────────────────┐
│                       OpenLDAP                                   │
│  ┌──────────────┐  ┌──────────────┐                             │
│  │ ou=People    │  │ ou=Groups    │                             │
│  │ - john.doe   │  │ - user       │                             │
│  │ - jane.smith │  │ - prothetic  │                             │
│  │ - alex.johnson│ │ - admin      │                             │
│  └──────────────┘  └──────────────┘                             │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2. Конфигурация docker-compose.yaml

```yaml
version: '3.8'

services:
  openldap:
    image: osixia/openldap:1.5.0
    container_name: bionicpro-openldap
    environment:
      LDAP_ORGANIZATION: BionicPRO EU
      LDAP_DOMAIN: example.com
      LDAP_ADMIN_PASSWORD: admin
      LDAP_CONFIG_PASSWORD: config
    ports:
      - "389:389"
      - "636:636"
    volumes:
      - ./ldap/data:/var/lib/ldap
      - ./ldap/config:/etc/ldap/slapd.d
      - ./ldap/config.ldif:/container/service/slapd/assets/config/bootstrap/ldif/custom/config.ldif
    networks:
      - bionicpro-network
    healthcheck:
      test: ["CMD", "ldapsearch", "-x", "-H", "ldap://localhost", "-D", "cn=admin,dc=example,dc=com", "-w", "admin", "-b", "dc=example,dc=com"]
      interval: 30s
      timeout: 10s
      retries: 5
```

---

## 5. Модель данных

### 5.1. Структура пользователя LDAP (inetOrgPerson)

```ldif
dn: uid=john.doe,ou=People,dc=example,dc=com
objectClass: inetOrgPerson
objectClass: organizationalPerson
objectClass: person
objectClass: top
uid: john.doe
cn: John Doe
sn: Doe
givenName: John
mail: john.doe@example.com
userPassword: {SHA}password
```

### 5.2. Структура группы LDAP (groupOfNames)

```ldif
dn: cn=prothetic_user,ou=Groups,dc=example,dc=com
objectClass: groupOfNames
objectClass: top
cn: prothetic_user
description: Users with prosthetic access
member: uid=john.doe,ou=People,dc=example,dc=com
member: uid=alex.johnson,ou=People,dc=example,dc=com
```

---

## 6. API-спецификация

### 6.1. LDAP операции (не требуется - управление через Keycloak)

Keycloak автоматически выполняет LDAP операции:
- Аутентификация (bind)
- Поиск пользователей
- Синхронизация ролей

---

## 7. Сценарии использования

### 7.1. Сценарий 1: Аутентификация LDAP пользователя

```
1. Пользователь пытается войти в приложение
2. BFF (bionicpro-auth) перенаправляет на Keycloak
3. Keycloak получает запрос на аутентификацию
4. Keycloak проверяет сначала встроенных пользователей
5. Если не найден - обращается к LDAP User Federation
6. Keycloak выполняет bind к LDAP с credentials пользователя
7. При успешном bind - пользователь аутентифицирован
8. Keycloak возвращает токен
```

### 7.2. Сценарий 2: Синхронизация ролей

```
1. Администратор добавляет пользователя в LDAP группу
2. Keycloak запускает синхронизацию (ручную или по расписанию)
3. Keycloak получает список групп пользователя из LDAP
4. Для каждой группы находится маппинг на Keycloak роль
5. Роли синхронизируются с Keycloak
6. При следующем входе пользователь получит новые роли
```

### 7.3. Сценарий 3: Создание нового LDAP пользователя

```
1. Администратор создаёт пользователя в LDAP через ldapadd
2. Запускается синхронизация в Keycloak
3. Keycloak импортирует пользователя (если Import Users = true)
4. Пользователь может аутентифицироваться
```

---

## 8. Зависимости от внешних систем

| Система | Версия | Назначение |
|---------|--------|------------|
| OpenLDAP | 1.5.0 | Хранение пользователей и групп |
| Keycloak | 21.1+ | Identity Provider с LDAP Federation |

---

## 9. Ограничения и допущения

### 9.1. Ограничения
1. **Single LDAP**: Пока только один LDAP для одного региона
2. **Password sync**: Пароли не синхронизируются обратно в LDAP
3. **Read-only groups**: Режим UNSYNCED означает роли не синхронизируются в LDAP

### 9.2. Допущения
1. OpenLDAP работает в Docker
2. Keycloak имеет доступ к LDAP по сети
3. Используется простая аутентификация (simple bind)

---

## 10. Критерии приёмки

### 10.1. Функциональные критерии

| # | Критерий | Метод проверки |
|---|----------|----------------|
| 1 | OpenLDAP запущен и доступен | ldapsearch из контейнера |
| 2 | Пользователи созданы в LDAP | ldapsearch -b ou=People |
| 3 | Группы созданы в LDAP | ldapsearch -b ou=Groups |
| 4 | LDAP Provider добавлен в Keycloak | Проверка в Admin Console |
| 5 | Синхронизация пользователей работает | Keycloak → User Federation → Sync |
| 6 | LDAP пользователь может войти | Тестовая аутентификация |
| 7 | Роли синхронизированы | Проверка ролей пользователя |

### 10.2. Тестовые сценарии

**TC-4.1**: Аутентификация LDAP пользователя
- Шаги: Использовать credentials john.doe/password
- Ожидаемый результат: Успешный вход с ролью prothetic_user

**TC-4.2**: Синхронизация нового пользователя
- Шаги: Добавить пользователя в LDAP → Sync
- Ожидаемый результат: Пользователь появился в Keycloak

**TC-4.3**: Изменение роли в LDAP
- Шаги: Добавить пользователя в группу administrator
- Ожидаемый результат: Роль administrator после синхронизации

**TC-4.4**: Fallback аутентификация
- Шаги: Остановить OpenLDAP
- Ожидаемый результат: Аутентификация через Keycloak БД работает

---

## 11. Чек-лист выполнения

### OpenLDAP
- [ ] Добавить openldap в docker-compose.yaml
- [ ] Создать файл config.ldif с пользователями и группами
- [ ] Создать docker volume для persistence
- [ ] Проверить запуск и доступность

### Keycloak
- [ ] Добавить LDAP User Federation провайдер
- [ ] Настроить подключение к OpenLDAP
- [ ] Настроить маппинг атрибутов пользователя
- [ ] Настроить маппинг групп на роли
- [ ] Протестировать синхронизацию
- [ ] Протестировать аутентификацию

### Тестирование
- [ ] Проверить аутентификацию каждого LDAP пользователя
- [ ] Проверить синхронизацию ролей
- [ ] Проверить fallback на Keycloak БД

---

## 6. Тестовые данные

Тестовые данные находятся в файле app/ldap/config.ldif:

| UID | Полное имя | Email | Пароль | Группа LDAP | Роль Keycloak |
|-----|------------|-------|--------|-------------|---------------|
| `john.doe` | John Doe | john@example.com | `password` | prothetic_user | prothetic_user |
| `jane.smith` | Jane Smith | jane@example.com | `password` | user | user |
| `alex.johnson` | Alex Johnson | alex@example.com | `password` | prothetic_user | prothetic_user |

Группы LDAP: user, prothetic_user, administrator

## 7. Параметры конфигурации LDAP в Keycloak

```json
{
  "vendor": "other",
  "connectionUrl": "ldap://openldap:389",
  "bindDn": "cn=admin,dc=example,dc=com",
  "bindCredential": "admin",
  "usersDn": "ou=People,dc=example,dc=com",
  "groupDn": "ou=Groups,dc=example,dc=com",
  "userObjectClasses": ["inetOrgPerson"],
  "groupObjectClasses": ["groupOfNames"],
  "mapper": {
    "usernameLDAPAttribute": "uid",
    "rdnLDAPAttribute": "uid",
    "uuidLDAPAttribute": "entryUUID",
    "userRolesRetrieveStrategy": "LOAD_GROUPS_BY_MEMBER_ATTRIBUTE",
    "groupNameLDAPAttribute": "cn",
    "groupsPath": "/"
  },
  "syncSettings": {
    "importEnabled": true,
    "syncRegistrations": true,
    "batchSizeForSync": 1000
  }
}
```

### Маппинг ролей:

| Группа LDAP | Роль Keycloak |
|-------------|----------------|
| user | user |
| prothetic_user | prothetic_user |
| administrator | administrator |

## 8. Граничные условия

- Timeout при обращении к LDAP: не более 2000 мс (2 секунды)
- При недоступности LDAP: fallback аутентификация через Keycloak БД

## 9. Критерии приёмки

| ID | Тест | Ожидаемый результат |
|----|------|---------------------|
| T4.1 | LDAPLogin | Вход с LDAP учётными данными (john.doe/password) успешен |
| T4.2 | RoleSync | Роли синхронизируются из LDAP в Keycloak |
| T4.3 | LDAPTimeout | Ошибка при превышении timeout 2 секунд |
| T4.4 | FallbackAuth | При недоступности LDAP используется Keycloak БД |