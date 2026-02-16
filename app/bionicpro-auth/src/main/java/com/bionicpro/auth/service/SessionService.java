package com.bionicpro.auth.service;

import com.bionicpro.auth.model.SessionData;
import com.bionicpro.auth.model.response.SessionInfo;

import java.util.List;

public interface SessionService {
    void createSession(String username, String accessToken, String refreshToken);
    void updateSession(String username, String newAccessToken, String newRefreshToken);
    void endSession(String username);
    SessionData getSession(String username);
    SessionInfo getSessionById(String sessionId);
    List<SessionInfo> getAllSessions();
    void deleteSession(String sessionId);
    SessionInfo getSessionInfo();
    Boolean validateSession();
}