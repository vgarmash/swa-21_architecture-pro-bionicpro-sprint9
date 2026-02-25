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
 * Контроллер аутентификации для BFF эндпоинтов.
 * Обрабатывает операции входа, обратного вызова, статуса, выхода и обновления.
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
     * Инициирует аутентификацию - перенаправляет на страницу входа Keycloak.
     * GET /api/auth/login
     */
    @GetMapping("/login")
    public void login(
            @RequestParam(required = false, defaultValue = "/") String redirectUri,
            HttpServletResponse response) throws Exception {
        
        log.info("Initiating login with redirectUri: {}", redirectUri);
        
        // Генерируем параметр state для защиты от CSRF
        String state = UUID.randomUUID().toString();
        
        // Сохраняем redirect URI в сессию для использования после callback
        sessionService.storeAuthRequest(state, redirectUri);
        
        // Spring Security OAuth2 Login обработает перенаправление
        // redirect_uri не передаём явно - он настроен в OAuth2ClientConfig
        response.sendRedirect("/oauth2/authorization/keycloak?state=" + state);
    }

    /**
     * Обрабатывает OAuth2 callback от Keycloak.
     * GET /api/auth/callback
     */
    @GetMapping("/callback")
    public void callback(
            @RequestParam(OAuth2ParameterNames.CODE) String code,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        
        log.info("=== OAuth2 Callback START ===");
        log.info("Callback params - code: {}, state: {}, error: {}", 
            code != null ? "[PRESENT]" : "[NULL]", state, error);
        
        // DEBUG: Check what's in SecurityContext BEFORE any processing
        Authentication debugAuth = SecurityContextHolder.getContext().getAuthentication();
        log.info("SecurityContext authentication BEFORE processing: {}", debugAuth);
        log.info("SecurityContext authentication isAuthenticated: {}", 
            debugAuth != null ? debugAuth.isAuthenticated() : "N/A");
        log.info("SecurityContext authentication class: {}", 
            debugAuth != null ? debugAuth.getClass().getName() : "N/A");
        
        if (error != null) {
            log.error("OAuth2 callback error: {}", error);
            // Логирование аудита для неуспешной аутентификации
            auditService.logAuthenticationFailure("unknown", error, request);
            response.sendRedirect("/login?error=" + error);
            return;
        }
        
        log.info("Getting redirectUri from sessionService for state: {}", state);
        // Получаем redirect URI из сохранённого запроса аутентификации
        String redirectUri = sessionService.getAuthRequest(state);
        log.info("Retrieved redirectUri: {}", redirectUri);
        
        // DEBUG: Check authentication AGAIN after getting redirectUri
        Authentication authAfter = SecurityContextHolder.getContext().getAuthentication();
        log.info("SecurityContext authentication AFTER getAuthRequest: {}", authAfter);
        log.info("SecurityContext authentication isAuthenticated: {}", 
            authAfter != null ? authAfter.isAuthenticated() : "N/A");
        
        // Сохраняем токены в сессию
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("=== Authentication check: {} ===", authentication);
        if (authentication != null && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Auth = (OAuth2AuthenticationToken) authentication;
            
            // Получаем токены из атрибутов OAuth2User
            Map<String, Object> attributes = oauth2Auth.getPrincipal().getAttributes();
            
            // Извлекаем ID токен
            OidcIdToken idToken = null;
            Object idTokenObj = attributes.get("id_token");
            if (idTokenObj instanceof OidcIdToken) {
                idToken = (OidcIdToken) idTokenObj;
            } else if (attributes.containsKey("id_token")) {
                // Если id_token является строкой, нам нужно его восстановить
                // На данный момент создаём базовый OidcIdToken из доступных данных
                String tokenValue = attributes.get("id_token").toString();
                idToken = new OidcIdToken(
                    tokenValue,
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    attributes
                );
            }
            
            // Извлекаем access токен из атрибутов
            OAuth2AccessToken accessToken = null;
            Object accessTokenObj = attributes.get("access_token");
            if (accessTokenObj instanceof OAuth2AccessToken) {
                accessToken = (OAuth2AccessToken) accessTokenObj;
            } else if (attributes.containsKey("access_token")) {
                // Создаём OAuth2AccessToken из строкового значения
                String tokenValue = attributes.get("access_token").toString();
                accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    tokenValue,
                    Instant.now(),
                    Instant.now().plusSeconds(3600)
                );
            }
            
            // Извлекаем refresh токен из атрибутов
            OAuth2RefreshToken refreshToken = null;
            Object refreshTokenObj = attributes.get("refresh_token");
            if (refreshTokenObj instanceof OAuth2RefreshToken) {
                refreshToken = (OAuth2RefreshToken) refreshTokenObj;
            } else if (attributes.containsKey("refresh_token")) {
                String tokenValue = attributes.get("refresh_token").toString();
                refreshToken = new OAuth2RefreshToken(tokenValue, Instant.now(), Instant.now().plusSeconds(86400));
            }
            
            // Создаём данные сессии
            if (idToken != null && accessToken != null) {
                sessionService.createSession(request, response, idToken, accessToken, refreshToken);
                log.info("Session created for user: {}", idToken.getSubject());
                
                // Логирование аудита для успешной аутентификации
                String sessionId = sessionService.getSessionIdFromRequest(request);
                auditService.logAuthenticationSuccess(idToken.getSubject(), sessionId, request);
            }
        }
        
        // Перенаправляем на исходную запрошенную страницу или по умолчанию
        response.sendRedirect(redirectUri != null ? redirectUri : "/");
    }

    /**
     * Получить статус аутентификации.
     * GET /api/auth/status
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> getStatus(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        // Получаем ID сессии из запроса
        String sessionId = sessionService.getSessionIdFromRequest(request);
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(sessionDataMapper.toUnauthenticatedResponse());
        }
        
        // Получаем данные сессии
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
        
        // Получаем информацию о сессии до её аннулирования для логирования аудита
        String sessionId = sessionService.getSessionIdFromRequest(request);
        String userId = null;
        if (sessionId != null) {
            com.bionicpro.model.SessionData sessionData = sessionService.getSession(sessionId);
            if (sessionData != null) {
                userId = sessionData.getUserId();
            }
        }
        
        // Аннулируем сессию и отзываем токены в Keycloak
        sessionService.invalidateSessionWithTokenRevocation(request, response);
        
        // Выход из Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        
        // Логирование аудита для выхода
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "not_authenticated"));
        }
        
        // Ротация сессии
        sessionService.rotateSession(request, response);
        
        Map<String, String> result = new HashMap<>();
        result.put("message", "Session refreshed");
        
        return ResponseEntity.ok(result);
    }
}
