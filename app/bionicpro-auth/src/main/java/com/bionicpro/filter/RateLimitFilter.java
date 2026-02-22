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
 * Фильтр ограничения частоты запросов для эндпоинтов аутентификации.
 * Реализует ограничения:
 * - Попытки входа: 10 в минуту с одного IP
 * - Попытки обновления токена: 5 в минуту с одного IP
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

        // Определяем, какой лимит применять на основе пути
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
     * Пытается потратить токен из корзины входа для данного IP.
     * @param clientIp IP-адрес клиента
     * @return true, если токен был потрачен, false, если лимит превышен
     */
    private boolean tryConsumeLogin(String clientIp) {
        Bucket bucket = loginBuckets.computeIfAbsent(clientIp, this::createLoginBucket);
        return bucket.tryConsume(1);
    }

    /**
     * Пытается потратить токен из корзины обновления для данного IP.
     * @param clientIp IP-адрес клиента
     * @return true, если токен был потрачен, false, если лимит превышен
     */
    private boolean tryConsumeRefresh(String clientIp) {
        Bucket bucket = refreshBuckets.computeIfAbsent(clientIp, this::createRefreshBucket);
        return bucket.tryConsume(1);
    }

    /**
     * Создаёт корзину для попыток входа с 10 запросами в минуту.
     * @param clientIp IP-адрес клиента (используется для логирования)
     * @return настроенная корзина
     */
    private Bucket createLoginBucket(String clientIp) {
        Bandwidth limit = Bandwidth.classic(LOGIN_ATTEMPTS_PER_MINUTE,
            Refill.greedy(LOGIN_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Создаёт корзину для попыток обновления с 5 запросами в минуту.
     * @param clientIp IP-адрес клиента (используется для логирования)
     * @return настроенная корзина
     */
    private Bucket createRefreshBucket(String clientIp) {
        Bandwidth limit = Bandwidth.classic(REFRESH_ATTEMPTS_PER_MINUTE,
            Refill.greedy(REFRESH_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Получает IP-адрес клиента из запроса.
     * Сначала проверяет заголовок X-Forwarded-For (для обратного прокси), затем возвращается к удалённому адресу.
     * @param request HTTP-запрос
     * @return IP-адрес клиента
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Берём первый IP в цепочке (оригинальный клиент)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Отправляет ответ 429 Too Many Requests.
     * @param response HTTP-ответ
     * @param message сообщение об ошибке
     */
    private void sendTooManyRequestsResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(String.format(
            "{\"error\":\"rate_limit_exceeded\",\"message\":\"%s\",\"retryAfter\":60}", message));
    }
}
