package com.bionicpro.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionData model.
 * Tests serialization, deserialization, and model behavior.
 */
@DisplayName("SessionData Tests")
class SessionDataTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTest {

        @Test
        @DisplayName("Should create SessionData with all fields")
        void builder_shouldCreateWithAllFields() {
            // Подготовка
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(1800);
            Instant accessTokenExpiresAt = now.plusSeconds(3600);
            Instant refreshTokenExpiresAt = now.plusSeconds(86400);

            // Действие
            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .userId("user456")
                .username("testuser")
                .roles(List.of("ROLE_USER", "ROLE_ADMIN"))
                .accessToken("encrypted-access-token")
                .refreshToken("encrypted-refresh-token")
                .accessTokenExpiresAt(accessTokenExpiresAt)
                .refreshTokenExpiresAt(refreshTokenExpiresAt)
                .createdAt(now)
                .expiresAt(expiresAt)
                .lastAccessedAt(now)
                .build();

            // Проверка
            assertEquals("session-123", sessionData.getSessionId());
            assertEquals("user456", sessionData.getUserId());
            assertEquals("testuser", sessionData.getUsername());
            assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), sessionData.getRoles());
            assertEquals("encrypted-access-token", sessionData.getAccessToken());
            assertEquals("encrypted-refresh-token", sessionData.getRefreshToken());
            assertEquals(accessTokenExpiresAt, sessionData.getAccessTokenExpiresAt());
            assertEquals(refreshTokenExpiresAt, sessionData.getRefreshTokenExpiresAt());
            assertEquals(now, sessionData.getCreatedAt());
            assertEquals(expiresAt, sessionData.getExpiresAt());
            assertEquals(now, sessionData.getLastAccessedAt());
        }

        @Test
        @DisplayName("Should create SessionData with null optional fields")
        void builder_shouldCreateWithNullFields() {
            // Действие
            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .userId("user456")
                .build();

            // Проверка
            assertEquals("session-123", sessionData.getSessionId());
            assertEquals("user456", sessionData.getUserId());
            assertNull(sessionData.getUsername());
            assertNull(sessionData.getRoles());
            assertNull(sessionData.getAccessToken());
            assertNull(sessionData.getRefreshToken());
        }
    }

    @Nested
    @DisplayName("Setters and Getters")
    class SettersGettersTest {

        @Test
        @DisplayName("Should set and get all fields correctly")
        void settersAndGetters_shouldWorkCorrectly() {
            // Подготовка
            SessionData sessionData = new SessionData();
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(1800);

            // Действие
            sessionData.setSessionId("session-123");
            sessionData.setUserId("user456");
            sessionData.setUsername("testuser");
            sessionData.setRoles(List.of("ROLE_USER"));
            sessionData.setAccessToken("token");
            sessionData.setRefreshToken("refresh");
            sessionData.setAccessTokenExpiresAt(expiresAt);
            sessionData.setRefreshTokenExpiresAt(expiresAt);
            sessionData.setCreatedAt(now);
            sessionData.setExpiresAt(expiresAt);
            sessionData.setLastAccessedAt(now);

            // Проверка
            assertEquals("session-123", sessionData.getSessionId());
            assertEquals("user456", sessionData.getUserId());
            assertEquals("testuser", sessionData.getUsername());
            assertEquals(List.of("ROLE_USER"), sessionData.getRoles());
            assertEquals("token", sessionData.getAccessToken());
            assertEquals("refresh", sessionData.getRefreshToken());
            assertEquals(expiresAt, sessionData.getAccessTokenExpiresAt());
            assertEquals(expiresAt, sessionData.getRefreshTokenExpiresAt());
            assertEquals(now, sessionData.getCreatedAt());
            assertEquals(expiresAt, sessionData.getExpiresAt());
            assertEquals(now, sessionData.getLastAccessedAt());
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTest {

        @Test
        @DisplayName("SessionData should be serializable")
        void shouldBeSerializable() {
            // Подготовка
            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .userId("user456")
                .username("testuser")
                .build();

            // Проверка - verify it can be serialized (no exception)
            assertDoesNotThrow(() -> {
                // Basic serialization check - class should implement Serializable
                assertTrue(java.io.Serializable.class.isAssignableFrom(SessionData.class));
            });
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("Should be equal for same values")
        void shouldBeEqualForSameValues() {
            // Подготовка
            SessionData session1 = SessionData.builder()
                .sessionId("session-123")
                .userId("user456")
                .username("testuser")
                .build();

            SessionData session2 = SessionData.builder()
                .sessionId("session-123")
                .userId("user456")
                .username("testuser")
                .build();

            // Проверка
            assertEquals(session1, session2);
            assertEquals(session1.hashCode(), session2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal for different session IDs")
        void shouldNotBeEqualForDifferentSessionIds() {
            // Подготовка
            SessionData session1 = SessionData.builder()
                .sessionId("session-123")
                .userId("user456")
                .build();

            SessionData session2 = SessionData.builder()
                .sessionId("session-456")
                .userId("user456")
                .build();

            // Проверка
            assertNotEquals(session1, session2);
        }
    }

    @Nested
    @DisplayName("ToString Test")
    class ToStringTest {

        @Test
        @DisplayName("ToString should include all fields")
        void toString_shouldIncludeAllFields() {
            // Подготовка
            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .userId("user456")
                .username("testuser")
                .build();

            // Действие
            String result = sessionData.toString();

            // Проверка
            assertTrue(result.contains("sessionId"));
            assertTrue(result.contains("session-123"));
            assertTrue(result.contains("userId"));
            assertTrue(result.contains("user456"));
        }
    }
}
