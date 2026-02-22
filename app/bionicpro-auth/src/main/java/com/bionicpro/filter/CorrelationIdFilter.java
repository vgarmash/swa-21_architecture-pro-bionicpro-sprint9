package com.bionicpro.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;





import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр, который генерирует и управляет идентификаторами корреляции для трассировки запросов.
 * <p>
 * Этот фильтр:
 * <ul>
 *   <li>Генерирует UUID идентификатор корреляции, если он отсутствует в заголовке запроса</li>
 *   <li>Извлекает заголовок X-Correlation-ID, если он присутствует</li>
 *   <li>Устанавливает идентификатор корреляции в MDC на протяжении жизненного цикла запроса</li>
 *   <li>Добавляет заголовок X-Correlation-ID в ответ</li>
 * </ul>
 * <p>
 * Поддерживает как сервлетный (Spring MVC), так и реактивный (Spring WebFlux) контексты.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Имя заголовка для идентификатора корреляции.
     */
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Ключ MDC для идентификатора корреляции.
     */
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Ключ MDC для IP-адреса клиента.
     */
    private static final String CLIENT_IP_MDC_KEY = "clientIp";

    /**
     * Логгер для фильтра идентификатора корреляции.
     */
    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);

    /**
     * Обрабатывает каждый запрос, чтобы гарантировать наличие идентификатора корреляции.
     * 
     * @param request  HTTP-запрос
     * @param response HTTP-ответ
     * @param filterChain цепочка фильтров
     * @throws ServletException если происходит ошибка во время фильтрации
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String correlationId = getOrGenerateCorrelationId(request);
        
        // Устанавливаем идентификатор корреляции в заголовок ответа
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        // Устанавливаем идентификатор корреляции в MDC для логирования
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        
        // Устанавливаем IP-адрес клиента в MDC, если доступен
        String clientIp = resolveClientIp(request);
        if (clientIp != null) {
            MDC.put(CLIENT_IP_MDC_KEY, clientIp);
        }

        try {
            logger.debug("Processing request with correlation ID: {} for URI: {}", 
                    correlationId, request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            // Очищаем MDC после завершения запроса
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(CLIENT_IP_MDC_KEY);
        }
    }

    /**
     * Получает идентификатор корреляции из заголовка запроса или генерирует новый.
     *
     * @param request HTTP-запрос
     * @return идентификатор корреляции
     */
    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            logger.debug("Generated new correlation ID: {}", correlationId);
        } else {
            logger.debug("Using existing correlation ID from header: {}", correlationId);
        }
        
        return correlationId;
    }

    /**
     * Разрешает IP-адрес клиента из запроса.
     * Сначала проверяет заголовок X-Forwarded-For, затем X-Real-IP, затем удалённый адрес.
     *
     * @param request HTTP-запрос
     * @return IP-адрес клиента
     */
    private String resolveClientIp(HttpServletRequest request) {
        // Сначала проверяем заголовок X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Берём первый IP в цепочке (оригинальный клиент)
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }

        // Проверяем заголовок X-Real-IP
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        // Используем удалённый адрес как запасной вариант
        return request.getRemoteAddr();
    }

    /**
     * Возвращает порядок выполнения фильтра. Этот фильтр должен запускаться первым,
     * чтобы гарантировать доступность идентификатора корреляции для всех последующих фильтров.
     *
     * @return порядок фильтра
     */
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
