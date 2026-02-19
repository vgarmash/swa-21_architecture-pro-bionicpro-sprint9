package com.bionicpro.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.util.Map;

/**
 * Service for authentication management.
 * Handles OAuth2 flows, token exchange, and user authentication.
 */
public interface AuthService {
    
    /**
     * Initiate authentication flow by redirecting to Keycloak.
     * @param request HTTP request
     * @param response HTTP response
     * @param redirectUri Optional redirect URI
     */
    void initiateAuthentication(HttpServletRequest request, HttpServletResponse response, String redirectUri);
    
    /**
     * Handle OAuth2 callback from Keycloak.
     * @param request HTTP request
     * @param response HTTP response
     * @param code Authorization code from Keycloak
     * @param state State parameter for validation
     * @param sessionState Session state from Keycloak
     * @return Map containing redirect URL or error information
     */
    Map<String, String> handleCallback(HttpServletRequest request, HttpServletResponse response, 
                                       String code, String state, String sessionState);
    
    /**
     * Check authentication status.
     * @param request HTTP request
     * @return Authentication status information
     */
    Map<String, Object> getAuthStatus(HttpServletRequest request);
    
    /**
     * Logout user and invalidate session.
     * @param request HTTP request
     * @param response HTTP response
     */
    void logout(HttpServletRequest request, HttpServletResponse response);
    
    /**
     * Refresh session.
     * @param request HTTP request
     * @param response HTTP response
     */
    void refreshSession(HttpServletRequest request, HttpServletResponse response);
    
    /**
     * Validate and refresh session if needed.
     * @param request HTTP request
     * @return Session data if valid, null otherwise
     */
    Object validateAndRefreshSession(HttpServletRequest request);
    
    /**
     * Get user details from ID token.
     * @param idToken ID token
     * @return User details map
     */
    Map<String, Object> getUserDetails(OidcIdToken idToken);
}