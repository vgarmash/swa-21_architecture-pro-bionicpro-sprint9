package com.bionicpro.auth.model.response;

import java.time.LocalDateTime;
import java.util.List;

public class SessionInfo {
    private String sessionId;
    private String username;
    private List<String> roles;
    private long sessionTimeout;
    private boolean isActive;

    // Constructors
    public SessionInfo() {}

    public SessionInfo(String sessionId, String username, List<String> roles, long sessionTimeout, boolean isActive) {
        this.sessionId = sessionId;
        this.username = username;
        this.roles = roles;
        this.sessionTimeout = sessionTimeout;
        this.isActive = isActive;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }
}