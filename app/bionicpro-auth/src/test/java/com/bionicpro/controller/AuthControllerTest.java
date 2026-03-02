package com.bionicpro.controller;

import com.bionicpro.audit.AuditService;
import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.mapper.SessionDataMapper;
import com.bionicpro.model.SessionData;
import com.bionicpro.service.AuthService;
import com.bionicpro.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для AuthController.
 * Тестирует эндпоинты аутентификации: login, callback, status, logout, refresh.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditService auditService;

    @Mock
    private SessionDataMapper sessionDataMapper;

    @InjectMocks
    private AuthController authController;

    @Mock
    private HttpServletRequest request;

    private MockHttpServletResponse servletResponse;

    @BeforeEach
    void setUp() {
        servletResponse = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("GET /api/auth/login")
    class LoginTest {

        @Test
        @DisplayName("Should call authService.initiateAuthentication with the given redirectUri")
        void login_shouldCallInitiateAuthentication() throws Exception {
            // Подготовка
            String redirectUri = "/dashboard";

            // Действие
            authController.login(redirectUri, request, servletResponse);

            // Проверка
            verify(authService).initiateAuthentication(request, servletResponse, redirectUri);
        }

        @Test
        @DisplayName("Should call authService.initiateAuthentication for default redirect URI")
        void login_shouldCallInitiateAuthenticationForDefaultRedirectUri() throws Exception {
            // Подготовка
            String defaultRedirectUri = "/";

            // Действие
            authController.login(defaultRedirectUri, request, servletResponse);

            // Проверка
            verify(authService).initiateAuthentication(request, servletResponse, defaultRedirectUri);
        }
    }

    @Nested
    @DisplayName("GET /api/auth/callback")
    class CallbackTest {

        @Test
        @DisplayName("Should redirect to error page when error parameter is present")
        void callback_shouldRedirectToErrorWhenErrorPresent() throws Exception {
            // Подготовка
            String error = "access_denied";
            String state = "test-state";

            // Действие
            authController.callback("code", state, error, null, request, servletResponse);

            // Проверка
            assertEquals(302, servletResponse.getStatus());
            assertTrue(servletResponse.getHeader("Location").contains("error=access_denied"));
            verify(authService, never()).handleCallback(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should redirect to URL returned by handleCallback on successful auth")
        void callback_shouldRedirectOnSuccessfulAuth() throws Exception {
            // Подготовка
            String code = "auth-code";
            String state = "test-state";

            when(authService.handleCallback(any(HttpServletRequest.class), any(MockHttpServletResponse.class),
                    eq(code), eq(state), isNull()))
                    .thenReturn(Map.of("redirect", "/dashboard"));

            // Действие
            authController.callback(code, state, null, null, request, servletResponse);

            // Проверка
            assertEquals(302, servletResponse.getStatus());
            assertEquals("/dashboard", servletResponse.getHeader("Location"));
            verify(authService).handleCallback(any(), any(), eq(code), eq(state), isNull());
        }

        @Test
        @DisplayName("Should redirect to error page when handleCallback returns error key")
        void callback_shouldRedirectToErrorOnCallbackError() throws Exception {
            // Подготовка
            String code = "auth-code";
            String state = "test-state";

            when(authService.handleCallback(any(HttpServletRequest.class), any(MockHttpServletResponse.class),
                    eq(code), eq(state), isNull()))
                    .thenReturn(Map.of("error", "Token exchange failed"));

            // Действие
            authController.callback(code, state, null, null, request, servletResponse);

            // Проверка
            assertEquals(302, servletResponse.getStatus());
            String location = servletResponse.getHeader("Location");
            assertNotNull(location);
            assertTrue(location.contains("error="));
        }
    }

    @Nested
    @DisplayName("GET /api/auth/status")
    class StatusTest {

        @Test
        @DisplayName("Should return 401 when session ID is null")
        void status_shouldReturnUnauthorizedWhenSessionIdIsNull() {
            // Подготовка
            when(sessionService.getSessionIdFromRequest(request)).thenReturn(null);
            when(sessionDataMapper.toUnauthenticatedResponse())
                    .thenReturn(AuthStatusResponse.builder().authenticated(false).build());

            // Действие
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);

            // Проверка
            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertFalse(result.getBody().isAuthenticated());
        }

        @Test
        @DisplayName("Should return 401 when session data is null")
        void status_shouldReturnUnauthorizedWhenSessionDataIsNull() {
            // Подготовка
            String sessionId = "test-session-id";

            when(sessionService.getSessionIdFromRequest(request)).thenReturn(sessionId);
            when(sessionService.getSession(sessionId)).thenReturn(null);
            when(sessionDataMapper.toUnauthenticatedResponse())
                    .thenReturn(AuthStatusResponse.builder().authenticated(false).build());

            // Действие
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);

            // Проверка
            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertFalse(result.getBody().isAuthenticated());
        }

        @Test
        @DisplayName("Should return user info when session exists")
        void status_shouldReturnUserInfoWhenAuthenticated() {
            // Подготовка
            String sessionId = "test-session-id";
            String userId = "user123";
            List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");
            Instant expiresAt = Instant.now().plusSeconds(1800);

            SessionData sessionData = SessionData.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .username("testuser")
                    .roles(roles)
                    .expiresAt(expiresAt)
                    .build();

            when(sessionService.getSessionIdFromRequest(request)).thenReturn(sessionId);
            when(sessionService.getSession(sessionId)).thenReturn(sessionData);

            AuthStatusResponse expectedResponse = AuthStatusResponse.builder()
                    .authenticated(true)
                    .userId(userId)
                    .username("testuser")
                    .roles(roles)
                    .sessionExpiresAt(expiresAt.toString())
                    .build();
            when(sessionDataMapper.toAuthStatusResponse(sessionData)).thenReturn(expectedResponse);

            // Действие
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);

            // Проверка
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isAuthenticated());
            assertEquals(userId, result.getBody().getUserId());
            assertEquals(roles, result.getBody().getRoles());
            assertNotNull(result.getBody().getSessionExpiresAt());
        }

        @Test
        @DisplayName("Should handle null session expiration")
        void status_shouldHandleNullSessionExpiration() {
            // Подготовка
            String sessionId = "test-session-id";
            String userId = "user123";

            SessionData sessionData = SessionData.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .username("testuser")
                    .roles(Collections.emptyList())
                    .expiresAt(null)
                    .build();

            when(sessionService.getSessionIdFromRequest(request)).thenReturn(sessionId);
            when(sessionService.getSession(sessionId)).thenReturn(sessionData);

            AuthStatusResponse expectedResponse = AuthStatusResponse.builder()
                    .authenticated(true)
                    .userId(userId)
                    .username("testuser")
                    .roles(Collections.emptyList())
                    .sessionExpiresAt(null)
                    .build();
            when(sessionDataMapper.toAuthStatusResponse(sessionData)).thenReturn(expectedResponse);

            // Действие
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);

            // Проверка
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isAuthenticated());
            assertNull(result.getBody().getSessionExpiresAt());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutTest {

        @Test
        @DisplayName("Should invalidate session and return success message")
        void logout_shouldInvalidateSessionAndReturnSuccess() {
            // Действие
            ResponseEntity<Map<String, String>> result = authController.logout(request, servletResponse);

            // Проверка
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Logged out successfully", result.getBody().get("message"));
            verify(sessionService).invalidateSessionWithTokenRevocation(request, servletResponse);
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshTest {

        @Test
        @DisplayName("Should return 401 when session ID is null")
        void refresh_shouldReturnUnauthorizedWhenNotAuthenticated() {
            // Подготовка
            when(sessionService.getSessionIdFromRequest(request)).thenReturn(null);

            // Действие
            ResponseEntity<Map<String, String>> result = authController.refresh(request, servletResponse);

            // Проверка
            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertEquals("not_authenticated", result.getBody().get("error"));
        }

        @Test
        @DisplayName("Should rotate session when session ID is present")
        void refresh_shouldRotateSessionWhenAuthenticated() {
            // Подготовка
            when(sessionService.getSessionIdFromRequest(request)).thenReturn("session-123");

            // Действие
            ResponseEntity<Map<String, String>> result = authController.refresh(request, servletResponse);

            // Проверка
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Session refreshed", result.getBody().get("message"));
            verify(sessionService).rotateSession(request, servletResponse);
        }
    }
}
