package com.bionicpro.service;

import com.bionicpro.model.SessionData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for authentication management.
 * Handles OAuth2 flows, token exchange, and user authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SessionService sessionService;
    
    @Value("${keycloak.server-url:http://localhost:8080}")
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
        // Get the client registration
        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");
        
        // Generate state parameter for CSRF protection
        String state = UUID.randomUUID().toString();
        
        // Store redirect URI in session for later use
        sessionService.storeAuthRequest(state, redirectUri);
        
        // Build the authorization URI
        String authorizationUri = clientRegistration.getProviderDetails().getAuthorizationUri()
                .replace("{client_id}", clientRegistration.getClientId())
                .replace("{redirect_uri}", clientRegistration.getRedirectUri())
                .replace("{response_type}", "code")
                .replace("{scope}", "openid profile email")
                .replace("{state}", state)
                .replace("{code_challenge}", "S256") // PKCE
                .replace("{code_challenge_method}", "S256");
        
        // Redirect to Keycloak
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
            // Validate state parameter to prevent CSRF attacks
            String storedRedirectUri = sessionService.getAuthRequest(state);
            if (storedRedirectUri == null) {
                log.warn("Invalid state parameter in callback");
                result.put("error", "Invalid state parameter");
                return result;
            }
            
            // Get the client registration
            ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");
            
            // Exchange authorization code for tokens
            OAuth2AuthorizedClient authorizedClient = authorizedClientService
                    .authorizeClient(clientRegistration.getRegistrationId(), code);
            
            // Get tokens
            OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
            OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
            OidcIdToken idToken = (OidcIdToken) authorizedClient.getPrincipal().getAuthorities().stream()
                    .filter(auth -> auth instanceof OidcUser)
                    .map(auth -> (OidcUser) auth)
                    .findFirst()
                    .map(OidcUser::getIdToken)
                    .orElse(null);
            
            if (idToken == null) {
                log.warn("ID token not found in authorized client");
                result.put("error", "ID token not found");
                return result;
            }
            
            // Create session
            sessionService.createSession(request, response, idToken, accessToken, refreshToken);
            
            // Redirect to stored redirect URI
            result.put("redirect", storedRedirectUri);
            
        } catch (Exception e) {
            log.error("Error handling callback", e);
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
            sessionService.invalidateSessionWithTokenRevocation(request, response);
        } catch (Exception e) {
            log.error("Error during logout", e);
        }
    }
    
    @Override
    public void refreshSession(HttpServletRequest request, HttpServletResponse response) {
        try {
            String sessionId = sessionService.getSessionIdFromRequest(request);
            
            if (sessionId != null) {
                // Rotate session to refresh it
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