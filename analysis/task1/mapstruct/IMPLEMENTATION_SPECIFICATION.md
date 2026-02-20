# MapStruct Implementation Specification for bionicpro-auth

## Overview

This specification provides detailed instructions for implementing MapStruct mappers in the `bionicpro-auth` module. The goal is to replace manual builder patterns with type-safe, generated mapper code.

### Current State

The module already has MapStruct 1.5.5.Final configured in [`pom.xml`](app/bionicpro-auth/pom.xml):
- `org.mapstruct:mapstruct` - Runtime annotations
- `org.mapstruct:mapstruct-processor` - Annotation processor (provided scope)

### Scope

| Mapper | Source | Target | Priority |
|--------|--------|--------|----------|
| `SessionDataMapper` | `SessionData` | `AuthStatusResponse` | High |
| `SessionDataMapper` | `SessionData` (copy) | `SessionData` | High |
| `AuditEventMapper` | `AuditEventData` | `AuditEvent` | Medium |

---

## Prerequisites

### 1. Verify Dependencies

Ensure `pom.xml` contains:

```xml
<dependencies>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.5.Final</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>1.5.5.Final</version>
                    </path>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok-mapstruct-binding</artifactId>
                        <version>0.2.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 2. Package Structure

Create the following package structure:

```
com.bionicpro
├── mapper/
│   ├── SessionDataMapper.java
│   └── AuditEventMapper.java
├── dto/
│   └── AuthStatusResponse.java
├── model/
│   ├── SessionData.java
│   └── TokenData.java
└── audit/
    └── AuditEvent.java
```

---

## Implementation Steps

### Step 1: Create SessionDataMapper

**File Location:** `app/bionicpro-auth/src/main/java/com/bionicpro/mapper/SessionDataMapper.java`

```java
package com.bionicpro.mapper;

import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.model.SessionData;
import org.mapstruct.*;

import java.time.Instant;

/**
 * MapStruct mapper for SessionData transformations.
 * Provides type-safe mapping between SessionData and response DTOs.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface SessionDataMapper {

    /**
     * Maps SessionData to AuthStatusResponse for authenticated users.
     * 
     * @param session the source SessionData
     * @return AuthStatusResponse with authenticated=true
     */
    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "sessionExpiresAt", source = "expiresAt")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "roles", source = "roles")
    AuthStatusResponse toAuthStatusResponse(SessionData session);

    /**
     * Creates an unauthenticated response.
     * 
     * @return AuthStatusResponse with authenticated=false
     */
    default AuthStatusResponse toUnauthenticatedResponse() {
        return AuthStatusResponse.builder()
                .authenticated(false)
                .build();
    }

    /**
     * Copies SessionData with a new session ID and updated lastAccessedAt.
     * Used for session rotation.
     * 
     * @param source the original SessionData
     * @param newSessionId the new session ID
     * @return new SessionData with updated fields
     */
    @Mapping(target = "sessionId", source = "newSessionId")
    @Mapping(target = "lastAccessedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "userId", source = "source.userId")
    @Mapping(target = "username", source = "source.username")
    @Mapping(target = "roles", source = "source.roles")
    @Mapping(target = "accessToken", source = "source.accessToken")
    @Mapping(target = "refreshToken", source = "source.refreshToken")
    @Mapping(target = "accessTokenExpiresAt", source = "source.accessTokenExpiresAt")
    @Mapping(target = "refreshTokenExpiresAt", source = "source.refreshTokenExpiresAt")
    @Mapping(target = "createdAt", source = "source.createdAt")
    @Mapping(target = "expiresAt", source = "source.expiresAt")
    SessionData copyForRotation(SessionData source, String newSessionId);
}
```

### Step 2: Create AuditEventMapper (Optional - Medium Priority)

**File Location:** `app/bionicpro-auth/src/main/java/com/bionicpro/mapper/AuditEventMapper.java`

```java
package com.bionicpro.mapper;

import com.bionicpro.audit.AuditEvent;
import com.bionicpro.audit.AuditEventType;
import org.mapstruct.*;

import java.time.Instant;

