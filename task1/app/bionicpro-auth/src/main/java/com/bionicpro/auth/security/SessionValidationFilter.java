/**
 * Фильтр проверки сессии для обеспечения безопасности.
 * Проверяет существование сессии пользователя перед обработкой запроса.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.security;

import com.bionicpro.auth.service.KeycloakService;
import com.bionicpro.auth.service.SessionService;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

import com.bionicpro.auth.model.SessionData;

/**
 * Фильтр для проверки активности сессии пользователя.
 * Отбрасывает запросы без действительной сессии, кроме специальных эндпоинтов.
 */
@Component
public class SessionValidationFilter implements Filter {

    /**
     * Сервис управления сессиями
     */
    private final SessionService sessionService;

    /**
     * Сервис Keycloak для обновления токенов
     */
    private final KeycloakService keycloakService;

    /**
     * Конструктор фильтра проверки сессии.
     *
     * @param sessionService  сервис управления сессиями
     * @param keycloakService сервис Keycloak для обновления токенов
     */
    public SessionValidationFilter(SessionService sessionService, KeycloakService keycloakService) {
        this.sessionService = sessionService;
        this.keycloakService = keycloakService;
    }

    /**
     * Выполняет фильтрацию запроса для проверки сессии.
     * Проверяет существование сессии для всех запросов, кроме специальных эндпоинтов.
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

        String sessionId = httpRequest.getSession().getId();
        String path = httpRequest.getRequestURI();

        // Skip validation for auth endpoints
        if (path.startsWith("/auth/") || path.startsWith("/actuator/")) {
            chain.doFilter(request, response);
            return;
        }

        // Validate session exists
        if (sessionService.getSessionData(sessionId) == null) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // Получаем метаданные из запроса
        String clientIpAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // Проверяем метаданные сессии
        SessionData sessionData = sessionService.getSessionData(sessionId);
        if (sessionData != null && !validateSessionMetadata(sessionData, clientIpAddress, userAgent)) {
            // Метаданные не совпадают - сессия может быть скомпрометирована
            sessionService.invalidateSession(sessionId);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // Проверяем и обновляем access_token при необходимости
        boolean tokenRefreshed = sessionService.checkAndRefreshAccessToken(sessionId, keycloakService);

        // Ротируем session ID при каждом запросе
        String newSessionId = sessionService.rotateSessionId(sessionId);
        if (newSessionId != null) {
            // Добавляем новый session ID в заголовок ответа
            httpResponse.setHeader("X-Session-ID", newSessionId);

            // Обновляем cookie с новым session ID
            Cookie sessionCookie = new Cookie("JSESSIONID", newSessionId);
            sessionCookie.setPath(httpRequest.getContextPath() + "/");
            sessionCookie.setHttpOnly(true);
            sessionCookie.setSecure(httpRequest.isSecure());
            httpResponse.addCookie(sessionCookie);
        }

        chain.doFilter(request, response);
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

    /**
     * Проверяет соответствие метаданных сессии (IP и User-Agent).
     *
     * @param sessionData     данные сессии
     * @param clientIpAddress текущий IP-адрес клиента
     * @param userAgent       текущий User-Agent клиента
     * @return true если метаданные совпадают, false в противном случае
     */
    private boolean validateSessionMetadata(SessionData sessionData, String clientIpAddress, String userAgent) {
        if (sessionData == null) {
            return false;
        }
        return Objects.equals(sessionData.getClientIpAddress(), clientIpAddress) &&
                Objects.equals(sessionData.getUserAgent(), userAgent);
    }
}
}