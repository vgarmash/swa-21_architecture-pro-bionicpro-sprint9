package com.bionicpro.repository;

import com.bionicpro.model.SessionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Session repository implementation that uses Redis as the primary storage.
 * This implementation does not fall back to in-memory storage and is designed for
 * distributed environments where all instances must share the same session data.
 */
@Component
public class SessionRepositoryFacade implements SessionRepository {
    
    private final SessionRepository primaryRepository;
    
    public SessionRepositoryFacade(
            @Qualifier("redisSessionRepository") SessionRepository primaryRepository) {
        this.primaryRepository = primaryRepository;
    }
    
    @Override
    public void save(String sessionId, SessionData sessionData) {
        primaryRepository.save(sessionId, sessionData);
    }
    
    @Override
    public Optional<SessionData> findById(String sessionId) {
        return primaryRepository.findById(sessionId);
    }
    
    @Override
    public void deleteById(String sessionId) {
        primaryRepository.deleteById(sessionId);
    }
    
    @Override
    public boolean existsById(String sessionId) {
        return primaryRepository.existsById(sessionId);
    }
}