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
 * Filter that generates and manages correlation IDs for request tracing.
 * <p>
 * This filter:
 * <ul>
 *   <li>Generates a UUID correlation ID if not present in request header</li>
 *   <li>Extracts X-Correlation-ID header if present</li>
 *   <li>Sets correlation ID in MDC for the request lifecycle</li>
 *   <li>Adds X-Correlation-ID header to response</li>
 * </ul>
 * <p>
 * Supports both servlet (Spring MVC) and reactive (Spring WebFlux) contexts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Header name for correlation ID.
     */
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * MDC key for correlation ID.
     */
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * MDC key for client IP.
     */
    private static final String CLIENT_IP_MDC_KEY = "clientIp";

    /**
     * Logger for correlation ID filter.
     */
    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);

    /**
     * Processes each request to ensure a correlation ID is present.
     * 
     * @param request  the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException if an error occurs during filtering
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String correlationId = getOrGenerateCorrelationId(request);
        
        // Set correlation ID in response header
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        // Set correlation ID in MDC for logging
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        
        // Set client IP in MDC if available
        String clientIp = resolveClientIp(request);
        if (clientIp != null) {
            MDC.put(CLIENT_IP_MDC_KEY, clientIp);
        }

        try {
            logger.debug("Processing request with correlation ID: {} for URI: {}", 
                    correlationId, request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            // Clean up MDC after request is complete
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(CLIENT_IP_MDC_KEY);
        }
    }

    /**
     * Gets the correlation ID from the request header or generates a new one.
     *
     * @param request the HTTP request
     * @return the correlation ID
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
     * Resolves the client IP address from the request.
     * Checks X-Forwarded-For header first, then X-Real-IP, then remote address.
     *
     * @param request the HTTP request
     * @return the client IP address
     */
    private String resolveClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header first
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP in the chain (original client)
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }

        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        // Fall back to remote address
        return request.getRemoteAddr();
    }

    /**
     * Returns the filter order. This filter should run first to ensure
     * correlation ID is available for all subsequent filters.
     *
     * @return the filter order
     */
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