/**
 * MapStruct mapper for AuditEvent creation.
 * Note: This mapper provides base mapping - sanitization logic
 * should remain in AuditServiceImpl for security reasons.
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    imports = {Instant.class}
)
public interface AuditEventMapper {

    /**
     * Creates base AuditEvent with timestamp.
     * Used as starting point - sanitization applied in service layer.
     *
     * @param eventType the audit event type
     * @param principal the user ID
     * @param sessionId the session ID
     * @param correlationId the correlation ID from MDC
     * @param clientIp the client IP address
     * @param userAgent the user agent string
     * @param outcome the outcome (SUCCESS, FAILURE, EXPIRED)
     * @return AuditEvent with populated fields
     */
    @Mapping(target = "timestamp", expression = "java(Instant.now())")
    @Mapping(target = "details", expression = "java(new java.util.HashMap<>())")
    AuditEvent createAuditEvent(
        AuditEventType eventType,
        String principal,
        String sessionId,
        String correlationId,
        String clientIp,
        String userAgent,
        String outcome
    );
}
```

### Step 3: Update AuthController

**File:** [`AuthController.java`](app/bionicpro-auth/src/main/java/com/bionicpro/controller/AuthController.java)

**Before (Current Implementation):**
```java
@GetMapping("/status")
public ResponseEntity<AuthStatusResponse> getStatus(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    
    if (authentication == null || !authentication.isAuthenticated()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthStatusResponse.builder()
                        .authenticated(false)
                        .build());
    }
    
    // ... extract roles and expiration ...
    
    AuthStatusResponse response = AuthStatusResponse.builder()
            .authenticated(true)
            .userId(userId)
            .username(userId)
            .roles(roles)
            .sessionExpiresAt(expiresAt != null ? expiresAt.toString() : null)
            .build();
    
    return ResponseEntity.ok(response);
}
```

**After (With MapStruct):**
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SessionService sessionService;
    private final AuditService auditService;
    private final SessionDataMapper sessionDataMapper;  // Add mapper injection

    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> getStatus(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        String sessionId = sessionService.getSessionIdFromRequest(request);
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        SessionData sessionData = sessionService.getSession(sessionId);
        if (sessionData == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        return ResponseEntity.ok(sessionDataMapper.toAuthStatusResponse(sessionData));
    }
    
    // ... rest of controller methods ...
}
```

### Step 4: Update SessionServiceImpl

**File:** [`SessionServiceImpl.java`](app/bionicpro-auth/src/main/java/com/bionicpro/service/SessionServiceImpl.java)

**Before (Current Implementation):**
```java
@Override
public SessionData rotateSession(String sessionId) {
    // ...
    SessionData newSession = SessionData.builder()
            .sessionId(newSessionId)
            .userId(oldSession.getUserId())
            .username(oldSession.getUsername())
            .roles(oldSession.getRoles())
            .accessToken(oldSession.getAccessToken())
            .refreshToken(oldSession.getRefreshToken())
            .accessTokenExpiresAt(oldSession.getAccessTokenExpiresAt())
            .refreshTokenExpiresAt(oldSession.getRefreshTokenExpiresAt())
            .createdAt(oldSession.getCreatedAt())
            .expiresAt(oldSession.getExpiresAt())
            .lastAccessedAt(Instant.now())
            .build();
    // ...
}
```

**After (With MapStruct):**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final BytesEncryptor bytesEncryptor;
    private final AuditService auditService;
    private final SessionDataMapper sessionDataMapper;  // Add mapper injection
    
    // ... other methods ...

    @Override
    public SessionData rotateSession(String sessionId) {
        if (sessionId == null) {
            log.debug("No session to rotate");
            return null;
        }
        
        SessionData oldSession = getSession(sessionId);
        
        if (oldSession == null) {
            log.debug("Session not found for rotation");
            return null;
        }
        
        // Generate new session ID
        String newSessionId = UUID.randomUUID().toString();
        
        // Use mapper for copy with new session ID
        SessionData newSession = sessionDataMapper.copyForRotation(oldSession, newSessionId);
        
        // Store new session
        String newRedisKey = getSessionKey(newSessionId);
        redisTemplate.opsForValue().set(newRedisKey, newSession, Duration.ofMinutes(sessionTimeoutMinutes));
        
        // Invalidate old session
        invalidateSessionById(sessionId);
        
        log.info("Rotated session for user: {} from {} to {}", 
                oldSession.getUserId(), sessionId, newSessionId);
        
        return newSession;
    }
}
```

---

## Code Templates

### Template 1: Basic Mapper Interface

```java
package com.bionicpro.mapper;

import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface EntityMapper {

    // For non-Spring usage (testing):
    // EntityMapper INSTANCE = Mappers.getMapper(EntityMapper.class);
    
    @Mapping(target = "targetField", source = "sourceField")
    TargetDto toDto(SourceEntity entity);
    
    @InheritInverseConfiguration
    SourceEntity toEntity(TargetDto dto);
}
```

### Template 2: Mapper with Custom Expression

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CustomMapper {

    @Mapping(
        target = "computedField",
        expression = "java(computeValue(source.getValue()))"
    )
    TargetDto toDto(Source source);
    
    // Custom method called from expression
    default String computeValue(String input) {
        return input != null ? input.toUpperCase() : null;
    }
}
```

