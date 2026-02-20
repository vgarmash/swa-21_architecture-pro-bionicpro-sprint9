package com.bionicpro.controller;

import com.bionicpro.audit.AuditService;
import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.mapper.SessionDataMapper;
import com.bionicpro.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthController.
 * Tests authentication endpoints: login, callback, status, logout, refresh.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

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

    @Mock
    private HttpServletResponse response;

    private MockHttpServletResponse servletResponse;

    @BeforeEach
    void setUp() {
        servletResponse = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /api/auth/login")
    class LoginTest {

        @Test
        @DisplayName("Should redirect to Keycloak login page with state parameter")
        void login_shouldRedirectToKeycloakWithState() throws Exception {
            // Arrange
            String redirectUri = "/dashboard";
            
            // Act
            authController.login(redirectUri, servletResponse);
            
            // Assert
            assertEquals(302, servletResponse.getStatus());
            String location = servletResponse.getHeader("Location");
            assertNotNull(location);
            assertTrue(location.contains("/oauth2/authorization/keycloak"));
            assertTrue(location.contains("state="));
            assertTrue(location.contains("redirect_uri=" + redirectUri));
            verify(sessionService).storeAuthRequest(anyString(), eq(redirectUri));
        }

        @Test
        @DisplayName("Should use default redirect URI when not provided")
        void login_shouldUseDefaultRedirectUri() throws Exception {
            // Arrange
            String defaultRedirectUri = "/";
            
            // Act
            authController.login(defaultRedirectUri, servletResponse);
            
            // Assert
            assertEquals(302, servletResponse.getStatus());
            verify(sessionService).storeAuthRequest(anyString(), eq(defaultRedirectUri));
        }
    }

    @Nested
    @DisplayName("GET /api/auth/callback")
    class CallbackTest {

        @Test
        @DisplayName("Should redirect to error page when error parameter is present")
        void callback_shouldRedirectToErrorWhenErrorPresent() throws Exception {
            // Arrange
            String error = "access_denied";
            String state = "test-state";
            
            // Act
            authController.callback("code", state, error, request, servletResponse);
            
            // Assert
            assertEquals(302, servletResponse.getStatus());
            assertTrue(servletResponse.getHeader("Location").contains("error=access_denied"));
        }

        @Test
        @DisplayName("Should create session when authentication is successful")
        void callback_shouldCreateSessionOnSuccessfulAuth() throws Exception {
            // Arrange
            String code = "auth-code";
            String state = "test-state";
            String redirectUri = "/dashboard";
            
            when(sessionService.getAuthRequest(state)).thenReturn(redirectUri);
            
            // Setup authentication with tokens in principal attributes
            OidcIdToken idToken = new OidcIdToken(
                "token-value",
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
            
            // Create mock principal with getAttributes() method
            DefaultOidcUser oidcUser = mock(DefaultOidcUser.class);
            Map<String, Object> attributes = new java.util.HashMap<>();
            attributes.put("sub", "user123");
            attributes.put("preferred_username", "testuser");
            attributes.put("id_token", "token-value");
            attributes.put("access_token", accessToken);
            
            when(oidcUser.getAttributes()).thenReturn(attributes);
            
            org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken authToken = 
                new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                    oidcUser,
                    Collections.emptyList(),
                    "keycloak"
                );
            
            SecurityContext securityContext = mock(SecurityContext.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authToken);
            
            // Act
            authController.callback(code, state, null, request, servletResponse);
            
            // Assert
            assertEquals(302, servletResponse.getStatus());
            verify(sessionService).createSession(eq(request), eq(servletResponse), any(OidcIdToken.class), any(OAuth2AccessToken.class), eq(null));
        }
    }

    @Nested
    @DisplayName("GET /api/auth/status")
    class StatusTest {

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void status_shouldReturnUnauthorizedWhenNotAuthenticated() {
            // Arrange
            SecurityContextHolder.clearContext();
            when(sessionDataMapper.toUnauthenticatedResponse())
                .thenReturn(AuthStatusResponse.builder().authenticated(false).build());

            // Act
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);
            
            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertFalse(result.getBody().isAuthenticated());
        }

        @Test
        @DisplayName("Should return 401 when session ID is null")
        void status_shouldReturnUnauthorizedWhenSessionIdIsNull() {
            // Arrange
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);

            SecurityContext securityContext = mock(SecurityContext.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);

            when(sessionService.getSessionIdFromRequest(request)).thenReturn(null);
            when(sessionDataMapper.toUnauthenticatedResponse())
                .thenReturn(AuthStatusResponse.builder().authenticated(false).build());

            // Act
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertFalse(result.getBody().isAuthenticated());
        }

        @Test
        @DisplayName("Should return 401 when session data is null")
        void status_shouldReturnUnauthorizedWhenSessionDataIsNull() {
            // Arrange
            String sessionId = "test-session-id";

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);

            SecurityContext securityContext = mock(SecurityContext.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);

            when(sessionService.getSessionIdFromRequest(request)).thenReturn(sessionId);
            when(sessionService.getSession(sessionId)).thenReturn(null);
            when(sessionDataMapper.toUnauthenticatedResponse())
                .thenReturn(AuthStatusResponse.builder().authenticated(false).build());

            // Act
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertFalse(result.getBody().isAuthenticated());
        }

        @Test
        @DisplayName("Should return user info when authenticated")
        void status_shouldReturnUserInfoWhenAuthenticated() {
            // Arrange
            String sessionId = "test-session-id";
            String userId = "user123";
            List<String> roles = List.of("ROLE_USER", "ROLE_ADMIN");
            Instant expiresAt = Instant.now().plusSeconds(1800);

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);

            SecurityContext securityContext = mock(SecurityContext.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);

            com.bionicpro.model.SessionData sessionData = com.bionicpro.model.SessionData.builder()
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

            // Act
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);

            // Assert
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
            // Arrange
            String sessionId = "test-session-id";
            String userId = "user123";

            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);

            SecurityContext securityContext = mock(SecurityContext.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);

            com.bionicpro.model.SessionData sessionData = com.bionicpro.model.SessionData.builder()
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
            
            // Act
            ResponseEntity<AuthStatusResponse> result = authController.getStatus(request);
            
            // Assert
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
            // Arrange
            Authentication authentication = mock(Authentication.class);
            
            SecurityContext securityContext = mock(SecurityContext.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            
            // Act
            ResponseEntity<Map<String, String>> result = authController.logout(request, servletResponse);
            
            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Logged out successfully", result.getBody().get("message"));
            verify(sessionService).invalidateSessionWithTokenRevocation(request, servletResponse);
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class RefreshTest {

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void refresh_shouldReturnUnauthorizedWhenNotAuthenticated() {
            // Arrange
            SecurityContextHolder.clearContext();
            
            // Act
            ResponseEntity<Map<String, String>> result = authController.refresh(request, servletResponse);
            
            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
            assertEquals("not_authenticated", result.getBody().get("error"));
        }

        @Test
        @DisplayName("Should rotate session when authenticated")
        void refresh_shouldRotateSessionWhenAuthenticated() {
            // Arrange
            Authentication authentication = mock(Authentication.class);
            when(authentication.isAuthenticated()).thenReturn(true);
            
            SecurityContext securityContext = mock(SecurityContext.class);
            SecurityContextHolder.setContext(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            
            // Act
            ResponseEntity<Map<String, String>> result = authController.refresh(request, servletResponse);
            
            // Assert
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Session refreshed", result.getBody().get("message"));
            verify(sessionService).rotateSession(request, servletResponse);
        }
    }
}
