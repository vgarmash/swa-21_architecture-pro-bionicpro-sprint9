package com.bionicpro.controller;

import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
            response.sendRedirect("/login?error=" + error);
            return;
        }
        
        log.info("Processing OAuth2 callback with state: {}", state);
        
        // Get redirect URI from stored auth request
        String redirectUri = sessionService.getAuthRequest(state);
        
        // Store tokens in session
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OidcIdToken) {
            OidcIdToken idToken = (OidcIdToken) authentication.getPrincipal();
            OAuth2AccessToken accessToken = (OAuth2AccessToken) authentication.getTokenCredentials().getAccessToken();
            OAuth2RefreshToken refreshToken = (OAuth2RefreshToken) authentication.getTokens()
                    .stream()
                    .filter(t -> t instanceof OAuth2RefreshToken)
                    .findFirst()
                    .orElse(null);
            
            // Create session data
            sessionService.createSession(request, response, idToken, accessToken, refreshToken);
            
            log.info("Session created for user: {}", idToken.getSubject());
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
                    .body(AuthStatusResponse.builder()
                            .authenticated(false)
                            .build());
        }
        
        String userId = authentication.getName();
        List<String> roles = List.of();
        
        // Extract roles from authentication
        if (authentication.getAuthorities() != null) {
            roles = authentication.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .toList();
        }
        
        // Get session expiration
        Instant expiresAt = sessionService.getSessionExpiration(request);
        
        AuthStatusResponse response = AuthStatusResponse.builder()
                .authenticated(true)
                .userId(userId)
                .username(userId)
                .roles(roles)
                .sessionExpiresAt(expiresAt != null ? expiresAt.toString() : null)
                .build();
        
        return ResponseEntity.ok(response);
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
        
        // Invalidate session
        sessionService.invalidateSession(request, response);
        
        // Logout from Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
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
