package com.bionicpro.auth.service;

import com.bionicpro.auth.model.SessionData;
import com.bionicpro.auth.model.response.SessionInfo;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionServiceImpl implements SessionService {

    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();

    @Override
    public void createSession(String username, String accessToken, String refreshToken) {
        String sessionId = generateSessionId();
        SessionData sessionData = new SessionData(sessionId, accessToken, refreshToken, username);
        sessions.put(sessionId, sessionData);
        sessionToUserMap.put(username, sessionId);
    }

    @Override
    public void updateSession(String username, String newAccessToken, String newRefreshToken) {
        String sessionId = sessionToUserMap.get(username);
        if (sessionId != null && sessions.containsKey(sessionId)) {
            SessionData sessionData = sessions.get(sessionId);
            sessionData.setAccessToken(newAccessToken);
            sessionData.setRefreshToken(newRefreshToken);
            sessionData.setLastAccessed(LocalDateTime.now());
        }
    }

    @Override
    public void endSession(String username) {
        String sessionId = sessionToUserMap.get(username);
        if (sessionId != null && sessions.containsKey(sessionId)) {
            sessions.remove(sessionId);
            sessionToUserMap.remove(username);
        }
    }

    @Override
    public SessionData getSession(String username) {
        String sessionId = sessionToUserMap.get(username);
        if (sessionId != null) {
            return sessions.get(sessionId);
        }
        return null;
    }

    @Override
    public SessionInfo getSessionById(String sessionId) {
        SessionData sessionData = sessions.get(sessionId);
        if (sessionData != null) {
            return new SessionInfo(
                    sessionData.getSessionId(),
                    sessionData.getUsername(),
                    null, // roles
                    3600, // sessionTimeout
                    sessionData.isActive()
            );
        }
        return null;
    }

    @Override
    public List<SessionInfo> getAllSessions() {
        List<SessionInfo> sessionInfos = new ArrayList<>();
        for (SessionData sessionData : sessions.values()) {
            sessionInfos.add(new SessionInfo(
                    sessionData.getSessionId(),
                    sessionData.getUsername(),
                    null, // roles
                    3600, // sessionTimeout
                    sessionData.isActive()
            ));
        }
        return sessionInfos;
    }

    @Override
    public void deleteSession(String sessionId) {
        SessionData sessionData = sessions.get(sessionId);
        if (sessionData != null) {
            sessions.remove(sessionId);
            sessionToUserMap.remove(sessionData.getUsername());
        }
    }

    @Override
    public SessionInfo getSessionInfo() {
        // Возвращает информацию о текущей сессии
        // В реальном приложении здесь будет логика получения текущего пользователя из JWT
        return null;
    }

    @Override
    public Boolean validateSession() {
        // Проверяет валидность текущей сессии
        // В реальном приложении здесь будет логика проверки JWT токена
        return false;
    }

    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString();
    }
}