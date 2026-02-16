package com.bionicpro.auth.model.response;

public class AuthResponse {
    private String sessionId;
    private boolean authenticated;
    private String redirectUrl;

    // Constructors
    public AuthResponse() {}

    public AuthResponse(String sessionId, boolean authenticated, String redirectUrl) {
        this.sessionId = sessionId;
        this.authenticated = authenticated;
        this.redirectUrl = redirectUrl;
    }

    public AuthResponse(String accessToken, String refreshToken) {
        this.authenticated = true;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    // Additional fields for token response
    private String accessToken;
    private String refreshToken;

    // Getters and Setters
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
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
}