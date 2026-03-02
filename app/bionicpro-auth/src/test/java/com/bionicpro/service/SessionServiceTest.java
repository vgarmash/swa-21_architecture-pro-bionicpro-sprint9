package com.bionicpro.service;

import com.bionicpro.audit.AuditService;
import com.bionicpro.mapper.SessionDataMapper;
import com.bionicpro.mapper.SessionDataMapperImpl;
import com.bionicpro.model.AuthRequestData;
import com.bionicpro.model.SessionData;
import com.bionicpro.repository.SessionRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionService.
 * Tests session management: creation, validation, refresh, revocation, invalidation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService Tests")
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private BytesEncryptor bytesEncryptor;

    @Mock
    private AuditService auditService;

    @Mock
    private RestTemplate restTemplate;

    private final SessionDataMapper sessionDataMapper = new SessionDataMapperImpl();

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private SessionServiceImpl sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl(
                sessionRepository,
                bytesEncryptor,
                auditService,
                sessionDataMapper,
                restTemplate,
                redisTemplate
        );
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String encodedCipherToken(String token) {
        return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Auth Request Storage")
    class AuthRequestStorageTest {

        @Test
        @DisplayName("Should store auth request data with state")
        void storeAuthRequest_shouldStoreAuthRequestData() {
            String state = "test-state";
            AuthRequestData authRequestData = AuthRequestData.builder()
                    .redirectUri("/dashboard")
                    .codeVerifier("verifier-1")
                    .nonce("nonce-1")
                    .createdAt(Instant.now())
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            sessionService.storeAuthRequest(state, authRequestData);

            ArgumentCaptor<AuthRequestData> captor = ArgumentCaptor.forClass(AuthRequestData.class);
            verify(valueOperations).set(eq("auth:request:" + state), captor.capture(), eq(Duration.ofMinutes(10)));
            assertEquals("/dashboard", captor.getValue().getRedirectUri());
            assertEquals("verifier-1", captor.getValue().getCodeVerifier());
            assertEquals("nonce-1", captor.getValue().getNonce());
        }

        @Test
        @DisplayName("Should return auth request data for existing state")
        void getAuthRequestData_shouldReturnDataWhenExists() {
            String state = "test-state";
            AuthRequestData stored = AuthRequestData.builder()
                    .redirectUri("/dashboard")
                    .codeVerifier("verifier-1")
                    .nonce("nonce-1")
                    .createdAt(Instant.now())
                    .build();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete("auth:request:" + state)).thenReturn(stored);

            AuthRequestData result = sessionService.getAuthRequestData(state);

            assertNotNull(result);
            assertEquals("/dashboard", result.getRedirectUri());
            assertEquals("verifier-1", result.getCodeVerifier());
            assertEquals("nonce-1", result.getNonce());
        }

        @Test
        @DisplayName("Should support legacy redirect-only auth request value")
        void getAuthRequestData_shouldSupportLegacyStringValue() {
            String state = "legacy-state";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete("auth:request:" + state)).thenReturn("/legacy");

            AuthRequestData result = sessionService.getAuthRequestData(state);

            assertNotNull(result);
            assertEquals("/legacy", result.getRedirectUri());
            assertNull(result.getCodeVerifier());
            assertNull(result.getNonce());
        }

        @Test
        @DisplayName("Should return null for non-existent state")
        void getAuthRequest_shouldReturnNullForNonExistentState() {
            String state = "non-existent-state";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.getAndDelete("auth:request:" + state)).thenReturn(null);

            String result = sessionService.getAuthRequest(state);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Session Creation")
    class SessionCreationTest {

        @Test
        @DisplayName("Should create session with tokens")
        void createSession_shouldCreateSessionWithTokens() throws Exception {
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");
            setFieldValue(sessionService, "cookieSecure", false);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(bytesEncryptor.encrypt(any())).thenReturn("encrypted".getBytes(StandardCharsets.UTF_8));

            OidcIdToken idToken = new OidcIdToken(
                    "id-token-value",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", "user123", "preferred_username", "testuser")
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

            sessionService.createSession(request, response, idToken, accessToken, refreshToken);

            verify(valueOperations).set(anyString(), any(SessionData.class), any(Duration.class));

            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());

            Cookie setCookie = cookieCaptor.getValue();
            assertEquals("BIONICPRO_SESSION", setCookie.getName());
            assertTrue(setCookie.isHttpOnly());
            assertEquals("/", setCookie.getPath());
        }
    }

    @Nested
    @DisplayName("Session Validation and Refresh")
    class SessionValidationAndRefreshTest {

        @Test
        @DisplayName("Should return null when session expired")
        void validateAndRefreshSession_shouldReturnNullWhenExpired() throws Exception {
            setFieldValue(sessionService, "sessionTimeoutMinutes", 30);

            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .expiresAt(Instant.now().minusSeconds(100))
                    .build();

            when(sessionRepository.findById("session-123")).thenReturn(Optional.of(sessionData));

            SessionData result = sessionService.validateAndRefreshSession("session-123");

            assertNull(result);
            verify(sessionRepository).deleteById("session-123");
            verify(auditService).logSessionExpired("user123", "session-123");
        }

        @Test
        @DisplayName("Should invalidate session when access token expiring and refresh token absent")
        void validateAndRefreshSession_shouldInvalidateWhenNoRefreshToken() throws Exception {
            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .expiresAt(Instant.now().plusSeconds(1800))
                    .accessTokenExpiresAt(Instant.now().plusSeconds(5))
                    .refreshToken(null)
                    .build();

            when(sessionRepository.findById("session-123")).thenReturn(Optional.of(sessionData));

            SessionData result = sessionService.validateAndRefreshSession("session-123");

            assertNull(result);
            verify(sessionRepository).deleteById("session-123");
        }

        @Test
        @DisplayName("Should refresh token and keep session valid when refresh succeeds")
        void validateAndRefreshSession_shouldRefreshAndReturnSession() throws Exception {
            setFieldValue(sessionService, "keycloakUrl", "http://keycloak:8080");
            setFieldValue(sessionService, "keycloakRealm", "reports-realm");
            setFieldValue(sessionService, "clientId", "bionicpro-auth");

            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .expiresAt(Instant.now().plusSeconds(1800))
                    .accessTokenExpiresAt(Instant.now().plusSeconds(10))
                    .refreshToken(encodedCipherToken("cipher-refresh-token"))
                    .build();

            Map<String, Object> tokenPayload = new HashMap<>();
            tokenPayload.put("access_token", "new-access-token");
            tokenPayload.put("refresh_token", "new-refresh-token");
            tokenPayload.put("expires_in", 120);
            tokenPayload.put("refresh_expires_in", 600);

            when(sessionRepository.findById("session-123")).thenReturn(Optional.of(sessionData));
            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn("plain-refresh-token".getBytes(StandardCharsets.UTF_8));
            when(bytesEncryptor.encrypt(any(byte[].class))).thenReturn("encrypted-value".getBytes(StandardCharsets.UTF_8));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(tokenPayload));

            SessionData result = sessionService.validateAndRefreshSession("session-123");

            assertNotNull(result);
            assertNotNull(result.getAccessTokenExpiresAt());
            assertTrue(result.getAccessTokenExpiresAt().isAfter(Instant.now()));
            assertNotNull(result.getLastAccessedAt());

            verify(sessionRepository).save(eq("session-123"), any(SessionData.class));
            verify(auditService).logTokenRefresh(eq("user123"), eq("session-123"), isNull());
        }

        @Test
        @DisplayName("Should invalidate session when refresh token flow fails")
        void validateAndRefreshSession_shouldInvalidateWhenRefreshFails() throws Exception {
            setFieldValue(sessionService, "keycloakUrl", "http://keycloak:8080");
            setFieldValue(sessionService, "keycloakRealm", "reports-realm");

            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .expiresAt(Instant.now().plusSeconds(1800))
                    .accessTokenExpiresAt(Instant.now().plusSeconds(5))
                    .refreshToken(encodedCipherToken("cipher-refresh-token"))
                    .build();

            when(sessionRepository.findById("session-123")).thenReturn(Optional.of(sessionData));
            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn("plain-refresh-token".getBytes(StandardCharsets.UTF_8));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());

            SessionData result = sessionService.validateAndRefreshSession("session-123");

            assertNull(result);
            verify(sessionRepository).deleteById("session-123");
        }
    }

    @Nested
    @DisplayName("Refresh Token Endpoint")
    class RefreshTokenEndpointTest {

        @Test
        @DisplayName("refreshAccessToken should return null on exception")
        void refreshAccessToken_shouldReturnNullOnException() throws Exception {
            setFieldValue(sessionService, "keycloakUrl", "http://keycloak:8080");
            setFieldValue(sessionService, "keycloakRealm", "reports-realm");

            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .refreshToken(encodedCipherToken("cipher-refresh-token"))
                    .build();

            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn("plain-refresh-token".getBytes(StandardCharsets.UTF_8));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("timeout"));

            SessionData result = sessionService.refreshAccessToken(sessionData);

            assertNull(result);
        }

        @Test
        @DisplayName("refreshAccessToken should return null on malformed token body")
        void refreshAccessToken_shouldReturnNullOnMalformedBody() throws Exception {
            setFieldValue(sessionService, "keycloakUrl", "http://keycloak:8080");
            setFieldValue(sessionService, "keycloakRealm", "reports-realm");

            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .refreshToken(encodedCipherToken("cipher-refresh-token"))
                    .build();

            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn("plain-refresh-token".getBytes(StandardCharsets.UTF_8));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(Map.of("refresh_token", "new-refresh")));

            SessionData result = sessionService.refreshAccessToken(sessionData);

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Logout and Revocation")
    class LogoutAndRevocationTest {

        @Test
        @DisplayName("Should revoke token and clear session cookie on invalidateSessionWithTokenRevocation")
        void invalidateSessionWithTokenRevocation_shouldRevokeAndClearCookie() throws Exception {
            setFieldValue(sessionService, "cookieName", "BIONICPRO_SESSION");
            setFieldValue(sessionService, "keycloakUrl", "http://keycloak:8080");
            setFieldValue(sessionService, "keycloakRealm", "reports-realm");
            setFieldValue(sessionService, "clientId", "bionicpro-auth");

            Cookie sessionCookie = new Cookie("BIONICPRO_SESSION", "session-123");
            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .refreshToken(encodedCipherToken("cipher-refresh-token"))
                    .build();

            when(request.getCookies()).thenReturn(new Cookie[]{sessionCookie});
            when(sessionRepository.findById("session-123")).thenReturn(Optional.of(sessionData));
            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn("plain-refresh-token".getBytes(StandardCharsets.UTF_8));
            when(restTemplate.postForEntity(contains("/protocol/openid-connect/token/logout"), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(ResponseEntity.ok("ok"));

            sessionService.invalidateSessionWithTokenRevocation(request, response);

            verify(restTemplate).postForEntity(contains("/protocol/openid-connect/token/logout"), any(HttpEntity.class), eq(String.class));
            verify(sessionRepository).deleteById("session-123");

            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookieCaptor.capture());
            assertEquals(0, cookieCaptor.getValue().getMaxAge());
        }

        @Test
        @DisplayName("revokeTokens should return true when refresh token is null")
        void revokeTokens_shouldReturnTrueWhenTokenNull() {
            assertTrue(sessionService.revokeTokens(null));
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("revokeTokens should return true when Keycloak call fails")
        void revokeTokens_shouldReturnTrueOnException() throws Exception {
            setFieldValue(sessionService, "keycloakUrl", "http://keycloak:8080");
            setFieldValue(sessionService, "keycloakRealm", "reports-realm");
            setFieldValue(sessionService, "clientId", "bionicpro-auth");

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RuntimeException("connection error"));

            boolean result = sessionService.revokeTokens("refresh-token");

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("Token Retrieval")
    class TokenRetrievalTest {

        @Test
        @DisplayName("Should return decrypted access token")
        void getAccessToken_shouldReturnDecryptedToken() {
            String encryptedToken = encodedCipherToken("cipher-access-token");
            String decryptedToken = "decryptedToken456";

            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .accessToken(encryptedToken)
                    .build();

            when(sessionRepository.findById("session-123")).thenReturn(Optional.of(sessionData));
            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn(decryptedToken.getBytes(StandardCharsets.UTF_8));

            String result = sessionService.getAccessToken("session-123");

            assertNotNull(result);
            assertEquals(decryptedToken, result);
        }
    }

    @Nested
    @DisplayName("Direct refresh request payload")
    class DirectRefreshRequestPayloadTest {

        @Test
        @DisplayName("Should send expected refresh request params")
        void refreshAccessToken_shouldSendExpectedRequest() throws Exception {
            setFieldValue(sessionService, "keycloakUrl", "http://keycloak:8080");
            setFieldValue(sessionService, "keycloakRealm", "reports-realm");
            setFieldValue(sessionService, "clientId", "bionicpro-auth");

            SessionData sessionData = SessionData.builder()
                    .sessionId("session-123")
                    .userId("user123")
                    .refreshToken(encodedCipherToken("cipher-refresh-token"))
                    .build();

            Map<String, Object> tokenPayload = new HashMap<>();
            tokenPayload.put("access_token", "new-access-token");
            tokenPayload.put("expires_in", 120);

            when(bytesEncryptor.decrypt(any(byte[].class))).thenReturn("plain-refresh-token".getBytes(StandardCharsets.UTF_8));
            when(bytesEncryptor.encrypt(any(byte[].class))).thenReturn("enc".getBytes(StandardCharsets.UTF_8));
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(tokenPayload));

            sessionService.refreshAccessToken(sessionData);

            ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(eq("http://keycloak:8080/realms/reports-realm/protocol/openid-connect/token"), captor.capture(), eq(Map.class));

            @SuppressWarnings("unchecked")
            MultiValueMap<String, String> body = (MultiValueMap<String, String>) captor.getValue().getBody();
            assertEquals("refresh_token", body.getFirst("grant_type"));
            assertEquals("bionicpro-auth", body.getFirst("client_id"));
            assertEquals("plain-refresh-token", body.getFirst("refresh_token"));
        }
    }
}
