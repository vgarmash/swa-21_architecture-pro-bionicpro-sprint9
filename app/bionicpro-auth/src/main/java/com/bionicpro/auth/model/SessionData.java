package com.bionicpro.auth.model;

import java.time.LocalDateTime;
import java.util.List;

public class SessionData {
    private String sessionId;
    private String accessToken;
    private String refreshToken;
    private String keycloakUserId;
    private String username;
    private List<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessed;
    private LocalDateTime expiresAt;

    // Constructors
    public SessionData() {}

    public SessionData(String sessionId, String accessToken, String refreshToken, String username) {
        this.sessionId = sessionId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
        this.createdAt = LocalDateTime.now();
        this.lastAccessed = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusHours(1);
    }

    public SessionData(String sessionId, String accessToken, String refreshToken, String keycloakUserId,
                       String username, List<String> roles, LocalDateTime createdAt,
                       LocalDateTime lastAccessed, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.keycloakUserId = keycloakUserId;
        this.username = username;
        this.roles = roles;
        this.createdAt = createdAt;
        this.lastAccessed = lastAccessed;
        this.expiresAt = expiresAt;
    }

    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(LocalDateTime lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return expiresAt.isAfter(LocalDateTime.now());
    }
}