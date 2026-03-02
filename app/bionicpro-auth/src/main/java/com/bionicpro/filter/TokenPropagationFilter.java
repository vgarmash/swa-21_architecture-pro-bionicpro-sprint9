package com.bionicpro.filter;

import com.bionicpro.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Фильтр для распространения токена доступа к запросам backend API.
 * Извлекает сессию из cookie, проверяет её и добавляет токен доступа к исходящим запросам.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class TokenPropagationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Пропускаем для эндпоинтов аутентификации
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Получаем идентификатор сессии из cookie
        String sessionId = getSessionIdFromRequest(request);

        if (sessionId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"not_authenticated\",\"message\":\"No session found\"}");
            return;
        }

        // Проверяем и обновляем сессию
        var sessionData = sessionService.validateAndRefreshSession(sessionId);

        if (sessionData == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"not_authenticated\",\"message\":\"Session expired or invalid\"}");
            return;
        }

        // Получаем токен доступа
        String accessToken = sessionService.getAccessToken(sessionId);

        if (accessToken == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"not_authenticated\",\"message\":\"No access token\"}");
            return;
        }

        // Добавляем токен доступа в атрибут запроса для использования далее
        request.setAttribute("accessToken", accessToken);
        request.setAttribute("userId", sessionData.getUserId());

        // Создаём Authentication и устанавливаем в SecurityContext
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (sessionData.getRoles() != null) {
            sessionData.getRoles().forEach(role ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                sessionData.getUserId(),
                accessToken,
                authorities
            );
        authentication.setDetails(sessionData);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        
        // Set the security context to the SecurityContextHolder so Spring Security sees it
        SecurityContextHolder.setContext(securityContext);
        
        // Also store the security context in the request for the SecurityContextRepository to use
        request.setAttribute("CUSTOM_SECURITY_CONTEXT", securityContext);

        log.debug("Token propagated for user: {}", sessionData.getUserId());

        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает идентификатор сессии из cookies запроса.
     */
    private String getSessionIdFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("BIONICPRO_SESSION".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }
}