### Template 3: Mapper with Lifecycle Hooks

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class LifecycleMapper {

    @BeforeMapping
    protected void validateSource(Source source) {
        if (source != null && source.getId() == null) {
            throw new IllegalArgumentException("Source ID cannot be null");
        }
    }

    @AfterMapping
    protected void enrichTarget(Source source, @MappingTarget TargetDto target) {
        target.setComputedAt(Instant.now());
    }

    public abstract TargetDto toDto(Source source);
}
```

### Template 4: Mapper with Null Value Handling

```java
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface SafeMapper {

    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    TargetDto toDtoSafe(Source source);
    
    @Mapping(target = "optionalField", source = "value", defaultValue = "DEFAULT")
    TargetDto withDefaults(Source source);
}
```

---

## Testing Requirements

### Test Class Structure

**File Location:** `app/bionicpro-auth/src/test/java/com/bionicpro/mapper/SessionDataMapperTest.java`

```java
package com.bionicpro.mapper;

import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.model.SessionData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SessionDataMapper.
 * Uses Mappers.getMapper() for isolated unit testing without Spring context.
 */
class SessionDataMapperTest {

    private SessionDataMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(SessionDataMapper.class);
    }

    @Nested
    @DisplayName("toAuthStatusResponse tests")
    class ToAuthStatusResponseTests {

        @Test
        @DisplayName("Should map all fields correctly for valid SessionData")
        void shouldMapAllFieldsCorrectly() {
            // Given
            Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
            SessionData session = SessionData.builder()
                    .sessionId(UUID.randomUUID().toString())
                    .userId("user-123")
                    .username("testuser")
                    .roles(List.of("ADMIN", "USER"))
                    .expiresAt(expiresAt)
                    .build();

            // When
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isAuthenticated()).isTrue();
            assertThat(response.getUserId()).isEqualTo("user-123");
            assertThat(response.getUsername()).isEqualTo("testuser");
            assertThat(response.getRoles())
                    .containsExactlyInAnyOrder("ADMIN", "USER");
            assertThat(response.getSessionExpiresAt()).isEqualTo(expiresAt.toString());
        }

        @Test
        @DisplayName("Should handle null roles list")
        void shouldHandleNullRoles() {
            // Given
            SessionData session = SessionData.builder()
                    .userId("user-123")
                    .username("testuser")
                    .roles(null)
                    .expiresAt(Instant.now())
                    .build();

            // When
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getRoles()).isNull();
        }

        @Test
        @DisplayName("Should handle null expiresAt")
        void shouldHandleNullExpiresAt() {
            // Given
            SessionData session = SessionData.builder()
                    .userId("user-123")
                    .username("testuser")
                    .roles(List.of("USER"))
                    .expiresAt(null)
                    .build();

            // When
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSessionExpiresAt()).isNull();
        }

        @Test
        @DisplayName("Should return null when source is null")
        void shouldReturnNullForNullSource() {
            // When
            AuthStatusResponse response = mapper.toAuthStatusResponse(null);

            // Then
            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should handle empty roles list")
        void shouldHandleEmptyRoles() {
            // Given
            SessionData session = SessionData.builder()
                    .userId("user-123")
                    .username("testuser")
                    .roles(List.of())
                    .expiresAt(Instant.now())
                    .build();

            // When
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getRoles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("toUnauthenticatedResponse tests")
    class ToUnauthenticatedResponseTests {

        @Test
        @DisplayName("Should return response with authenticated=false")
        void shouldReturnUnauthenticatedResponse() {
            // When
            AuthStatusResponse response = mapper.toUnauthenticatedResponse();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isAuthenticated()).isFalse();
            assertThat(response.getUserId()).isNull();
            assertThat(response.getUsername()).isNull();
            assertThat(response.getRoles()).isNull();
            assertThat(response.getSessionExpiresAt()).isNull();
        }
    }

    @Nested
    @DisplayName("copyForRotation tests")
    class CopyForRotationTests {

        @Test
        @DisplayName("Should copy all fields with new session ID")
        void shouldCopyWithNewSessionId() {
            // Given
            String originalSessionId = UUID.randomUUID().toString();
            String newSessionId = UUID.randomUUID().toString();
            Instant createdAt = Instant.now().minus(5, ChronoUnit.MINUTES);
            Instant expiresAt = Instant.now().plus(25, ChronoUnit.MINUTES);
            
            SessionData original = SessionData.builder()
                    .sessionId(originalSessionId)
                    .userId("user-456")
                    .username("rotateuser")
                    .roles(List.of("USER"))
                    .accessToken("encrypted-access-token")
                    .refreshToken("encrypted-refresh-token")
                    .accessTokenExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .refreshTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .createdAt(createdAt)
                    .expiresAt(expiresAt)
                    .lastAccessedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                    .build();

            // When
            SessionData copy = mapper.copyForRotation(original, newSessionId);

            // Then
            assertThat(copy).isNotNull();
            assertThat(copy.getSessionId()).isEqualTo(newSessionId);
            assertThat(copy.getUserId()).isEqualTo("user-456");
            assertThat(copy.getUsername()).isEqualTo("rotateuser");
            assertThat(copy.getRoles()).isEqualTo(List.of("USER"));
            assertThat(copy.getAccessToken()).isEqualTo("encrypted-access-token");
            assertThat(copy.getRefreshToken()).isEqualTo("encrypted-refresh-token");
            assertThat(copy.getCreatedAt()).isEqualTo(createdAt);
            assertThat(copy.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(copy.getLastAccessedAt()).isNotNull();
            // Verify lastAccessedAt is recent (within 1 second)
            assertThat(copy.getLastAccessedAt())
                    .isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should handle null source")
        void shouldHandleNullSource() {
            // When
            SessionData copy = mapper.copyForRotation(null, "new-id");

            // Then
            assertThat(copy).isNull();
        }

        @Test
        @DisplayName("Should handle null newSessionId")
        void shouldHandleNullNewSessionId() {
            // Given
            SessionData original = SessionData.builder()
                    .sessionId("original-id")
                    .userId("user-789")
                    .build();

            // When
            SessionData copy = mapper.copyForRotation(original, null);

            // Then
            assertThat(copy).isNotNull();
            assertThat(copy.getSessionId()).isNull();
            assertThat(copy.getUserId()).isEqualTo("user-789");
        }
    }
}
```

### Test Configuration for Spring Context Tests

**File Location:** `app/bionicpro-auth/src/test/java/com/bionicpro/mapper/SessionDataMapperIntegrationTest.java`

```java
package com.bionicpro.mapper;

import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.model.SessionData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test verifying Spring DI injection of the mapper.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionDataMapperIntegrationTest {

    @Autowired
    private SessionDataMapper sessionDataMapper;

    @Test
    @DisplayName("Mapper should be injected by Spring")
    void mapperShouldBeInjected() {
        assertThat(sessionDataMapper).isNotNull();
    }

    @Test
    @DisplayName("Should map using injected mapper")
    void shouldMapUsingInjectedMapper() {
        // Given
        SessionData session = SessionData.builder()
                .userId("integration-user")
                .username("integrationtest")
                .roles(List.of("TEST"))
                .expiresAt(Instant.now())
                .build();

        // When
        AuthStatusResponse response = sessionDataMapper.toAuthStatusResponse(session);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getUserId()).isEqualTo("integration-user");
    }
}
```

---

## Edge Cases and Error Handling

### 1. Null Source Handling

MapStruct generates null checks by default. For methods returning `null` when source is `null`:

```java
// Generated code will be:
if (session == null) {
    return null;
}
```

To return a default object instead:

```java
@BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
AuthStatusResponse toAuthStatusResponse(SessionData session);
```

### 2. Type Conversion - Instant to String

The `expiresAt` (Instant) to `sessionExpiresAt` (String) conversion requires handling:

```java
@Mapping(
    target = "sessionExpiresAt",
    expression = "java(session.getExpiresAt() != null ? session.getExpiresAt().toString() : null)"
)
AuthStatusResponse toAuthStatusResponse(SessionData session);
```

Or use a custom mapping method:

```java
default String mapInstantToString(Instant instant) {
    return instant != null ? instant.toString() : null;
}
```

### 3. Collection Mapping

For the `List<String> roles` field, MapStruct handles this automatically. However, to ensure null-safety:

```java
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface SessionDataMapper {
    // roles will be copied as-is, null remains null
}
```

---

## Verification Checklist

After implementation, verify:

- [ ] Mapper interfaces compile without errors
- [ ] Generated implementation classes exist in `target/generated-sources/annotations`
- [ ] `SessionDataMapperImpl` class is generated with `@Component` annotation
- [ ] Spring successfully injects mappers into services
- [ ] All unit tests pass
- [ ] Integration tests pass
- [ ] No mapping warnings in build output (check `unmappedTargetPolicy`)

---

## Build Commands

```bash
# Clean build with annotation processing
cd app/bionicpro-auth
mvn clean compile

# Run tests
mvn test -Dtest=SessionDataMapperTest

# Run integration tests
mvn test -Dtest=SessionDataMapperIntegrationTest

# Verify no unmapped properties warnings
mvn compile -X 2>&1 | grep -i "unmapped"
```

---

## References

- [MapStruct Reference Guide](https://mapstruct.org/documentation/stable/reference/html/)
- [MapStruct with Lombok](https://mapstruct.org/faq/#can-i-use-mapstruct-together-with-project-lombok)
- Context7 Documentation: `/mapstruct/mapstruct`
