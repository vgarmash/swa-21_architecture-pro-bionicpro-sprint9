/**
 * Контроллер для обработки аутентификационных запросов.
 * Обрабатывает вход пользователя, обмен кода на токены и выход.
 * Использует Spring Security OAuth2 Resource Server для валидации токенов.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2.controller;

import com.bionicpro.auth2.model.PKCEParams;
import com.bionicpro.auth2.model.TokenResponse;
import com.bionicpro.auth2.service.KeycloakService;
import com.bionicpro.auth2.service.TokenCacheService;
import com.bionicpro.auth2.util.PKCEUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;

/**
 * Контроллер для обработки аутентификационных запросов.
 * Реализует OAuth2 PKCE (Proof Key for Code Exchange) для безопасности.
 */
@Controller
public class AuthController {
    
    /**
     * URI для перенаправления после аутентификации
     */
    @Value("${bff.frontend.redirect-uri}")
    private String frontendRedirectUri;
    
    /**
     * Сервис для работы с Keycloak
     */
    private final KeycloakService keycloakService;
    
    /**
     * Сервис кэширования токенов в Redis
     */
    private final TokenCacheService tokenCacheService;
    
    /**
     * Время жизни access_token в секундах
     */
    @Value("${bff.token.access-token-lifetime}")
    private int accessTokenLifetime;
    
    /**
     * Время жизни refresh_token в секундах
     */
    @Value("${bff.token.refresh-token-lifetime}")
    private int refreshTokenLifetime;
    
    /**
     * Конструктор контроллера.
     *
     * @param keycloakService сервис для работы с Keycloak
     * @param tokenCacheService сервис кэширования токенов
     */
    public AuthController(KeycloakService keycloakService, TokenCacheService tokenCacheService) {
        this.keycloakService = keycloakService;
        this.tokenCacheService = tokenCacheService;
    }
    
    /**
     * Инициирует процесс входа пользователя.
     * Генерирует параметры PKCE и перенаправляет пользователя на страницу авторизации Keycloak.
     *
     * @param request HTTP запрос
     * @return ResponseEntity с RedirectView для перенаправления на авторизацию
     */
    @GetMapping("/auth/login")
    public ResponseEntity<RedirectView> initiateLogin(HttpServletRequest request) {
        PKCEParams pkceParams = new PKCEParams();
        pkceParams.setCodeVerifier(PKCEUtil.generateCodeVerifier());
        pkceParams.setCodeChallenge(PKCEUtil.generateCodeChallenge(pkceParams.getCodeVerifier()));
        
        // Сохраняем code verifier в сессии временно
        String sessionId = request.getSession().getId();
        request.getSession().setAttribute("code_verifier_" + sessionId, pkceParams.getCodeVerifier());
        
        URI authorizationUri = keycloakService.getAuthorizationUri(
            pkceParams,
            request.getRequestURL().toString().replace("/auth/login", "/auth/callback")
        );
        
        RedirectView redirectView = new RedirectView(authorizationUri.toString());
        return ResponseEntity.status(HttpStatus.FOUND).body(redirectView);
    }
    
    /**
     * Обрабатывает callback от Keycloak после успешной авторизации.
     * Обменивает код на токены и создает сессию пользователя.
     *
     * @param code код авторизации из Keycloak
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @return ResponseEntity с RedirectView для перенаправления на фронтенд
     */
    @GetMapping("/auth/callback")
    public ResponseEntity<RedirectView> handleCallback(
            @RequestParam("code") String code,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        String sessionId = request.getSession().getId();
        String codeVerifier = (String) request.getSession().getAttribute("code_verifier_" + sessionId);
        
        if (codeVerifier == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        try {
            TokenResponse tokenResponse = keycloakService.exchangeCodeForTokens(
                code,
                request.getRequestURL().toString(),
                codeVerifier
            );
            
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            
            // Извлекаем данные пользователя из JWT (упрощенно)
            String userId = "user1"; // В продакшене декодировать JWT и извлечь
            String userName = "User One";
            String userEmail = "user1@example.com";
            String[] roles = {"user"};
            
            // Сохраняем токены в Redis
            tokenCacheService.storeAccessToken(
                sessionId, 
                tokenResponse.getAccessToken(), 
                tokenResponse.getExpiresIn()
            );
            tokenCacheService.storeRefreshToken(
                sessionId, 
                tokenResponse.getRefreshToken(), 
                refreshTokenLifetime
            );
            
            // Сохраняем данные сессии
            com.bionicpro.auth2.model.SessionData sessionData = new com.bionicpro.auth2.model.SessionData();
            sessionData.setSessionId(sessionId);
            sessionData.setUserId(userId);
            sessionData.setUserName(userName);
            sessionData.setUserEmail(userEmail);
            sessionData.setRoles(roles);
            sessionData.setClientIpAddress(getClientIpAddress(request));
            sessionData.setUserAgent(request.getHeader("User-Agent"));
            tokenCacheService.storeSessionData(sessionId, sessionData, accessTokenLifetime);
            
            // Устанавливаем cookie для сессии
            response.addCookie(createSessionCookie(sessionId));
            
            RedirectView redirectView = new RedirectView(frontendRedirectUri);
            return ResponseEntity.status(HttpStatus.FOUND).body(redirectView);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Выполняет выход пользователя.
     * Удаляет токены из Redis и перенаправляет пользователя на страницу выхода Keycloak.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @return ResponseEntity с RedirectView для перенаправления на logout Keycloak
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<RedirectView> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = request.getSession().getId();
        
        // Удаляем токены из Redis
        tokenCacheService.removeAccessToken(sessionId);
        tokenCacheService.removeRefreshToken(sessionId);
        tokenCacheService.removeSessionData(sessionId);
        
        // Очищаем cookie
        response.addCookie(createSessionCookie(sessionId, true));
        
        URI logoutUri = keycloakService.getLogoutUri(
            "dummy_refresh_token", // В продакшене получить из Redis
            frontendRedirectUri
        );
        
        RedirectView redirectView = new RedirectView(logoutUri.toString());
        return ResponseEntity.status(HttpStatus.FOUND).body(redirectView);
    }
    
    /**
     * Создает cookie для сессии.
     *
     * @param sessionId ID сессии
     * @return Cookie для сессии
     */
    private Cookie createSessionCookie(String sessionId) {
        return createSessionCookie(sessionId, false);
    }
    
    /**
     * Создает cookie для сессии с возможностью очистки.
     *
     * @param sessionId ID сессии
     * @param clear флаг очистки cookie
     * @return Cookie для сессии
     */
    private Cookie createSessionCookie(String sessionId, boolean clear) {
        Cookie cookie = new Cookie("BIONIC_AUTH2_SESSION", clear ? "" : sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite("Strict");
        cookie.setPath("/");
        cookie.setMaxAge(clear ? 0 : 600); // 10 минут
        return cookie;
    }
    
    /**
     * Получает IP-адрес клиента из запроса.
     * Учитывает заголовки X-Forwarded-For и X-Real-IP для прокси.
     *
     * @param request HTTP запрос
     * @return IP-адрес клиента
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress != null && !ipAddress.isEmpty()) {
            // X-Forwarded-For может содержать несколько IP через запятую, берем первый
            return ipAddress.split(",")[0].trim();
        }
        ipAddress = request.getHeader("X-Real-IP");
        if (ipAddress != null && !ipAddress.isEmpty()) {
            return ipAddress;
        }
        return request.getRemoteAddr();
    }
}