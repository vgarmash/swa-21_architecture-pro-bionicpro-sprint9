package com.bionicpro.auth.repository;

import com.bionicpro.auth.model.SessionData;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemorySessionRepository {
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    public void save(SessionData sessionData) {
        sessions.put(sessionData.getSessionId(), sessionData);
    }

    public SessionData findById(String sessionId) {
        return sessions.get(sessionId);
    }

    public void deleteById(String sessionId) {
        sessions.remove(sessionId);
    }

    public Iterable<SessionData> findAll() {
        return sessions.values();
    }
}