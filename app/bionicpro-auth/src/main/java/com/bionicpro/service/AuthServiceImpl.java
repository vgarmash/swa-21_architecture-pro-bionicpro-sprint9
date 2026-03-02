package com.bionicpro.service;

import com.bionicpro.audit.AuditService;
import com.bionicpro.model.AuthRequestData;
import com.bionicpro.model.SessionData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Сервис для управления аутентификацией.
 * Обрабатывает OAuth2 потоки, обмен токенами и аутентификацию пользователя.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final RestTemplate restTemplate;

    @Value("${keycloak.server-url:http://keycloak:8080}")
    private String keycloakUrl;

    @Value("${keycloak.realm:reports-realm}")
    private String keycloakRealm;

    @Value("${keycloak.client-id:bionicpro-auth}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    @Value("${auth.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Value("${auth.session.cookie-name:BIONICPRO_SESSION}")
    private String cookieName;

    @Value("${auth.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void initiateAuthentication(HttpServletRequest request, HttpServletResponse response, String redirectUri) {
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");

        String state = UUID.randomUUID().toString();
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String nonce = UUID.randomUUID().toString();

        String safeRedirectUri = (redirectUri == null || redirectUri.isBlank() || "/".equals(redirectUri))
                ? frontendUrl
                : redirectUri;
        AuthRequestData authRequestData = AuthRequestData.builder()
                .redirectUri(safeRedirectUri)
                .codeVerifier(codeVerifier)
                .nonce(nonce)
                .createdAt(Instant.now())
                .build();
        sessionService.storeAuthRequest(state, authRequestData);

        String scope = String.join(" ", clientRegistration.getScopes());
        String authorizationUri = UriComponentsBuilder
                .fromUriString(clientRegistration.getProviderDetails().getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", clientRegistration.getClientId())
                .queryParam("scope", scope)
                .queryParam("redirect_uri", clientRegistration.getRedirectUri())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("nonce", nonce)
                .build()
                .encode()
                .toUriString();

        try {
            response.sendRedirect(authorizationUri);
        } catch (Exception e) {
            log.error("Failed to redirect to Keycloak", e);
            throw new RuntimeException("Failed to initiate authentication", e);
        }
    }

    @Override
    public Map<String, String> handleCallback(HttpServletRequest request, HttpServletResponse response,
                                              String code, String state, String sessionState) {
        Map<String, String> result = new HashMap<>();

        try {
            if (isBlank(state)) {
                result.put("error", "Invalid state parameter");
                return result;
            }

            AuthRequestData authRequestData = sessionService.getAuthRequestData(state);
            if (authRequestData == null || isBlank(authRequestData.getRedirectUri())) {
                log.warn("Invalid state parameter in callback");
                result.put("error", "Invalid state parameter");
                return result;
            }

            String storedRedirectUri = authRequestData.getRedirectUri();
            String codeVerifier = authRequestData.getCodeVerifier();
            String nonce = authRequestData.getNonce();

            if (isBlank(codeVerifier)) {
                result.put("error", "Missing PKCE code verifier");
                return result;
            }
            if (isBlank(nonce)) {
                result.put("error", "Missing nonce");
                return result;
            }
            if (isBlank(code)) {
                result.put("error", "Missing authorization code");
                return result;
            }

            ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, keycloakRealm);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("code", code);
            params.add("redirect_uri", clientRegistration.getRedirectUri());
            params.add("code_verifier", codeVerifier);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUrl, httpEntity, Map.class);

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                log.error("Failed to exchange authorization code for tokens");
                auditService.logAuthenticationFailure("unknown", "Token exchange failed", request);
                result.put("error", "Token exchange failed");
                return result;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenMap = (Map<String, Object>) tokenResponse.getBody();
            String accessTokenValue = getStringValue(tokenMap, "access_token");
            String refreshTokenValue = getStringValue(tokenMap, "refresh_token");
            String idTokenValue = getStringValue(tokenMap, "id_token");

            if (isBlank(accessTokenValue) || isBlank(idTokenValue)) {
                auditService.logAuthenticationFailure("unknown", "Invalid token response", request);
                result.put("error", "Invalid token response");
                return result;
            }

            long expiresIn = getLongValue(tokenMap, "expires_in", 300);
            long refreshExpiresIn = getLongValue(tokenMap, "refresh_expires_in", 1800);
            Instant now = Instant.now();

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    accessTokenValue,
                    now,
                    now.plusSeconds(expiresIn)
            );
            OAuth2RefreshToken refreshToken = refreshTokenValue != null
                    ? new OAuth2RefreshToken(refreshTokenValue, now, now.plusSeconds(refreshExpiresIn))
                    : null;

            // Извлекаем subject и preferred_username из access token (JWT)
            String subject = extractClaimFromJwt(accessTokenValue, "sub");
            if (isBlank(subject)) {
                subject = "unknown";
            }
            String preferredUsername = extractClaimFromJwt(accessTokenValue, "preferred_username");

            OidcIdToken idToken = OidcIdToken.withTokenValue(idTokenValue)
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(expiresIn))
                    .subject(subject)
                    .claim("preferred_username", preferredUsername)
                    .claim("nonce", nonce)
                    .build();

            sessionService.createSession(request, response, idToken, accessToken, refreshToken);

            String sessionId = sessionService.getSessionIdFromRequest(request);
            auditService.logAuthenticationSuccess(idToken.getSubject(), sessionId, request);

            result.put("redirect", storedRedirectUri);
        } catch (Exception e) {
            log.error("Error handling callback", e);
            auditService.logAuthenticationFailure("unknown", "Authentication exception: " + e.getMessage(), request);
            result.put("error", "Authentication failed");
        }

        return result;
    }

    @Override
    public Map<String, Object> getAuthStatus(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String sessionId = sessionService.getSessionIdFromRequest(request);

            if (sessionId == null) {
                result.put("authenticated", false);
                result.put("error", "No session found");
                return result;
            }

            SessionData sessionData = sessionService.validateAndRefreshSession(sessionId);

            if (sessionData == null) {
                result.put("authenticated", false);
                result.put("error", "Session expired or invalid");
                return result;
            }

            result.put("authenticated", true);
            result.put("userId", sessionData.getUserId());
            result.put("username", sessionData.getUsername());
            result.put("roles", sessionData.getRoles());
            result.put("sessionExpiresAt", sessionData.getExpiresAt());

        } catch (Exception e) {
            log.error("Error getting auth status", e);
            result.put("authenticated", false);
            result.put("error", "Error checking authentication status");
        }

        return result;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String sessionId = sessionService.getSessionIdFromRequest(request);
            String userId = null;
            if (sessionId != null) {
                SessionData sessionData = sessionService.getSession(sessionId);
                if (sessionData != null) {
                    userId = sessionData.getUserId();
                }
            }

            sessionService.invalidateSessionWithTokenRevocation(request, response);

            if (userId != null && sessionId != null) {
                auditService.logLogout(userId, sessionId, request);
            }
        } catch (Exception e) {
            log.error("Error during logout", e);
        }
    }

    @Override
    public void refreshSession(HttpServletRequest request, HttpServletResponse response) {
        try {
            String sessionId = sessionService.getSessionIdFromRequest(request);

            if (sessionId != null) {
                sessionService.rotateSession(request, response);
            }
        } catch (Exception e) {
            log.error("Error refreshing session", e);
        }
    }

    @Override
    public Object validateAndRefreshSession(HttpServletRequest request) {
        try {
            String sessionId = sessionService.getSessionIdFromRequest(request);
            return sessionId != null ? sessionService.validateAndRefreshSession(sessionId) : null;
        } catch (Exception e) {
            log.error("Error validating and refreshing session", e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getUserDetails(OidcIdToken idToken) {
        Map<String, Object> userDetails = new HashMap<>();

        if (idToken != null) {
            userDetails.put("userId", idToken.getSubject());
            userDetails.put("username", idToken.getClaimAsString("preferred_username"));
            userDetails.put("email", idToken.getClaimAsString("email"));
            userDetails.put("firstName", idToken.getClaimAsString("given_name"));
            userDetails.put("lastName", idToken.getClaimAsString("family_name"));
            userDetails.put("roles", idToken.getClaimAsStringList("roles"));
        }

        return userDetails;
    }

    String generateCodeVerifier() {
        byte[] verifierBytes = new byte[32];
        SECURE_RANDOM.nextBytes(verifierBytes);
        return BASE64_URL_ENCODER.encodeToString(verifierBytes);
    }

    String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL_ENCODER.encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PKCE code challenge", e);
        }
    }

    private String getStringValue(Map<String, Object> tokenMap, String key) {
        Object value = tokenMap.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private long getLongValue(Map<String, Object> tokenMap, String key, long defaultValue) {
        Object value = tokenMap.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Извлекает claim из JWT токена.
     * JWT состоит из трёх частей: header.payload.signature
     * Payload кодируется Base64URL.
     */
    private String extractClaimFromJwt(String jwt, String claimName) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String payload = parts[1];
            // Добавляем padding для Base64
            String paddedPayload = payload + "=".repeat((4 - payload.length() % 4) % 4);
            byte[] decoded = Base64.getUrlDecoder().decode(paddedPayload);
            String json = new String(decoded, StandardCharsets.UTF_8);
            
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> claims = mapper.readValue(json, Map.class);
            return (String) claims.get(claimName);
        } catch (Exception e) {
            log.warn("Failed to extract claim '{}' from JWT", claimName, e);
            return null;
        }
    }
}
