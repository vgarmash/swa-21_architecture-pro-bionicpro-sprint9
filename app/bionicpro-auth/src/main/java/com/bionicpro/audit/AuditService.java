package com.bionicpro.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Service authentication audit interface for logging events.
 * Provides methods to record various authentication-related operations
 * for security auditing and compliance purposes.
 */
public interface AuditService {

    /**
     * Logs a generic audit event.
     *
     * @param event the audit event to log
     */
    void log(AuditEvent event);

    /**
     * Logs a successful authentication event.
     *
     * @param userId    the ID of the user who authenticated
     * @param sessionId the session ID associated with the authentication
     * @param request   the HTTP request (for IP and user agent extraction)
     */
    void logAuthenticationSuccess(String userId, String sessionId, HttpServletRequest request);

    /**
     * Logs a failed authentication event.
     *
     * @param username the username that failed to authenticate
     * @param error    the error message or error type
     * @param request  the HTTP request (for IP and user agent extraction)
     */
    void logAuthenticationFailure(String username, String error, HttpServletRequest request);

    /**
     * Logs a logout event.
     *
     * @param userId    the ID of the user who logged out
     * @param sessionId the session ID that was terminated
     * @param request   the HTTP request (for IP and user agent extraction)
     */
    void logLogout(String userId, String sessionId, HttpServletRequest request);

    /**
     * Logs a token refresh event.
     *
     * @param userId    the ID of the user whose token was refreshed
     * @param sessionId the session ID associated with the refresh
     * @param request   the HTTP request (for IP and user agent extraction)
     */
    void logTokenRefresh(String userId, String sessionId, HttpServletRequest request);

    /**
     * Logs a session creation event.
     *
     * @param userId    the ID of the user for whom the session was created
     * @param sessionId the newly created session ID
     * @param request   the HTTP request (for IP and user agent extraction)
     */
    void logSessionCreated(String userId, String sessionId, HttpServletRequest request);

    /**
     * Logs a session expiration event.
     *
     * @param userId    the ID of the user whose session expired
     * @param sessionId the session ID that expired
     */
    void logSessionExpired(String userId, String sessionId);

    /**
     * Logs a session invalidation event.
     *
     * @param userId    the ID of the user whose session was invalidated
     * @param sessionId the session ID that was invalidated
     */
    void logSessionInvalidated(String userId, String sessionId);
}
