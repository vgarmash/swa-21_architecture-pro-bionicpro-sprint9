package com.bionicpro.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionRotationFilter extends OncePerRequestFilter {

    private final SessionRotationService sessionRotationService;

    public SessionRotationFilter(SessionRotationService sessionRotationService) {
        this.sessionRotationService = sessionRotationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Выполняем ротацию сессии только для защищённых ресурсов
            if (isProtectedResource(request)) {
                sessionRotationService.rotateSession(request, response);
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            throw e;
        }
    }

    private boolean isProtectedResource(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/public/");
    }
}
