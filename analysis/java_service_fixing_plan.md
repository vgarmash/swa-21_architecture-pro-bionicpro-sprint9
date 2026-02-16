# План исправления Java-сервиса bionicpro-auth

## Дата анализа: 2026-02-15

---

## 1. КРИТИЧЕСКИЕ СИНТАКСИЧЕСКИЕ ОШИБКИ

### 1.1 SecurityConfig.java:27
**Файл**: [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/config/SecurityConfig.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/config/SecurityConfig.java:27)

**Проблема**: Лишняя закрывающая скобка `))` в строке 27.

**Текущий код**:
```java
configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
```

**Исправление**:
```java
configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
```

---

### 1.2 SessionController.java:29
**Файл**: [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/controller/SessionController.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/controller/SessionController.java:29)

**Проблема**: Лишняя закрывающая скобка `)` в строке 29.

**Текущий код**:
```java
return ResponseEntity.ok(isValid);
```

**Исправление**:
```java
return ResponseEntity.ok(isValid);
```

---

### 1.3 SessionServiceImpl.java:88
**Файл**: [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/service/SessionServiceImpl.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/service/SessionServiceImpl.java:88)

**Проблема**: Лишняя закрывающая скобка `)` в строке 88.

**Текущий код**:
```java
SessionData sessionData = sessions.get(sessionId);
```

**Исправление**:
```java
SessionData sessionData = sessions.get(sessionId);
```

---

## 2. ЗАВИСЫ ОБЯЗАТЕЛЬНЫЕ ДЛЯ pom.xml

### 2.1 H2 Database
**Добавить в dependencies**:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```
**Обосновление**: В [`application.yml`](app/bionicpro-auth/src/main/resources/application.yml:13) настроен in-memory H2 datasource.

---

### 2.2 Jakarta Servlet API (опционально)
**Добавить в dependencies**:
```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```
**Обосновление**: В [`CookieUtil.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/util/CookieUtil.java) используется `jakarta.servlet.http.Cookie`. Обычно входит в `spring-boot-starter-web`.

---

## 3. НЕИСПОЛЬЗУЕМЫЕ ЗАВИСЫ (рекомендовано к удалению/комментированию)

### 3.1 spring-boot-starter-data-jpa
**Текущий**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```
**Статус**: Объявлен, но не используется (нет JPA-сущностей).

---

### 3.2 spring-boot-starter-cache
**Текущий**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```
**Статус**: Объявлен, но не используется (нет кэширования в коде).

---

### 3.3 nimbus-jose-jwt
**Текущий**:
```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.31</version>
</dependency>
```
**Статус**: Объявлен, но не используется (самописная JWT-реализация в [`JwtUtil.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/util/JwtUtil.java)).

---

## 4. ОПЦИОНАЛЬНЫЕ УЛУЧШЕНИЯ

### 4.1 Lombok
**Текущий**: Упомянут в `maven-compiler-plugin`, но не в dependencies.

**Добавить**:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```
**Обоснование**: Упрощает код (геттеры, сеттеры, конструкторы via `@Data`, `@RequiredArgsConstructor`).

---

## 5. ДУБЛИКАТЫ ФУНКЦИОНАЛЬНОСТИ (код-ревью)

### 5.1 InMemorySessionRepository vs SessionServiceImpl
**Проблема**: Оба класса используют `ConcurrentHashMap` для хранения сессий.

- [`InMemorySessionRepository`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/repository/InMemorySessionRepository.java) - репозиторий слой
- [`SessionServiceImpl`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/service/SessionServiceImpl.java) - сервис слой с встроенной картой

**Рекомендация**: Использовать только один подход. Если нужен репозиторий слой - удалить карту из сервиса. Если это простое in-memory implementation - можно удалить отдельный репозиторий.

---

## 6. АРХИТЕКТУРНЫЕ РЕКОМЕНДАЦИИ

### 6.1 JWT Реализация
**Текущее**: Самописная реализация в [`JwtUtil.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/util/JwtUtil.java) с использованием `javax.crypto.Mac`.

**Рекомендация**: Использовать `nimbus-jose-jwt` или `jjwt` для более безопасной и поддерживаемой реализации.

---

### 6.2 SecurityConfig JwtDecoder
**Текущее**: Метод `jwtDecoder()` возвращает заглушку, бросающую `UnsupportedOperationException`.

**Рекомендация**: Подключить реальный Keycloak JWT decoder:
```java
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder.withJwkSetUri("http://localhost:8080/realms/bionicpro/protocol/openid-connect/certs").build();
}
```

---

### 6.3 AuthController.initiateLogin()
**Текущее**: Возвращает `String` с заглушкой "login_url".

**Рекомендация**: Вернуть URL для редиректа к Keycloak:
```java
@PostMapping("/login")
public ResponseEntity<LoginResponse> login() {
    String keycloakUrl = "http://localhost:8080/realms/bionicpro/protocol/openid-connect/auth...";
    return ResponseEntity.ok(new LoginResponse(keycloakUrl));
}
```

---

## 7. ПОРЯДОК ДЕЙСТВИЙ

1. Исправить синтаксические ошибки (раздел 1)
2. Добавить H2 database (раздел 2.1)
3. Удалить/комментировать неиспользуемые зависимости (раздел 3)
4. Добавить Lombok (раздел 4.1)
5. Рефакторинг кода (раздел 5-6)

---

## 8. ФАЙЛЫ, УПОМЯНУТЫЕ В ПЛАНЕ

- [`app/bionicpro-auth/pom.xml`](app/bionicpro-auth/pom.xml)
- [`app/bionicpro-auth/src/main/resources/application.yml`](app/bionicpro-auth/src/main/resources/application.yml)
- [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/config/SecurityConfig.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/config/SecurityConfig.java)
- [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/util/JwtUtil.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/util/JwtUtil.java)
- [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/util/CookieUtil.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/util/CookieUtil.java)
- [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/model/SessionData.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/model/SessionData.java)
- [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/repository/InMemorySessionRepository.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/repository/InMemorySessionRepository.java)
- [`app/bionicpro-auth/src/main/java/com/bionicpro/auth/service/SessionServiceImpl.java`](app/bionicpro-auth/src/main/java/com/bionicpro/auth/service/SessionServiceImpl.java)