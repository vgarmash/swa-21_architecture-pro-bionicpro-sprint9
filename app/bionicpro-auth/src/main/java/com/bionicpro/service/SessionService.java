package com.bionicpro.service;

import com.bionicpro.model.SessionData;
import com.bionicpro.model.TokenData;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for session management with Redis storage.
 * Handles session creation, validation, rotation, and token storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final BytesEncryptor bytesEncryptor;

    @Value("${auth.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Value("${auth.session.cookie-name:BIONICPRO_SESSION}")
    private String cookieName;

    // In-memory store for auth request state (temporary, until callback)
    private final Map<String, String> authRequestStore = new ConcurrentHashMap<>();

    /**
     * Store auth request parameters before redirect to Keycloak.
     */
    public void storeAuthRequest(String state, String redirectUri) {
        authRequestStore.put(state, redirectUri);
        log.debug("Stored auth request for state: {}", state);
    }

    /**
     * Get and remove stored auth request.
     */
    public String getAuthRequest(String state) {
        String redirectUri = authRequestStore.remove(state);
        log.debug("Retrieved auth request for state: {}, redirectUri: {}", state, redirectUri);
        return redirectUri;
    }

    /**
     * Create new session with tokens.
     */
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
    public SessionData getSession(String sessionId) {
        String redisKey = getSessionKey(sessionId);
        Object value = redisTemplate.opsForValue().get(redisKey);
        
        if (value instanceof SessionData) {
            return (SessionData) value;
        }
        
        return null;
    }

    /**
     * Validate session and refresh tokens if needed.
     */
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
            // Token needs refresh - in production, would call Keycloak to refresh
            log.debug("Access token needs refresh for user: {}", sessionData.getUserId());
            // TODO: Implement token refresh with Keycloak
        }
        
        // Update last accessed time
        sessionData.setLastAccessedAt(Instant.now());
        updateSession(sessionId, sessionData);
        
        return sessionData;
    }

    /**
     * Rotate session - generate new session ID, invalidate old one.
     */
    public void rotateSession(HttpServletRequest request, HttpServletResponse response) {
        String oldSessionId = getSessionIdFromRequest(request);
        
        if (oldSessionId == null) {
            log.debug("No session to rotate");
            return;
        }
        
        SessionData oldSession = getSession(oldSessionId);
        
        if (oldSession == null) {
            log.debug("Session not found for rotation");
            return;
        }
        
        // Create new session ID
        String newSessionId = UUID.randomUUID().toString();
        
        // Update session data with new ID
        oldSession.setSessionId(newSessionId);
        oldSession.setLastAccessedAt(Instant.now());
        
        // Store new session
        String newRedisKey = getSessionKey(newSessionId);
        redisTemplate.opsForValue().set(newRedisKey, oldSession, Duration.ofMinutes(sessionTimeoutMinutes));
        
        // Invalidate old session
        invalidateSessionById(oldSessionId);
        
        // Set new cookie
        setSessionCookie(response, newSessionId);
        
        log.info("Rotated session for user: {} from {} to {}", oldSession.getUserId(), oldSessionId, newSessionId);
    }

    /**
     * Invalidate session from request.
     */
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
    public void invalidateSessionById(String sessionId) {
        String redisKey = getSessionKey(sessionId);
        redisTemplate.delete(redisKey);
        log.debug("Deleted session: {}", sessionId);
    }

    /**
     * Get session expiration time.
     */
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
    public String getAccessToken(String sessionId) {
        SessionData sessionData = getSession(sessionId);
        
        if (sessionData != null && sessionData.getAccessToken() != null) {
            return decryptToken(sessionData.getAccessToken());
        }
        
        return null;
    }

    /**
     * Update session in Redis.
     */
    private void updateSession(String sessionId, SessionData sessionData) {
        String redisKey = getSessionKey(sessionId);
        redisTemplate.opsForValue().set(redisKey, sessionData, Duration.ofMinutes(sessionTimeoutMinutes));
    }

    /**
     * Get session ID from request cookie.
     */
    private String getSessionIdFromRequest(HttpServletRequest request) {
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
     */
    private void setSessionCookie(HttpServletResponse response, String sessionId) {
        Cookie cookie = new Cookie(cookieName, sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(sessionTimeoutMinutes * 60);
        cookie.setAttribute("SameSite", "Lax");
        
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
}
