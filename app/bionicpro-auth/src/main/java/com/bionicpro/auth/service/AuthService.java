package com.bionicpro.auth.service;

import com.bionicpro.auth.model.request.LoginRequest;
import com.bionicpro.auth.model.response.AuthResponse;
import com.bionicpro.auth.model.response.TokenResponse;

public interface AuthService {
    String initiateLogin();
    AuthResponse handleCallback(LoginRequest loginRequest);
    TokenResponse refreshTokens(String refreshToken);
    void logout(String refreshToken);
}