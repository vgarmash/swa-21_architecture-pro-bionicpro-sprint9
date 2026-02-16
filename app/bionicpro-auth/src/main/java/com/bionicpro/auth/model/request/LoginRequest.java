package com.bionicpro.auth.model.request;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String codeVerifier;

    @NotBlank
    private String redirectUri;

    // Constructors
    public LoginRequest() {}

    public LoginRequest(String code, String codeVerifier, String redirectUri) {
        this.code = code;
        this.codeVerifier = codeVerifier;
        this.redirectUri = redirectUri;
    }

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCodeVerifier() {
        return codeVerifier;
    }

    public void setCodeVerifier(String codeVerifier) {
        this.codeVerifier = codeVerifier;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}