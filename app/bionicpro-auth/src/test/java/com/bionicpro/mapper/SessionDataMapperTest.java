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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
            // Дано
            Instant expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
            SessionData session = SessionData.builder()
                    .sessionId(UUID.randomUUID().toString())
                    .userId("user-123")
                    .username("testuser")
                    .roles(List.of("ADMIN", "USER"))
                    .expiresAt(expiresAt)
                    .build();

            // Когда
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Тогда
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
            // Дано
            SessionData session = SessionData.builder()
                    .userId("user-123")
                    .username("testuser")
                    .roles(null)
                    .expiresAt(Instant.now())
                    .build();

            // Когда
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Тогда
            assertThat(response).isNotNull();
            assertThat(response.getRoles()).isNull();
        }

        @Test
        @DisplayName("Should handle null expiresAt")
        void shouldHandleNullExpiresAt() {
            // Дано
            SessionData session = SessionData.builder()
                    .userId("user-123")
                    .username("testuser")
                    .roles(List.of("USER"))
                    .expiresAt(null)
                    .build();

            // Когда
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Тогда
            assertThat(response).isNotNull();
            assertThat(response.getSessionExpiresAt()).isNull();
        }

        @Test
        @DisplayName("Should return null when source is null")
        void shouldReturnNullForNullSource() {
            // Когда
            AuthStatusResponse response = mapper.toAuthStatusResponse(null);

            // Тогда
            assertThat(response).isNull();
        }

        @Test
        @DisplayName("Should handle empty roles list")
        void shouldHandleEmptyRoles() {
            // Дано
            SessionData session = SessionData.builder()
                    .userId("user-123")
                    .username("testuser")
                    .roles(List.of())
                    .expiresAt(Instant.now())
                    .build();

            // Когда
            AuthStatusResponse response = mapper.toAuthStatusResponse(session);

            // Тогда
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
            // Когда
            AuthStatusResponse response = mapper.toUnauthenticatedResponse();

            // Тогда
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
            // Дано
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

            // Когда
            SessionData copy = mapper.copyForRotation(original, newSessionId);

            // Тогда
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
            // Когда
            SessionData copy = mapper.copyForRotation(null, "new-id");

            // Тогда
            assertThat(copy).isNull();
        }

        @Test
        @DisplayName("Should handle null newSessionId")
        void shouldHandleNullNewSessionId() {
            // Дано
            SessionData original = SessionData.builder()
                    .sessionId("original-id")
                    .userId("user-789")
                    .build();

            // Когда
            SessionData copy = mapper.copyForRotation(original, null);

            // Тогда
            assertThat(copy).isNotNull();
            assertThat(copy.getSessionId()).isNull();
            assertThat(copy.getUserId()).isEqualTo("user-789");
        }
    }
}
