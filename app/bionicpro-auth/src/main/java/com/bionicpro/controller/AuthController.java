package com.bionicpro.controller;

import com.bionicpro.audit.AuditService;
import com.bionicpro.dto.AuthStatusResponse;
import com.bionicpro.mapper.SessionDataMapper;
import com.bionicpro.model.SessionData;
import com.bionicpro.service.AuthService;
import com.bionicpro.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Контроллер аутентификации для BFF эндпоинтов.
 * Обрабатывает операции входа, обратного вызова, статуса, выхода и обновления.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final SessionDataMapper sessionDataMapper;

    /**
     * Инициирует OAuth2 PKCE аутентификацию.
     * GET /api/auth/login
     */
    @GetMapping("/login")
    public void login(
            @RequestParam(required = false, defaultValue = "/") String redirectUri,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        log.info("Initiating login with redirectUri: {}", redirectUri);
        authService.initiateAuthentication(request, response, redirectUri);
    }

    /**
     * Обрабатывает OAuth2 callback от Keycloak.
     * GET /api/auth/callback
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(value = "session_state", required = false) String sessionState,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        log.info("OAuth2 callback received - code: {}, state: {}, error: {}",
            code != null ? "[PRESENT]" : "[NULL]",
            state != null ? "[PRESENT]" : "[NULL]",
            error);

        if (error != null) {
            log.error("OAuth2 callback error: {}", error);
            auditService.logAuthenticationFailure("unknown", error, request);
            response.sendRedirect("/login?error=" + error);
            return;
        }

        Map<String, String> result = authService.handleCallback(request, response, code, state, sessionState);

        if (result.containsKey("error")) {
            log.error("Callback processing error: {}", result.get("error"));
            response.sendRedirect("/login?error=" + result.get("error"));
            return;
        }

        String redirectUrl = result.getOrDefault("redirect", "/");
        log.info("Callback successful, redirecting to: {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    /**
     * Получить статус аутентификации.
     * GET /api/auth/status
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> getStatus(HttpServletRequest request) {
        String sessionId = sessionService.getSessionIdFromRequest(request);
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }

        SessionData sessionData = sessionService.getSession(sessionId);
        if (sessionData == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }

        return ResponseEntity.ok(sessionDataMapper.toAuthStatusResponse(sessionData));
    }

    /**
     * Выход пользователя из системы.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("Processing logout request");

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

        Map<String, String> result = new HashMap<>();
        result.put("message", "Logged out successfully");

        return ResponseEntity.ok(result);
    }

    /**
     * Обновление сессии (запуск ротации сессии).
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = sessionService.getSessionIdFromRequest(request);
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "not_authenticated"));
        }

        sessionService.rotateSession(request, response);

        Map<String, String> result = new HashMap<>();
        result.put("message", "Session refreshed");

        return ResponseEntity.ok(result);
    }
}
