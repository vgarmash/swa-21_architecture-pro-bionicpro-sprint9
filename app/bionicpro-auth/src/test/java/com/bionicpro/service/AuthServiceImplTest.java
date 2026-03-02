package com.bionicpro.service;

import com.bionicpro.audit.AuditService;
import com.bionicpro.model.AuthRequestData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl.
 * Covers PKCE generation, redirect params and callback token exchange branches.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Tests")
class AuthServiceImplTest {

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditService auditService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        lenient().when(clientRegistrationRepository.findByRegistrationId("keycloak")).thenReturn(buildClientRegistration());
        ReflectionTestUtils.setField(authService, "keycloakUrl", "http://keycloak:8080");
        ReflectionTestUtils.setField(authService, "keycloakRealm", "reports-realm");
        ReflectionTestUtils.setField(authService, "clientId", "bionicpro-auth");
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");
    }

    @Nested
    @DisplayName("PKCE generation")
    class PkceGenerationTest {

        @Test
        @DisplayName("Should generate valid code_verifier and S256 code_challenge")
        void generateCodeVerifierAndChallenge_shouldBeValid() throws Exception {
            String codeVerifier = authService.generateCodeVerifier();
            assertNotNull(codeVerifier);
            assertTrue(codeVerifier.length() >= 43 && codeVerifier.length() <= 128);

            String codeChallenge = authService.generateCodeChallenge(codeVerifier);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

            assertEquals(expected, codeChallenge);
        }
    }

    @Nested
    @DisplayName("Initiate authentication")
    class InitiateAuthenticationTest {

        @Test
        @DisplayName("Should create redirect URL with required OAuth2+PKCE params and store state linkage")
        void initiateAuthentication_shouldBuildAuthorizationRedirectWithPkce() throws Exception {
            ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<AuthRequestData> authRequestCaptor = ArgumentCaptor.forClass(AuthRequestData.class);
            ArgumentCaptor<String> locationCaptor = ArgumentCaptor.forClass(String.class);

            authService.initiateAuthentication(request, response, "/dashboard");

            verify(sessionService).storeAuthRequest(stateCaptor.capture(), authRequestCaptor.capture());
            verify(response).sendRedirect(locationCaptor.capture());

            String state = stateCaptor.getValue();
            AuthRequestData authRequestData = authRequestCaptor.getValue();
            String redirectLocation = locationCaptor.getValue();

            assertNotNull(authRequestData);
            assertEquals("/dashboard", authRequestData.getRedirectUri());
            assertNotNull(authRequestData.getCodeVerifier());
            assertFalse(authRequestData.getCodeVerifier().isBlank());
            assertNotNull(authRequestData.getNonce());
            assertFalse(authRequestData.getNonce().isBlank());

            UriComponents uri = UriComponentsBuilder.fromUriString(redirectLocation).build();
            MultiValueMap<String, String> query = uri.getQueryParams();

            assertEquals("code", query.getFirst("response_type"));
            assertEquals("bionicpro-auth", query.getFirst("client_id"));
            assertEquals("http://localhost:8000/api/auth/callback", query.getFirst("redirect_uri"));
            assertEquals(state, query.getFirst("state"));
            assertEquals("S256", query.getFirst("code_challenge_method"));
            assertEquals(authService.generateCodeChallenge(authRequestData.getCodeVerifier()), query.getFirst("code_challenge"));

            String scope = query.getFirst("scope");
            assertNotNull(scope);
            assertTrue(scope.contains("openid"));
            assertTrue(scope.contains("profile"));
            assertTrue(scope.contains("email"));

            String nonce = query.getFirst("nonce");
            assertNotNull(nonce);
            assertEquals(authRequestData.getNonce(), nonce);
        }
    }

    @Nested
    @DisplayName("Handle callback")
    class HandleCallbackTest {

        private AuthRequestData validAuthRequestData() {
            String codeVerifier = authService.generateCodeVerifier();
            return AuthRequestData.builder()
                    .redirectUri("/dashboard")
                    .codeVerifier(codeVerifier)
                    .nonce("nonce-1")
                    .createdAt(Instant.now())
                    .build();
        }

        @Test
        @DisplayName("Should exchange authorization code for tokens and create session")
        void handleCallback_shouldCreateSessionOnSuccess() {
            String state = "state-1";
            AuthRequestData authRequestData = validAuthRequestData();

            when(sessionService.getAuthRequestData(state)).thenReturn(authRequestData);
            when(sessionService.getSessionIdFromRequest(request)).thenReturn("session-1");
            when(restTemplate.postForEntity(eq("http://keycloak:8080/realms/reports-realm/protocol/openid-connect/token"), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(buildTokenResponse()));

            Map<String, String> result = authService.handleCallback(request, response, "auth-code", state, null);

            assertEquals("/dashboard", result.get("redirect"));
            assertNull(result.get("error"));

            ArgumentCaptor<HttpEntity> tokenRequestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(eq("http://keycloak:8080/realms/reports-realm/protocol/openid-connect/token"), tokenRequestCaptor.capture(), eq(Map.class));

            @SuppressWarnings("unchecked")
            MultiValueMap<String, String> tokenBody = (LinkedMultiValueMap<String, String>) tokenRequestCaptor.getValue().getBody();
            assertEquals("authorization_code", tokenBody.getFirst("grant_type"));
            assertEquals("auth-code", tokenBody.getFirst("code"));
            assertEquals("bionicpro-auth", tokenBody.getFirst("client_id"));
            assertEquals(authRequestData.getCodeVerifier(), tokenBody.getFirst("code_verifier"));

            ArgumentCaptor<OidcIdToken> idTokenCaptor = ArgumentCaptor.forClass(OidcIdToken.class);
            ArgumentCaptor<OAuth2AccessToken> accessTokenCaptor = ArgumentCaptor.forClass(OAuth2AccessToken.class);
            ArgumentCaptor<OAuth2RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(OAuth2RefreshToken.class);

            verify(sessionService).createSession(eq(request), eq(response), idTokenCaptor.capture(), accessTokenCaptor.capture(), refreshTokenCaptor.capture());

            assertEquals("user-123", idTokenCaptor.getValue().getSubject());
            assertEquals("test-user", idTokenCaptor.getValue().getClaimAsString("preferred_username"));
            assertEquals("nonce-1", idTokenCaptor.getValue().getClaimAsString("nonce"));
            assertEquals("access-token", accessTokenCaptor.getValue().getTokenValue());
            assertEquals("refresh-token", refreshTokenCaptor.getValue().getTokenValue());
            assertTrue(accessTokenCaptor.getValue().getExpiresAt().isAfter(Instant.now()));

            verify(auditService).logAuthenticationSuccess(eq("user-123"), eq("session-1"), eq(request));
        }

        @Test
        @DisplayName("Should reject callback when state is missing")
        void handleCallback_shouldRejectWhenStateMissing() {
            Map<String, String> result = authService.handleCallback(request, response, "code", null, null);

            assertEquals("Invalid state parameter", result.get("error"));
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should reject callback when state does not exist")
        void handleCallback_shouldRejectWhenStateNotFound() {
            when(sessionService.getAuthRequestData("unknown-state")).thenReturn(null);

            Map<String, String> result = authService.handleCallback(request, response, "code", "unknown-state", null);

            assertEquals("Invalid state parameter", result.get("error"));
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should reject callback when code_verifier is missing")
        void handleCallback_shouldRejectWhenCodeVerifierMissing() {
            AuthRequestData data = AuthRequestData.builder()
                    .redirectUri("/dashboard")
                    .nonce("nonce-1")
                    .createdAt(Instant.now())
                    .build();
            when(sessionService.getAuthRequestData("state-1")).thenReturn(data);

            Map<String, String> result = authService.handleCallback(request, response, "code", "state-1", null);

            assertEquals("Missing PKCE code verifier", result.get("error"));
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should reject callback when nonce is missing")
        void handleCallback_shouldRejectWhenNonceMissing() {
            AuthRequestData data = AuthRequestData.builder()
                    .redirectUri("/dashboard")
                    .codeVerifier(authService.generateCodeVerifier())
                    .createdAt(Instant.now())
                    .build();
            when(sessionService.getAuthRequestData("state-1")).thenReturn(data);

            Map<String, String> result = authService.handleCallback(request, response, "code", "state-1", null);

            assertEquals("Missing nonce", result.get("error"));
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should reject callback when code is missing")
        void handleCallback_shouldRejectWhenCodeMissing() {
            when(sessionService.getAuthRequestData("state-1")).thenReturn(validAuthRequestData());

            Map<String, String> result = authService.handleCallback(request, response, "", "state-1", null);

            assertEquals("Missing authorization code", result.get("error"));
            verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("Should handle non-2xx token endpoint response")
        void handleCallback_shouldHandleNon2xxTokenResponse() {
            when(sessionService.getAuthRequestData("state-1")).thenReturn(validAuthRequestData());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "bad_gateway")));

            Map<String, String> result = authService.handleCallback(request, response, "code", "state-1", null);

            assertEquals("Token exchange failed", result.get("error"));
            verify(auditService).logAuthenticationFailure(eq("unknown"), eq("Token exchange failed"), eq(request));
        }

        @Test
        @DisplayName("Should handle token endpoint exception")
        void handleCallback_shouldHandleTokenEndpointException() {
            when(sessionService.getAuthRequestData("state-1")).thenReturn(validAuthRequestData());
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RuntimeException("connection refused"));

            Map<String, String> result = authService.handleCallback(request, response, "code", "state-1", null);

            assertEquals("Authentication failed", result.get("error"));
            verify(auditService).logAuthenticationFailure(eq("unknown"), contains("Authentication exception"), eq(request));
        }

        @Test
        @DisplayName("Should reject callback on empty token payload")
        void handleCallback_shouldRejectOnBrokenTokenPayload() {
            when(sessionService.getAuthRequestData("state-1")).thenReturn(validAuthRequestData());

            Map<String, Object> brokenPayload = new HashMap<>();
            brokenPayload.put("id_token", "id-token");
            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(brokenPayload));

            Map<String, String> result = authService.handleCallback(request, response, "code", "state-1", null);

            assertEquals("Invalid token response", result.get("error"));
            verify(auditService).logAuthenticationFailure(eq("unknown"), eq("Invalid token response"), eq(request));
        }
    }

    private ClientRegistration buildClientRegistration() {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("bionicpro-auth")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8000/api/auth/callback")
                .scope("openid", "profile", "email")
                .authorizationUri("http://localhost:8088/realms/reports-realm/protocol/openid-connect/auth")
                .tokenUri("http://localhost:8080/realms/reports-realm/protocol/openid-connect/token")
                .userInfoUri("http://localhost:8080/realms/reports-realm/protocol/openid-connect/userinfo")
                .jwkSetUri("http://localhost:8080/realms/reports-realm/protocol/openid-connect/certs")
                .userNameAttributeName("sub")
                .build();
    }

    private Map<String, Object> buildTokenResponse() {
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "access-token");
        tokenResponse.put("refresh_token", "refresh-token");
        tokenResponse.put("id_token", "id-token");
        tokenResponse.put("sub", "user-123");
        tokenResponse.put("preferred_username", "test-user");
        tokenResponse.put("expires_in", 600);
        tokenResponse.put("refresh_expires_in", 1800);
        return tokenResponse;
    }
}
