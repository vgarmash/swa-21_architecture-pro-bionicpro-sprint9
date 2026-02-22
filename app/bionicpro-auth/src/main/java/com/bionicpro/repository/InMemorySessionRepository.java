package com.bionicpro.repository;

import com.bionicpro.model.SessionData;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация SessionRepository.
 * Этот репозиторий служит как запасной вариант, когда Redis недоступен.
 */
@Repository
public class InMemorySessionRepository implements SessionRepository {
    
    private final Map<String, SessionData> sessionStore = new ConcurrentHashMap<>();
    
    @Override
    public void save(String sessionId, SessionData sessionData) {
        sessionStore.put(sessionId, sessionData);
    }
    
    @Override
    public Optional<SessionData> findById(String sessionId) {
        return Optional.ofNullable(sessionStore.get(sessionId));
    }
    
    @Override
    public void deleteById(String sessionId) {
        sessionStore.remove(sessionId);
    }
    
    @Override
    public boolean existsById(String sessionId) {
        return sessionStore.containsKey(sessionId);
    }
}