package com.bionicpro.controller;

import com.bionicpro.audit.AuditService;
import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.mapper.SessionDataMapper;
import com.bionicpro.model.SessionData;
import com.bionicpro.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication controller for BFF endpoints.
 * Handles login, callback, status, logout, and refresh operations.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SessionService sessionService;
    private final AuditService auditService;
    private final SessionDataMapper sessionDataMapper;

    /**
     * Initiate authentication - redirect to Keycloak login page.
     * GET /api/auth/login
     */
    @GetMapping("/login")
    public void login(
            @RequestParam(required = false, defaultValue = "/") String redirectUri,
            HttpServletResponse response) throws Exception {
        
        log.info("Initiating login with redirectUri: {}", redirectUri);
        
        // Generate state parameter for CSRF protection
        String state = UUID.randomUUID().toString();
        
        // Store redirect URI in session for use after callback
        sessionService.storeAuthRequest(state, redirectUri);
        
        // Spring Security OAuth2 Login will handle the redirect
        response.sendRedirect("/oauth2/authorization/keycloak?state=" + state + 
                "&redirect_uri=" + redirectUri);
    }

    /**
     * Handle OAuth2 callback from Keycloak.
     * GET /api/auth/callback
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam(OAuth2ParameterNames.CODE) String code,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        
        if (error != null) {
            log.error("OAuth2 callback error: {}", error);
            // Audit logging for failed authentication
            auditService.logAuthenticationFailure("unknown", error, request);
            response.sendRedirect("/login?error=" + error);
            return;
        }
        
        log.info("Processing OAuth2 callback with state: {}", state);
        
        // Get redirect URI from stored auth request
        String redirectUri = sessionService.getAuthRequest(state);
        
        // Store tokens in session
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Auth = (OAuth2AuthenticationToken) authentication;
            
            // Get tokens from OAuth2User attributes
            Map<String, Object> attributes = oauth2Auth.getPrincipal().getAttributes();
            
            // Extract ID token
            OidcIdToken idToken = null;
            Object idTokenObj = attributes.get("id_token");
            if (idTokenObj instanceof OidcIdToken) {
                idToken = (OidcIdToken) idTokenObj;
            } else if (attributes.containsKey("id_token")) {
                // If id_token is a string, we need to reconstruct it
                // For now, create a basic OidcIdToken from available data
                String tokenValue = attributes.get("id_token").toString();
                idToken = new OidcIdToken(
                    tokenValue,
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    attributes
                );
            }
            
            // Extract access token from attributes
            OAuth2AccessToken accessToken = null;
            Object accessTokenObj = attributes.get("access_token");
            if (accessTokenObj instanceof OAuth2AccessToken) {
                accessToken = (OAuth2AccessToken) accessTokenObj;
            } else if (attributes.containsKey("access_token")) {
                // Create OAuth2AccessToken from string value
                String tokenValue = attributes.get("access_token").toString();
                accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    tokenValue,
                    Instant.now(),
                    Instant.now().plusSeconds(3600)
                );
            }
            
            // Extract refresh token from attributes
            OAuth2RefreshToken refreshToken = null;
            Object refreshTokenObj = attributes.get("refresh_token");
            if (refreshTokenObj instanceof OAuth2RefreshToken) {
                refreshToken = (OAuth2RefreshToken) refreshTokenObj;
            } else if (attributes.containsKey("refresh_token")) {
                String tokenValue = attributes.get("refresh_token").toString();
                refreshToken = new OAuth2RefreshToken(tokenValue, Instant.now(), Instant.now().plusSeconds(86400));
            }
            
            // Create session data
            if (idToken != null && accessToken != null) {
                sessionService.createSession(request, response, idToken, accessToken, refreshToken);
                log.info("Session created for user: {}", idToken.getSubject());
                
                // Audit logging for successful authentication
                String sessionId = sessionService.getSessionIdFromRequest(request);
                auditService.logAuthenticationSuccess(idToken.getSubject(), sessionId, request);
            }
        }
        
        // Redirect to the original requested page or default
        response.sendRedirect(redirectUri != null ? redirectUri : "/");
    }

    /**
     * Get authentication status.
     * GET /api/auth/status
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> getStatus(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        // Get session ID from request
        String sessionId = sessionService.getSessionIdFromRequest(request);
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        // Get session data
        SessionData sessionData = sessionService.getSession(sessionId);
        if (sessionData == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        return ResponseEntity.ok(sessionDataMapper.toAuthStatusResponse(sessionData));
    }

    /**
     * Logout user.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("Processing logout request");
        
        // Get session info before invalidation for audit logging
        String sessionId = sessionService.getSessionIdFromRequest(request);
        String userId = null;
        if (sessionId != null) {
            com.bionicpro.model.SessionData sessionData = sessionService.getSession(sessionId);
            if (sessionData != null) {
                userId = sessionData.getUserId();
            }
        }
        
        // Invalidate session and revoke tokens in Keycloak
        sessionService.invalidateSessionWithTokenRevocation(request, response);
        
        // Logout from Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        
        // Audit logging for logout
        if (userId != null && sessionId != null) {
            auditService.logLogout(userId, sessionId, request);
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("message", "Logged out successfully");
        
        return ResponseEntity.ok(result);
    }

    /**
     * Refresh session (trigger session rotation).
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "not_authenticated"));
        }
        
        // Rotate session
        sessionService.rotateSession(request, response);
        
        Map<String, String> result = new HashMap<>();
        result.put("message", "Session refreshed");
        
        return ResponseEntity.ok(result);
    }
}
