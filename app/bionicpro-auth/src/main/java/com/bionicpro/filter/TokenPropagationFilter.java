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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter for propagating access token to backend API requests.
 * Extracts session from cookie, validates it, and adds access token to outgoing requests.
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
        
        // Skip for auth endpoints
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Get session ID from cookie
        String sessionId = getSessionIdFromRequest(request);
        
        if (sessionId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"not_authenticated\",\"message\":\"No session found\"}");
            return;
        }
        
        // Validate and refresh session
        var sessionData = sessionService.validateAndRefreshSession(sessionId);
        
        if (sessionData == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"not_authenticated\",\"message\":\"Session expired or invalid\"}");
            return;
        }
        
        // Get access token
        String accessToken = sessionService.getAccessToken(sessionId);
        
        if (accessToken == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"not_authenticated\",\"message\":\"No access token\"}");
            return;
        }
        
        // Add access token to request attribute for downstream use
        request.setAttribute("accessToken", accessToken);
        request.setAttribute("userId", sessionData.getUserId());
        
        log.debug("Token propagated for user: {}", sessionData.getUserId());
        
        filterChain.doFilter(request, response);
    }

    /**
     * Extract session ID from request cookies.
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
