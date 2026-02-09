/**
 * Контроллер для обработки аутентификационных запросов.
 * Обрабатывает вход пользователя, обмен кода на токены, обновление токенов и выход.
 * Реализует OAuth2 PKCE (Proof Key for Code Exchange) для безопасности.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.controller;

import com.bionicpro.auth.model.PKCEParams;
import com.bionicpro.auth.model.TokenResponse;
import com.bionicpro.auth.service.KeycloakService;
import com.bionicpro.auth.util.CryptoUtil;
import com.bionicpro.auth.util.PKCEUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;

@Controller
public class AuthController {
    
    /**
     * URI для перенаправления после аутентификации
     */
    @Value("${bff.frontend.redirect-uri}")
    private String frontendRedirectUri;
    
    /**
     * Ключ шифрования для зашифрования токенов
     */
    @Value("${bff.encryption.key}")
    private String encryptionKey;
    
    /**
     * Сервис для работы с Keycloak
     */
    private final KeycloakService keycloakService;
    
    /**
     * Конструктор контроллера.
     *
     * @param keycloakService сервис для работы с Keycloak
     */
    public AuthController(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
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
        
        // Store code verifier in session temporarily
        HttpSession session = request.getSession();
        String sessionId = session.getId();
        session.setAttribute("code_verifier_" + sessionId, pkceParams.getCodeVerifier());
        
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
        
        HttpSession session = request.getSession();
        String sessionId = session.getId();
        String codeVerifier = (String) session.getAttribute("code_verifier_" + sessionId);
        
        if (codeVerifier == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        try {
            TokenResponse tokenResponse = keycloakService.exchangeCodeForTokens(
                code,
                request.getRequestURL().toString(),
                codeVerifier
            );
            
            // Extract user info from JWT (simplified)
            String userId = "user1"; // In production, decode JWT and extract
            String userName = "User One";
            String userEmail = "user1@example.com";
            String[] roles = {"user"};
            
            // Store encrypted refresh token in session
            String encryptedRefreshToken = CryptoUtil.encrypt(
                tokenResponse.getRefreshToken(), encryptionKey
            );
            session.setAttribute("encrypted_refresh_token", encryptedRefreshToken);
            session.setAttribute("access_token_expires_at", System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn());
            session.setAttribute("user_id", userId);
            session.setAttribute("user_name", userName);
            session.setAttribute("user_email", userEmail);
            session.setAttribute("roles", roles);
            session.setAttribute("client_ip_address", getClientIpAddress(request));
            session.setAttribute("user_agent", request.getHeader("User-Agent"));
            
            // Set session cookie
            response.addCookie(createSessionCookie(sessionId));
            
            RedirectView redirectView = new RedirectView(frontendRedirectUri);
            return ResponseEntity.status(HttpStatus.FOUND).body(redirectView);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Обновляет токены пользователя.
     * Использует refresh токен для получения новых access и refresh токенов.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @return ResponseEntity с статусом обновления
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<Void> refreshTokens(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        String encryptedRefreshToken = (String) session.getAttribute("encrypted_refresh_token");
        
        if (encryptedRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            // Decrypt refresh token
            String refreshToken = CryptoUtil.decrypt(encryptedRefreshToken, encryptionKey);
            
            // Refresh tokens with Keycloak
            TokenResponse tokenResponse = keycloakService.refreshTokens(refreshToken);
            
            // Update session with new tokens
            String newEncryptedRefreshToken = CryptoUtil.encrypt(
                tokenResponse.getRefreshToken(), encryptionKey
            );
            session.setAttribute("encrypted_refresh_token", newEncryptedRefreshToken);
            session.setAttribute("access_token_expires_at", System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn());
            
            // Set new session cookie
            response.addCookie(createSessionCookie(sessionId));
            
            return ResponseEntity.status(HttpStatus.OK).build();
            
        } catch (Exception e) {
            session.invalidate();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    /**
     * Выполняет выход пользователя.
     * Инвалидирует сессию и перенаправляет пользователя на страницу выхода Keycloak.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @return ResponseEntity с RedirectView для перенаправления на logout Keycloak
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<RedirectView> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        String sessionId = session.getId();
        
        // Invalidate session
        session.invalidate();
        
        // Clear cookie
        response.addCookie(createSessionCookie(sessionId, true));
        
        URI logoutUri = keycloakService.getLogoutUri(
            "dummy_refresh_token", // In production, get from session
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
        Cookie cookie = new Cookie("BIONICPRO_SESSION", clear ? "" : sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite("Strict");
        cookie.setPath("/");
        cookie.setMaxAge(clear ? 0 : 600); // 10 minutes
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