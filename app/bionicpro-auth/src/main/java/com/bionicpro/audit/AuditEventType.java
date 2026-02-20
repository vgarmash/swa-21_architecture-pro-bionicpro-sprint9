package com.bionicpro.audit;

/**
 * Enum representing different types of authentication audit events.
 */
public enum AuditEventType {
    /**
     * User successfully authenticated.
     */
    AUTHENTICATION_SUCCESS,
    
    /**
     * User failed to authenticate (invalid credentials).
     */
    AUTHENTICATION_FAILURE,
    
    /**
     * User logged out.
     */
    LOGOUT,
    
    /**
     * Access token was refreshed.
     */
    TOKEN_REFRESH,
    
    /**
     * New session was created.
     */
    SESSION_CREATED,
    
    /**
     * Session expired.
     */
    SESSION_EXPIRED,
    
    /**
     * Session was invalidated (logged out, etc.).
     */
    SESSION_INVALIDATED
}
