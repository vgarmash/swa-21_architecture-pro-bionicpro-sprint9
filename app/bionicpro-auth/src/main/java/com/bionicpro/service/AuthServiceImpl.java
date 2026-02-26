package com.bionicpro.service;

import com.bionicpro.audit.AuditService;
import com.bionicpro.model.SessionData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для управления аутентификацией.
 * Обрабатывает OAuth2 потоки, обмен токенами и аутентификацию пользователя.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${keycloak.server-url:http://keycloak:8080}")
    private String keycloakUrl;
    
    @Value("${keycloak.realm:reports-realm}")
    private String keycloakRealm;
    
    @Value("${keycloak.client-id:bionicpro-auth}")
    private String clientId;
    
    @Value("${auth.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;
    
    @Value("${auth.session.cookie-name:BIONICPRO_SESSION}")
    private String cookieName;
    
    @Override
    public void initiateAuthentication(HttpServletRequest request, HttpServletResponse response, String redirectUri) {
        // Получаем регистрацию клиента
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");
        
        // Генерируем параметр state для защиты от CSRF
        String state = UUID.randomUUID().toString();
        
        // Сохраняем redirect URI в сессию для дальнейшего использования
        sessionService.storeAuthRequest(state, redirectUri);
        
        // Формируем URI авторизации
        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri()
                .replace("{client_id}", clientRegistration.getClientId())
                .replace("{redirect_uri}", clientRegistration.getRedirectUri())
                .replace("{response_type}", "code")
                .replace("{scope}", "openid profile email")
                .replace("{state}", state)
                .replace("{code_challenge}", "S256") // PKCE
                .replace("{code_challenge_method}", "S256");
        
        // Перенаправляем на Keycloak
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
            // Валидируем параметр state для предотвращения CSRF атак
            String storedRedirectUri = sessionService.getAuthRequest(state);
            if (storedRedirectUri == null) {
                log.warn("Invalid state parameter in callback");
                result.put("error", "Invalid state parameter");
                return result;
            }
            
            // Получаем регистрацию клиента
            ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");
            
            // Обменять код авторизации на токены с помощью REST шаблона
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakUrl, keycloakRealm);
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", clientId);
            params.add("code", code);
            params.add("redirect_uri", clientRegistration.getRedirectUri());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);
            
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUrl, httpEntity, Map.class);
            
            if (tokenResponse.getStatusCode() != HttpStatus.OK || tokenResponse.getBody() == null) {
                log.error("Failed to exchange authorization code for tokens");
                // Логирование аудита для неуспешной аутентификации
                auditService.logAuthenticationFailure("unknown", "Token exchange failed", request);
                result.put("error", "Token exchange failed");
                return result;
            }
            
            Map<String, Object> tokenMap = tokenResponse.getBody();
            String accessTokenValue = (String) tokenMap.get("access_token");
            String refreshTokenValue = (String) tokenMap.get("refresh_token");
            String idTokenValue = (String) tokenMap.get("id_token");
            
            OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessTokenValue, Instant.now(), Instant.now().plusSeconds(300));
            OAuth2RefreshToken refreshToken = refreshTokenValue != null ? new OAuth2RefreshToken(refreshTokenValue, Instant.now(), Instant.now().plusSeconds(1800)) : null;
            
            // Парсим ID токен
            OidcIdToken idToken = OidcIdToken.withTokenValue(idTokenValue)
                    .subject((String) tokenMap.get("sub"))
                    .claim("preferred_username", tokenMap.get("preferred_username"))
                    .build();
            
            if (idToken == null) {
                log.warn("ID token not found in authorized client");
                // Логирование аудита для неуспешной аутентификации
                auditService.logAuthenticationFailure("unknown", "ID token not found", request);
                result.put("error", "ID token not found");
                return result;
            }
            
            // Создаём сессию
            sessionService.createSession(request, response, idToken, accessToken, refreshToken);
            
            // Логирование аудита для успешной аутентификации
            String sessionId = sessionService.getSessionIdFromRequest(request);
            auditService.logAuthenticationSuccess(idToken.getSubject(), sessionId, request);
            
            // Перенаправляем на сохранённый redirect URI
            result.put("redirect", storedRedirectUri);
            
        } catch (Exception e) {
            log.error("Error handling callback", e);
            // Audit logging for failed authentication
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
            // Получаем информацию о сессии до её аннулирования для логирования аудита
            String sessionId = sessionService.getSessionIdFromRequest(request);
            String userId = null;
            if (sessionId != null) {
                SessionData sessionData = sessionService.getSession(sessionId);
                if (sessionData != null) {
                    userId = sessionData.getUserId();
                }
            }
            
            sessionService.invalidateSessionWithTokenRevocation(request, response);
            
            // Логирование аудита для выхода
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
                // Ротация сессии для её обновления
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
}