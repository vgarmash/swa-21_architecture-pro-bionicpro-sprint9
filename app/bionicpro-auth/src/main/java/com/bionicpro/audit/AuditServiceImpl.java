package com.bionicpro.audit;

import com.bionicpro.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Implementation of the AuditService interface.
 * Provides methods to record various authentication-related operations
 * for security auditing and compliance purposes.
 * <p>
 * Uses SLF4J with logger name "AUDIT" for all audit logging.
 * Extracts correlation ID from MDC for request tracing.
 */
@Service
public class AuditServiceImpl implements AuditService {

    /**
     * Logger name for audit events.
     */
    private static final String AUDIT_LOGGER_NAME = "AUDIT";

    /**
     * MDC key for correlation ID.
     */
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * MDC key for client IP.
     */
    private static final String CLIENT_IP_MDC_KEY = "clientIp";

    /**
     * MDC key for user ID.
     */
    private static final String USER_ID_MDC_KEY = "userId";

    /**
     * The audit logger instance.
     */
    private final Logger auditLogger;

    /**
     * Client IP resolver for extracting client IP from requests.
     */
    private final ClientIpResolver clientIpResolver;

    /**
     * Constructs a new AuditServiceImpl with the given client IP resolver.
     *
     * @param clientIpResolver the client IP resolver
     */
    public AuditServiceImpl(ClientIpResolver clientIpResolver) {
        this.auditLogger = LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(AuditEvent event) {
        if (event == null) {
            return;
        }

        // Set MDC context for the log event
        setMdcContext(event);

        try {
            // Log the audit event as JSON
            auditLogger.info(event.toString());
        } finally {
            // Clear MDC context after logging
            clearMdcContext();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logAuthenticationSuccess(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.AUTHENTICATION_SUCCESS, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logAuthenticationFailure(String username, String error, HttpServletRequest request) {
        // Sanitize the username for security (never log actual usernames on failure)
        String sanitizedUsername = sanitizeUsername(username);
        
        AuditEvent event = buildAuditEvent(AuditEventType.AUTHENTICATION_FAILURE, sanitizedUsername, null, request)
                .outcome("FAILURE")
                .errorType("INVALID_CREDENTIALS")
                .errorMessage(AuditEvent.sanitizeErrorMessage(error))
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logLogout(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.LOGOUT, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logTokenRefresh(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.TOKEN_REFRESH, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logSessionCreated(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.SESSION_CREATED, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logSessionExpired(String userId, String sessionId) {
        AuditEvent event = AuditEvent.builder()
                .timestamp(Instant.now())
                .correlationId(getCorrelationId())
                .eventType(AuditEventType.SESSION_EXPIRED)
                .principal(sanitizeUserId(userId))
                .clientIp(getMdcClientIp())
                .sessionId(sanitizeSessionId(sessionId))
                .outcome("EXPIRED")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logSessionInvalidated(String userId, String sessionId) {
        AuditEvent event = AuditEvent.builder()
                .timestamp(Instant.now())
                .correlationId(getCorrelationId())
                .eventType(AuditEventType.SESSION_INVALIDATED)
                .principal(sanitizeUserId(userId))
                .clientIp(getMdcClientIp())
                .sessionId(sanitizeSessionId(sessionId))
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * Builds a base AuditEvent with common fields from the request.
     *
     * @param eventType  the type of audit event
     * @param userId    the user ID
     * @param sessionId the session ID (may be null)
     * @param request   the HTTP request
     * @return the audit event builder
     */
    private AuditEvent.AuditEventBuilder buildAuditEvent(AuditEventType eventType, 
                                                          String userId, 
                                                          String sessionId, 
                                                          HttpServletRequest request) {
        String clientIp = clientIpResolver.getClientIp(request);
        String userAgent = ClientIpResolver.sanitizeUserAgent(
                clientIpResolver.getUserAgent(request));

        return AuditEvent.builder()
                .timestamp(Instant.now())
                .correlationId(getCorrelationId())
                .eventType(eventType)
                .principal(sanitizeUserId(userId))
                .clientIp(clientIp)
                .userAgent(userAgent)
                .sessionId(sanitizeSessionId(sessionId));
    }

    /**
     * Gets the correlation ID from MDC, or null if not set.
     *
     * @return the correlation ID from MDC
     */
    private String getCorrelationId() {
        return MDC.get(CORRELATION_ID_MDC_KEY);
    }

    /**
     * Gets the client IP from MDC, or null if not set.
     *
     * @return the client IP from MDC
     */
    private String getMdcClientIp() {
        return MDC.get(CLIENT_IP_MDC_KEY);
    }

    /**
     * Sets the MDC context from the audit event for logging.
     *
     * @param event the audit event
     */
    private void setMdcContext(AuditEvent event) {
        if (event.getCorrelationId() != null) {
            MDC.put(CORRELATION_ID_MDC_KEY, event.getCorrelationId());
        }
        if (event.getClientIp() != null) {
            MDC.put(CLIENT_IP_MDC_KEY, event.getClientIp());
        }
        if (event.getPrincipal() != null) {
            MDC.put(USER_ID_MDC_KEY, event.getPrincipal());
        }
    }

    /**
     * Clears the MDC context after logging.
     */
    private void clearMdcContext() {
        MDC.remove(CORRELATION_ID_MDC_KEY);
        MDC.remove(CLIENT_IP_MDC_KEY);
        MDC.remove(USER_ID_MDC_KEY);
    }

    /**
     * Sanitizes the username for secure logging.
     * On authentication failure, we never log the actual username to avoid
     * giving attackers information about valid usernames.
     *
     * @param username the raw username
     * @return sanitized username
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "unknown";
        }
        // For failed auth attempts, don't reveal whether the username exists
        // by not logging it at all - just mark it as attempted
        return "[REDACTED]";
    }

    /**
     * Sanitizes the user ID for secure logging.
     *
     * @param userId the raw user ID
     * @return sanitized user ID
     */
    private String sanitizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "unknown";
        }
        // Truncate if too long
        if (userId.length() > 100) {
            return userId.substring(0, 100) + "...[truncated]";
        }
        return userId;
    }

    /**
     * Sanitizes the session ID for secure logging.
     * Masks part of the session ID to prevent session hijacking.
     *
     * @param sessionId the raw session ID
     * @return sanitized session ID
     */
    private String sanitizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        // Mask the session ID, showing only first 8 and last 8 characters
        if (sessionId.length() > 20) {
            return sessionId.substring(0, 8) + "..." + sessionId.substring(sessionId.length() - 8);
        }
        // For short session IDs, mask completely
        return "[SESSION_ID]";
    }
}
