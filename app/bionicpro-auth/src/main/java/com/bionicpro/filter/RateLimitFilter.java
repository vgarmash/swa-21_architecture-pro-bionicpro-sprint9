package com.bionicpro.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for authentication endpoints.
 * Implements limits:
 * - Login attempts: 10 per minute per IP
 * - Refresh attempts: 5 per minute per IP
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LOGIN_ATTEMPTS_PER_MINUTE = 10;
    private static final int REFRESH_ATTEMPTS_PER_MINUTE = 5;

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String clientIp = getClientIP(request);
        String path = request.getRequestURI();

        // Determine which rate limit to apply based on the path
        if (path.contains("/api/auth/login")) {
            if (!tryConsumeLogin(clientIp)) {
                log.warn("Rate limit exceeded for login attempts from IP: {}", clientIp);
                sendTooManyRequestsResponse(response, "Login rate limit exceeded. Maximum 10 attempts per minute.");
                return;
            }
        } else if (path.contains("/api/auth/refresh")) {
            if (!tryConsumeRefresh(clientIp)) {
                log.warn("Rate limit exceeded for refresh attempts from IP: {}", clientIp);
                sendTooManyRequestsResponse(response, "Refresh rate limit exceeded. Maximum 5 attempts per minute.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Attempts to consume a token from the login bucket for the given IP.
     * @param clientIp the client IP address
     * @return true if the token was consumed, false if rate limit exceeded
     */
    private boolean tryConsumeLogin(String clientIp) {
        Bucket bucket = loginBuckets.computeIfAbsent(clientIp, this::createLoginBucket);
        return bucket.tryConsume(1);
    }

    /**
     * Attempts to consume a token from the refresh bucket for the given IP.
     * @param clientIp the client IP address
     * @return true if the token was consumed, false if rate limit exceeded
     */
    private boolean tryConsumeRefresh(String clientIp) {
        Bucket bucket = refreshBuckets.computeIfAbsent(clientIp, this::createRefreshBucket);
        return bucket.tryConsume(1);
    }

    /**
     * Creates a bucket for login attempts with 10 requests per minute.
     * @param clientIp the client IP address (used for logging)
     * @return configured bucket
     */
    private Bucket createLoginBucket(String clientIp) {
        Bandwidth limit = Bandwidth.classic(LOGIN_ATTEMPTS_PER_MINUTE, 
            Refill.greedy(LOGIN_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Creates a bucket for refresh attempts with 5 requests per minute.
     * @param clientIp the client IP address (used for logging)
     * @return configured bucket
     */
    private Bucket createRefreshBucket(String clientIp) {
        Bandwidth limit = Bandwidth.classic(REFRESH_ATTEMPTS_PER_MINUTE, 
            Refill.greedy(REFRESH_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Gets the client IP address from the request.
     * Checks X-Forwarded-For header first (for reverse proxy), then falls back to remote address.
     * @param request the HTTP request
     * @return client IP address
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Sends a 429 Too Many Requests response.
     * @param response the HTTP response
     * @param message the error message
     */
    private void sendTooManyRequestsResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(String.format(
            "{\"error\":\"rate_limit_exceeded\",\"message\":\"%s\",\"retryAfter\":60}", message));
    }
}
