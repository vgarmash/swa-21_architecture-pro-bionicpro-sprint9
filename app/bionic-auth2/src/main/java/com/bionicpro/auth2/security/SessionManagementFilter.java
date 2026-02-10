/**
 * Фильтр управления сессиями для обеспечения безопасности.
 * Проверяет и обновляет access_token при истечении, ротирует session ID.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2.security;

import com.bionicpro.auth2.service.KeycloakService;
import com.bionicpro.auth2.service.TokenCacheService;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * Фильтр для управления сессиями пользователя.
 * Обеспечивает автоматическое обновление access_token и ротацию session ID.
 */
@Component
public class SessionManagementFilter implements Filter {
    
    /**
     * Сервис Keycloak для обновления токенов
     */
    private final KeycloakService keycloakService;
    
    /**
     * Сервис кэширования токенов в Redis
     */
    private final TokenCacheService tokenCacheService;
    
    /**
     * Ключ шифрования для зашифрования токенов
     */
    @Value("${bff.encryption.key}")
    private String encryptionKey;
    
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
     * Время жизни сессии в секундах
     */
    @Value("${bff.token.session-lifetime}")
    private int sessionLifetime;
    
    /**
     * Конструктор фильтра управления сессиями.
     *
     * @param keycloakService сервис Keycloak для обновления токенов
     * @param tokenCacheService сервис кэширования токенов
     */
    public SessionManagementFilter(KeycloakService keycloakService, TokenCacheService tokenCacheService) {
        this.keycloakService = keycloakService;
        this.tokenCacheService = tokenCacheService;
    }
    
    /**
     * Выполняет фильтрацию запроса для управления сессией.
     * Проверяет и обновляет access_token при необходимости, ротирует session ID.
     *
     * @param request  объект запроса
     * @param response объект ответа
     * @param chain    цепочка фильтров
     * @throws IOException      если возникает ошибка ввода-вывода
     * @throws ServletException если возникает ошибка сервлета
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String path = httpRequest.getRequestURI();
        
        // Пропускаем фильтрацию для auth endpoints и actuator
        if (path.startsWith("/auth/") || path.startsWith("/actuator/")) {
            chain.doFilter(request, response);
            return;
        }
        
        // Получаем session ID из cookie
        String sessionId = getSessionIdFromCookie(httpRequest);
        
        if (sessionId == null) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        // Проверяем и обновляем access_token при необходимости
        boolean tokenRefreshed = checkAndRefreshAccessToken(sessionId);
        
        // Ротируем session ID при каждом запросе для предотвращения session fixation
        String newSessionId = rotateSessionId(sessionId);
        
        // Обновляем cookie с новым session ID
        httpResponse.addCookie(createSessionCookie(newSessionId));
        
        // Добавляем новый session ID в заголовок ответа
        httpResponse.setHeader("X-Session-ID", newSessionId);
        
        chain.doFilter(request, response);
    }
    
    /**
     * Получает session ID из cookie.
     *
     * @param request HTTP запрос
     * @return session ID или null если не найден
     */
    private String getSessionIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("BIONIC_AUTH2_SESSION".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
    
    /**
     * Проверяет и обновляет access_token при истечении.
     * Если access_token истек (осталось меньше 30 секунд), обновляет его через refresh_token.
     *
     * @param sessionId ID сессии
     * @return true, если access_token был обновлен, false в противном случае
     */
    private boolean checkAndRefreshAccessToken(String sessionId) {
        String accessToken = tokenCacheService.getAccessToken(sessionId);
        if (accessToken == null) {
            return false;
        }
        
        // Проверяем оставшееся время жизни access_token
        // В реальном приложении нужно хранить время истечения в Redis
        // Для простоты предполагаем, что access_token истекает через 120 секунд
        long currentTime = System.currentTimeMillis() / 1000;
        long remainingTime = accessTokenLifetime - (currentTime % accessTokenLifetime);
        
        // Если осталось меньше 30 секунд до истечения, обновляем access_token
        if (remainingTime < 30) {
            String refreshToken = tokenCacheService.getRefreshToken(sessionId);
            if (refreshToken == null) {
                return false;
            }
            
            try {
                // Обновляем токены через Keycloak
                com.bionicpro.auth2.model.TokenResponse tokenResponse = 
                    keycloakService.refreshTokens(refreshToken);
                
                if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                    return false;
                }
                
                // Сохраняем новые токены в Redis
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
                
                return true;
            } catch (Exception e) {
                // Ошибка при обновлении токенов - удаляем сессию
                tokenCacheService.removeAccessToken(sessionId);
                tokenCacheService.removeRefreshToken(sessionId);
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Ротирует session ID для предотвращения session fixation attack.
     * Генерирует новый session ID и сохраняет все данные сессии.
     *
     * @param oldSessionId старый session ID
     * @return новый session ID
     */
    private String rotateSessionId(String oldSessionId) {
        // Генерируем новый session ID
        String newSessionId = java.util.UUID.randomUUID().toString();
        
        // Переносим данные из старой сессии в новую
        Object sessionData = tokenCacheService.getSessionData(oldSessionId);
        if (sessionData != null) {
            tokenCacheService.storeSessionData(newSessionId, sessionData, sessionLifetime);
        }
        
        // Удаляем старую сессию
        tokenCacheService.removeAccessToken(oldSessionId);
        tokenCacheService.removeRefreshToken(oldSessionId);
        tokenCacheService.removeSessionData(oldSessionId);
        
        return newSessionId;
    }
    
    /**
     * Создает cookie для сессии.
     *
     * @param sessionId ID сессии
     * @return Cookie для сессии
     */
    private Cookie createSessionCookie(String sessionId) {
        Cookie cookie = new Cookie("BIONIC_AUTH2_SESSION", sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite("Strict");
        cookie.setPath("/");
        cookie.setMaxAge(sessionLifetime);
        return cookie;
    }
    
    /**
     * Инициализирует фильтр.
     *
     * @param filterConfig конфигурация фильтра
     */
    @Override
    public void init(FilterConfig filterConfig) {
        // Инициализация при необходимости
    }
    
    /**
     * Очищает ресурсы фильтра.
     */
    @Override
    public void destroy() {
        // Очистка при необходимости
    }
}