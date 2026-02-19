package com.bionicpro.repository;

import com.bionicpro.model.SessionData;
import java.util.Optional;

/**
 * Session repository interface for storing and retrieving session data.
 * This interface provides methods for session management operations.
 */
public interface SessionRepository {
    
    /**
     * Save session data.
     * @param sessionId the session ID
     * @param sessionData the session data to save
     */
    void save(String sessionId, SessionData sessionData);
    
    /**
     * Find session data by session ID.
     * @param sessionId the session ID
     * @return Optional containing session data if found, empty otherwise
     */
    Optional<SessionData> findById(String sessionId);
    
    /**
     * Delete session data by session ID.
     * @param sessionId the session ID
     */
    void deleteById(String sessionId);
    
    /**
     * Check if session exists by session ID.
     * @param sessionId the session ID
     * @return true if session exists, false otherwise
     */
    boolean existsById(String sessionId);
}