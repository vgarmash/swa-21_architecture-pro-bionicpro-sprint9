package com.bionicpro.service;

import com.bionicpro.model.SessionData;
import com.bionicpro.model.TokenData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;

/**
 * Service for session management with Redis storage.
 * Handles session creation, validation, rotation, and token storage.
 */
public interface SessionService {
    
    /**
     * Store auth request parameters before redirect to Keycloak.
     */
    void storeAuthRequest(String state, String redirectUri);
    
    /**
     * Get and remove stored auth request.
     */
    String getAuthRequest(String state);
    
    /**
     * Create new session with tokens.
     */
    void createSession(HttpServletRequest request, HttpServletResponse response,
                       OidcIdToken idToken, OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken);
    
    /**
     * Get session data by session ID.
     */
    SessionData getSession(String sessionId);
    
    /**
     * Validate session and refresh tokens if needed.
     */
    SessionData validateAndRefreshSession(String sessionId);
    
    /**
     * Refresh access token using refresh token.
     * Calls Keycloak token endpoint with grant_type=refresh_token.
     */
    SessionData refreshAccessToken(SessionData sessionData);
    
    /**
     * Rotate session - generate new session ID, invalidate old one.
     * This method takes sessionId as parameter and returns new SessionData.
     * Should be called on every authenticated request.
     */
    SessionData rotateSession(String sessionId);
    
    /**
     * Rotate session from request - generate new session ID, invalidate old one.
     * This method extracts sessionId from request and sets new cookie.
     */
    void rotateSession(HttpServletRequest request, HttpServletResponse response);
    
    /**
     * Revoke access and refresh tokens by calling Keycloak logout endpoint.
     * Should be called on user logout to invalidate tokens in Keycloak.
     */
    boolean revokeTokens(String refreshToken);
    
    /**
     * Invalidate session and revoke tokens.
     * Should be called on user logout to properly revoke tokens in Keycloak.
     */
    void invalidateSessionWithTokenRevocation(HttpServletRequest request, HttpServletResponse response);
    
    /**
     * Invalidate session from request.
     */
    void invalidateSession(HttpServletRequest request, HttpServletResponse response);
    
    /**
     * Invalidate session by ID.
     */
    void invalidateSessionById(String sessionId);
    
    /**
     * Get session expiration time.
     */
    Instant getSessionExpiration(HttpServletRequest request);
    
    /**
     * Get decrypted access token for session.
     */
    String getAccessToken(String sessionId);
    
    /**
     * Get session ID from request cookie.
     */
    String getSessionIdFromRequest(HttpServletRequest request);
    
    /**
     * Set session cookie with security attributes.
     * Public method to allow filters to set cookies.
     */
    void setSessionCookieFromFilter(HttpServletResponse response, String sessionId);
}