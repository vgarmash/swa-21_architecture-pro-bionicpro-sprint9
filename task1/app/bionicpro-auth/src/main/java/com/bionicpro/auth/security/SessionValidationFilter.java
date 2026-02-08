/**
 * Фильтр проверки сессии для обеспечения безопасности.
 * Проверяет существование сессии пользователя перед обработкой запроса.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.security;

import com.bionicpro.auth.service.SessionService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
     * Конструктор фильтра проверки сессии.
     *
     * @param sessionService сервис управления сессиями
     */
    public SessionValidationFilter(SessionService sessionService) {
        this.sessionService = sessionService;
    }
    
    /**
     * Выполняет фильтрацию запроса для проверки сессии.
     * Проверяет существование сессии для всех запросов, кроме специальных эндпоинтов.
     *
     * @param request объект запроса
     * @param response объект ответа
     * @param chain цепочка фильтров
     * @throws IOException если возникает ошибка ввода-вывода
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
        
        chain.doFilter(request, response);
    }
}