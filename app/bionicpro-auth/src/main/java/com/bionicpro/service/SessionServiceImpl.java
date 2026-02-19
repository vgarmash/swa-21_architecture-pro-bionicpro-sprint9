package com.bionicpro.service;

import com.bionicpro.model.SessionData;
import com.bionicpro.model.TokenData;
import com.bionicpro.repository.SessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Service for session management with Redis storage.
 * Handles session creation, validation, rotation, and token storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final BytesEncryptor bytesEncryptor;

    @Value("${auth.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Value("${auth.session.cookie-name:BIONICPRO_SESSION}")
    private String cookieName;

    @Value("${keycloak.server-url:http://localhost:8080}")
    private String keycloakUrl;

    @Value("${keycloak.realm:reports-realm}")
    private String keycloakRealm;

    @Value("${keycloak.client-id:bionicpro-auth}")
    private String clientId;

    private static final String TOKEN_URL_FORMAT = "%s/realms/%s/protocol/openid-connect/token";

    // Redis template for auth request storage
    private final RedisTemplate<String, String> redisTemplateString;

    // Redis key prefix for auth requests
    private static final String AUTH_REQUEST_PREFIX = "auth:request:";
    // TTL for auth requests (10 minutes)
    private static final Duration AUTH_REQUEST_TTL = Duration.ofMinutes(10);

    /**
     * Store auth request parameters before redirect to Keycloak.
     */
    @Override
    public void storeAuthRequest(String state, String redirectUri) {
        // Для auth request storage используем Redis напрямую, так как это специфичная логика
        String key = AUTH_REQUEST_PREFIX + state;
        redisTemplateString.opsForValue().set(key, redirectUri, AUTH_REQUEST_TTL);
        log.debug("Stored auth request for state: {}", state);
    }

    /**
     * Get and remove stored auth request.
     */
    @Override
    public String getAuthRequest(String state) {
        // Для auth request storage используем Redis напрямую, так как это специфичная логика
        String key = AUTH_REQUEST_PREFIX + state;
        String redirectUri = redisTemplateString.opsForValue().getAndDelete(key);
        log.debug("Retrieved auth request for state: {}, redirectUri: {}", state, redirectUri);
        return redirectUri;
    }

    /**
     * Create new session with tokens.
     */
    @Override
    public void createSession(HttpServletRequest request, HttpServletResponse response,
                              OidcIdToken idToken, OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken) {
        
        String sessionId = UUID.randomUUID().toString();
        
        // Create session data
        SessionData sessionData = SessionData.builder()
                .sessionId(sessionId)
                .userId(idToken.getSubject())
                .username(idToken.getClaimAsString("preferred_username"))
                .roles(idToken.getClaimAsStringList("roles"))
                .accessToken(encryptToken(accessToken.getTokenValue()))
                .refreshToken(refreshToken != null ? encryptToken(refreshToken.getTokenValue()) : null)
                .accessTokenExpiresAt(accessToken.getExpiresAt())
                .refreshTokenExpiresAt(refreshToken != null ? refreshToken.getExpiresAt() : null)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(sessionTimeoutMinutes)))
                .lastAccessedAt(Instant.now())
                .build();
        
        // Store in Redis
        String redisKey = getSessionKey(sessionId);
        redisTemplate.opsForValue().set(redisKey, sessionData, Duration.ofMinutes(sessionTimeoutMinutes));
        
        // Set session cookie
        setSessionCookie(response, sessionId);
        
        log.info("Created session for user: {}", sessionData.getUserId());
    }

    /**
     * Get session data by session ID.
     */
    @Override
    public SessionData getSession(String sessionId) {
        return sessionRepository.findById(sessionId).orElse(null);
    }

    /**
     * Validate session and refresh tokens if needed.
     */
    @Override
    public SessionData validateAndRefreshSession(String sessionId) {
        SessionData sessionData = getSession(sessionId);
        
        if (sessionData == null) {
            return null;
        }
        
        // Check if session is expired
        if (sessionData.getExpiresAt() != null && Instant.now().isAfter(sessionData.getExpiresAt())) {
            log.info("Session expired for user: {}", sessionData.getUserId());
            invalidateSessionById(sessionId);
            return null;
        }
        
        // Check if access token needs refresh (expired or about to expire within 30 seconds)
        if (sessionData.getAccessTokenExpiresAt() != null
                && Instant.now().plusSeconds(30).isAfter(sessionData.getAccessTokenExpiresAt())) {
            // Token needs refresh - call Keycloak to refresh
            log.debug("Access token needs refresh for user: {}", sessionData.getUserId());
            
            if (sessionData.getRefreshToken() != null) {
                sessionData = refreshAccessToken(sessionData);
                
                if (sessionData == null) {
                    // Refresh failed - invalidate session
                    log.warn("Token refresh failed for user: {}", sessionData.getUserId());
                    invalidateSessionById(sessionId);
                    return null;
                }
            } else {
                log.warn("No refresh token available for user: {}", sessionData.getUserId());
                // Invalidate session if no refresh token available
                invalidateSessionById(sessionId);
                return null;
            }
        }
        
        // Update last accessed time
        sessionData.setLastAccessedAt(Instant.now());
        updateSession(sessionId, sessionData);
        
        return sessionData;
    }

    /**
     * Refresh access token using refresh token.
     * Calls Keycloak token endpoint with grant_type=refresh_token.
     */
    @Override
    public SessionData refreshAccessToken(SessionData sessionData) {
        try {
            String tokenUrl = String.format(TOKEN_URL_FORMAT, keycloakUrl, keycloakRealm);
            
            // Get decrypted refresh token
            String refreshToken = decryptToken(sessionData.getRefreshToken());
            
            // Prepare request parameters
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("client_id", clientId);
            params.add("refresh_token", refreshToken);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            // Make POST request to Keycloak
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                
                // Extract new tokens
                String newAccessToken = (String) tokenResponse.get("access_token");
                String newRefreshToken = (String) tokenResponse.get("refresh_token");
                Integer expiresIn = (Integer) tokenResponse.get("expires_in");
                
                // Update session data with new tokens
                sessionData.setAccessToken(encryptToken(newAccessToken));
                if (newRefreshToken != null) {
                    sessionData.setRefreshToken(encryptToken(newRefreshToken));
                }
                
                // Calculate new expiration times
                Instant now = Instant.now();
                sessionData.setAccessTokenExpiresAt(now.plusSeconds(expiresIn != null ? expiresIn : 300));
                
                // Update refresh token expiration if provided
                if (tokenResponse.get("refresh_expires_in") != null) {
                    Integer refreshExpiresIn = (Integer) tokenResponse.get("refresh_expires_in");
                    sessionData.setRefreshTokenExpiresAt(now.plusSeconds(refreshExpiresIn));
                }
                
                log.info("Successfully refreshed token for user: {}", sessionData.getUserId());
                return sessionData;
            } else {
                log.error("Unexpected response from Keycloak during token refresh: {}", response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("Failed to refresh access token for user: {} - Error: {}", 
                    sessionData.getUserId(), e.getMessage());
            return null;
        }
    }

    /**
     * Rotate session - generate new session ID, invalidate old one.
     * This method takes sessionId as parameter and returns new SessionData.
     * Should be called on every authenticated request.
     */
    @Override
    public SessionData rotateSession(String sessionId) {
        if (sessionId == null) {
            log.debug("No session to rotate");
            return null;
        }
        
        SessionData oldSession = getSession(sessionId);
        
        if (oldSession == null) {
            log.debug("Session not found for rotation");
            return null;
        }
        
        // Generate new session ID
        String newSessionId = UUID.randomUUID().toString();
        
        // Copy data to new session
        SessionData newSession = SessionData.builder()
                .sessionId(newSessionId)
                .userId(oldSession.getUserId())
                .username(oldSession.getUsername())
                .roles(oldSession.getRoles())
                .accessToken(oldSession.getAccessToken())
                .refreshToken(oldSession.getRefreshToken())
                .accessTokenExpiresAt(oldSession.getAccessTokenExpiresAt())
                .refreshTokenExpiresAt(oldSession.getRefreshTokenExpiresAt())
                .createdAt(oldSession.getCreatedAt())
                .expiresAt(oldSession.getExpiresAt())
                .lastAccessedAt(Instant.now())
                .build();
        
        // Store new session
        String newRedisKey = getSessionKey(newSessionId);
        redisTemplate.opsForValue().set(newRedisKey, newSession, Duration.ofMinutes(sessionTimeoutMinutes));
        
        // Invalidate old session
        invalidateSessionById(sessionId);
        
        log.info("Rotated session for user: {} from {} to {}", oldSession.getUserId(), sessionId, newSessionId);
        
        return newSession;
    }

    /**
     * Rotate session from request - generate new session ID, invalidate old one.
     * This method extracts sessionId from request and sets new cookie.
     */
    @Override
    public void rotateSession(HttpServletRequest request, HttpServletResponse response) {
        String oldSessionId = getSessionIdFromRequest(request);
        
        if (oldSessionId == null) {
            log.debug("No session to rotate");
            return;
        }
        
        SessionData newSession = rotateSession(oldSessionId);
        
        if (newSession != null) {
            // Set new cookie with new session ID
            setSessionCookie(response, newSession.getSessionId());
        }
    }

    /**
     * Revoke access and refresh tokens by calling Keycloak logout endpoint.
     * Should be called on user logout to invalidate tokens in Keycloak.
     */
    @Override
    public boolean revokeTokens(String refreshToken) {
        if (refreshToken == null) {
            log.debug("No refresh token to revoke");
            return true; // No token to revoke, consider it success
        }
        
        try {
            String tokenUrl = String.format(TOKEN_URL_FORMAT, keycloakUrl, keycloakRealm);
            
            // Prepare request parameters for logout endpoint
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("refresh_token", refreshToken);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            // Use POST to Keycloak logout endpoint
            restTemplate.postForEntity(tokenUrl + "/logout", request, String.class);
            
            log.info("Successfully revoked tokens in Keycloak");
            return true;
            
        } catch (Exception e) {
            log.warn("Failed to revoke tokens in Keycloak: {}", e.getMessage());
            // Return true anyway - local session is being invalidated anyway
            return true;
        }
    }
    
    /**
     * Invalidate session and revoke tokens.
     * Should be called on user logout to properly revoke tokens in Keycloak.
     */
    @Override
    public void invalidateSessionWithTokenRevocation(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = getSessionIdFromRequest(request);
        
        if (sessionId != null) {
            // Get session data to revoke tokens
            SessionData sessionData = getSession(sessionId);
            if (sessionData != null && sessionData.getRefreshToken() != null) {
                // Revoke tokens in Keycloak
                String refreshToken = decryptToken(sessionData.getRefreshToken());
                revokeTokens(refreshToken);
            }
            
            // Invalidate session
            invalidateSessionById(sessionId);
        }
        
        // Clear cookie
        clearSessionCookie(response);
        
        log.info("Session invalidated with token revocation");
    }
    
    /**
     * Invalidate session from request.
     */
    @Override
    public void invalidateSession(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = getSessionIdFromRequest(request);
        
        if (sessionId != null) {
            invalidateSessionById(sessionId);
        }
        
        // Clear cookie
        clearSessionCookie(response);
        
        log.info("Session invalidated");
    }

    /**
     * Invalidate session by ID.
     */
    @Override
    public void invalidateSessionById(String sessionId) {
        sessionRepository.deleteById(sessionId);
        log.debug("Deleted session: {}", sessionId);
    }

    /**
     * Get session expiration time.
     */
    @Override
    public Instant getSessionExpiration(HttpServletRequest request) {
        String sessionId = getSessionIdFromRequest(request);
        
        if (sessionId != null) {
            SessionData sessionData = getSession(sessionId);
            if (sessionData != null) {
                return sessionData.getExpiresAt();
            }
        }
        
        return null;
    }

    /**
     * Get decrypted access token for session.
     */
    @Override
    public String getAccessToken(String sessionId) {
        SessionData sessionData = getSession(sessionId);
        
        if (sessionData != null && sessionData.getAccessToken() != null) {
            return decryptToken(sessionData.getAccessToken());
        }
        
        return null;
    }

    /**
     * Get session ID from request cookie.
     */
    @Override
    public String getSessionIdFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }

    /**
     * Set session cookie with security attributes.
     * Public method to allow filters to set cookies.
     */
    @Override
    public void setSessionCookieFromFilter(HttpServletResponse response, String sessionId) {
        setSessionCookie(response, sessionId);
    }

    /**
     * Update session in repository.
     */
    private void updateSession(String sessionId, SessionData sessionData) {
        sessionRepository.save(sessionId, sessionData);
    }

    /**
     * Set session cookie with security attributes.
     */
    private void setSessionCookie(HttpServletResponse response, String sessionId) {
        Cookie cookie = new Cookie(cookieName, sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(sessionTimeoutMinutes * 60);
        cookie.setAttribute("SameSite", "Strict");
        
        response.addCookie(cookie);
        log.debug("Set session cookie: {}", sessionId);
    }

    /**
     * Clear session cookie.
     */
    private void clearSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        
        response.addCookie(cookie);
        log.debug("Cleared session cookie");
    }

    /**
     * Get Redis key for session.
     */
    private String getSessionKey(String sessionId) {
        return "bionicpro:session:" + sessionId;
    }

    /**
     * Encrypt token value.
     */
    private String encryptToken(String token) {
        byte[] encrypted = bytesEncryptor.encrypt(token.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypt token value.
     */
    private String decryptToken(String encryptedToken) {
        byte[] decoded = Base64.getDecoder().decode(encryptedToken);
        byte[] decrypted = bytesEncryptor.decrypt(decoded);
        return new String(decrypted);
    }
    
    private final RestTemplate restTemplate = new RestTemplate();
}