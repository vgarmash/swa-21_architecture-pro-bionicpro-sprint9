package com.bionicpro.service;

import com.bionicpro.model.SessionData;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionService.
 * Tests session management: creation, validation, rotation, invalidation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService Tests")
class SessionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private BytesEncryptor bytesEncryptor;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(redisTemplate, bytesEncryptor);
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("Auth Request Storage")
    class AuthRequestStorageTest {

        @Test
        @DisplayName("Should store auth request with state")
        void storeAuthRequest_shouldStoreAuthRequest() {
            // Arrange
            String state = "test-state";
            String redirectUri = "/dashboard";

            // Act
            sessionService.storeAuthRequest(state, redirectUri);

            // Assert - verify through getAuthRequest
            String retrievedUri = sessionService.getAuthRequest(state);
            assertEquals(redirectUri, retrievedUri);
        }

        @Test
        @DisplayName("Should return null for non-existent state")
        void getAuthRequest_shouldReturnNullForNonExistentState() {
            // Arrange
            String state = "non-existent-state";

            // Act
            String result = sessionService.getAuthRequest(state);

            // Assert
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Session Creation")
    class SessionCreationTest {

        @Test
        @DisplayName("Should create session with tokens")
        void createSession_shouldCreateSessionWithTokens() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(bytesEncryptor.encrypt(any())).thenReturn("encrypted".getBytes());

            OidcIdToken idToken = new OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                java.util.Map.of("sub", "user123", "preferred_username", "testuser")
            );

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600)
            );

            OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                "refresh-token-value",
                Instant.now(),
                Instant.now().plusSeconds(86400)
            );

            // Act
            sessionService.createSession(request, response, idToken, accessToken, refreshToken);

            // Assert
            verify(valueOperations).set(anyString(), any(SessionData.class), any(Duration.class));
            
            // Verify cookie is set
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            
            Cookie setCookie = cookieCaptor.getValue();
            assertEquals("BIONICPRO_SESSION", setCookie.getName());
            assertTrue(setCookie.isHttpOnly());
            assertEquals("/", setCookie.getPath());
        }

        @Test
        @DisplayName("Should create session without refresh token")
        void createSession_shouldCreateSessionWithoutRefreshToken() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(bytesEncryptor.encrypt(any())).thenReturn("encrypted".getBytes());

            OidcIdToken idToken = new OidcIdToken(
                "id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                java.util.Map.of("sub", "user123", "preferred_username", "testuser")
            );

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600)
            );

            // Act
            sessionService.createSession(request, response, idToken, accessToken, null);

            // Assert
            verify(valueOperations).set(anyString(), any(SessionData.class), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("Session Retrieval")
    class SessionRetrievalTest {

        @Test
        @DisplayName("Should return session data when exists")
        void getSession_shouldReturnSessionData() {
            // Arrange
            SessionData expectedSession = SessionData.builder()
                .sessionId("session-123")
                .userId("user123")
                .username("testuser")
                .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("bionicpro:session:session-123")).thenReturn(expectedSession);

            // Act
            SessionData result = sessionService.getSession("session-123");

            // Assert
            assertNotNull(result);
            assertEquals("session-123", result.getSessionId());
            assertEquals("user123", result.getUserId());
        }

        @Test
        @DisplayName("Should return null when session not found")
        void getSession_shouldReturnNullWhenNotFound() {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            // Act
            SessionData result = sessionService.getSession("non-existent");

            // Assert
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Session Validation")
    class SessionValidationTest {

        @Test
        @DisplayName("Should return session when valid")
        void validateAndRefreshSession_shouldReturnValidSession() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);

            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .userId("user123")
                .expiresAt(Instant.now().plusSeconds(1800))
                .accessTokenExpiresAt(Instant.now().plusSeconds(3600))
                .lastAccessedAt(Instant.now())
                .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("bionicpro:session:session-123")).thenReturn(sessionData);

            // Act
            SessionData result = sessionService.validateAndRefreshSession("session-123");

            // Assert
            assertNotNull(result);
            assertEquals("user123", result.getUserId());
        }

        @Test
        @DisplayName("Should return null when session expired")
        void validateAndRefreshSession_shouldReturnNullWhenExpired() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);

            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .userId("user123")
                .expiresAt(Instant.now().minusSeconds(100)) // Expired
                .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("bionicpro:session:session-123")).thenReturn(sessionData);

            // Act
            SessionData result = sessionService.validateAndRefreshSession("session-123");

            // Assert
            assertNull(result);
            verify(redisTemplate).delete("bionicpro:session:session-123");
        }

        @Test
        @DisplayName("Should return null when session not found")
        void validateAndRefreshSession_shouldReturnNullWhenNotFound() {
            // Arrange
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            // Act
            SessionData result = sessionService.validateAndRefreshSession("non-existent");

            // Assert
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Session Rotation")
    class SessionRotationTest {

        @Test
        @DisplayName("Should rotate session successfully")
        void rotateSession_shouldRotateSession() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");

            Cookie sessionCookie = new Cookie("BIONICPRO_SESSION", "old-session-id");
            
            SessionData oldSession = SessionData.builder()
                .sessionId("old-session-id")
                .userId("user123")
                .username("testuser")
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();

            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("bionicpro:session:old-session-id")).thenReturn(oldSession);

            // Act
            sessionService.rotateSession(request, response);

            // Assert
            verify(valueOperations).set(anyString(), any(SessionData.class), any(Duration.class));
            verify(redisTemplate).delete("bionicpro:session:old-session-id");
            
            // Verify new cookie is set
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            
            Cookie newCookie = cookieCaptor.getValue();
            assertEquals("BIONICPRO_SESSION", newCookie.getName());
            assertNotEquals("old-session-id", newCookie.getValue());
        }

        @Test
        @DisplayName("Should do nothing when no session cookie")
        void rotateSession_shouldDoNothingWhenNoCookie() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");

            when(request.getCookies()).thenReturn(null);

            // Act
            sessionService.rotateSession(request, response);

            // Assert
            verify(redisTemplate, never()).delete(anyString());
            verify(response, never()).addCookie(any());
        }
    }

    @Nested
    @DisplayName("Session Invalidation")
    class SessionInvalidationTest {

        @Test
        @DisplayName("Should invalidate session and clear cookie")
        void invalidateSession_shouldInvalidateSessionAndClearCookie() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");

            Cookie sessionCookie = new Cookie("BIONICPRO_SESSION", "session-123");
            
            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});

            // Act
            sessionService.invalidateSession(request, response);

            // Assert
            verify(redisTemplate).delete("bionicpro:session:session-123");
            
            // Verify cookie is cleared
            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            
            Cookie clearedCookie = cookieCaptor.getValue();
            assertEquals("BIONICPRO_SESSION", clearedCookie.getName());
            assertEquals(0, clearedCookie.getMaxAge());
        }
    }

    @Nested
    @DisplayName("Session Expiration")
    class SessionExpirationTest {

        @Test
        @DisplayName("Should return expiration time when session exists")
        void getSessionExpiration_shouldReturnExpirationTime() throws Exception {
            // Arrange
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");

            Cookie sessionCookie = new Cookie("BIONICPRO_SESSION", "session-123");
            Instant expiresAt = Instant.now().plusSeconds(1800);
            
            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .expiresAt(expiresAt)
                .build();

            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("bionicpro:session:session-123")).thenReturn(sessionData);

            // Act
            Instant result = sessionService.getSessionExpiration(request);

            // Assert
            assertNotNull(result);
            assertEquals(expiresAt, result);
        }

        @Test
        @DisplayName("Should return null when no session cookie")
        void getSessionExpiration_shouldReturnNullWhenNoCookie() {
            // Arrange
            when(request.getCookies()).thenReturn(null);

            // Act
            Instant result = sessionService.getSessionExpiration(request);

            // Assert
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Token Retrieval")
    class TokenRetrievalTest {

        @Test
        @DisplayName("Should return decrypted access token")
        void getAccessToken_shouldReturnDecryptedToken() throws Exception {
            // Arrange
            String encryptedToken = "encryptedToken123";
            String decryptedToken = "decryptedToken456";
            
            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .accessToken(encryptedToken)
                .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("bionicpro:session:session-123")).thenReturn(sessionData);
            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn(decryptedToken.getBytes());

            // Act
            String result = sessionService.getAccessToken("session-123");

            // Assert
            assertNotNull(result);
            assertEquals(decryptedToken, result);
        }

        @Test
        @DisplayName("Should return null when no access token")
        void getAccessToken_shouldReturnNullWhenNoToken() throws Exception {
            // Arrange
            SessionData sessionData = SessionData.builder()
                .sessionId("session-123")
                .accessToken(null)
                .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("bionicpro:session:session-123")).thenReturn(sessionData);

            // Act
            String result = sessionService.getAccessToken("session-123");

            // Assert
            assertNull(result);
        }
    }
}
