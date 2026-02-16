package com.bionicpro.auth.service;

import com.bionicpro.auth.model.request.LoginRequest;
import com.bionicpro.auth.model.response.AuthResponse;
import com.bionicpro.auth.model.response.TokenResponse;
import com.bionicpro.auth.util.JwtUtil;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;

    public AuthServiceImpl(JwtUtil jwtUtil, SessionService sessionService) {
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
    }

    @Override
    public String initiateLogin() {
        // В реальном приложении здесь будет логика инициализации логина
        // Для примера просто возвращаем заглушку
        return "login_url";
    }

    @Override
    public AuthResponse handleCallback(LoginRequest loginRequest) {
        // Обработка callback от Keycloak
        // В реальном приложении здесь будет обмен кода на токены
        // Для примера генерируем токены с фиктивным username
        String username = "user@example.com"; // Из реального кода username из токена
        String accessToken = jwtUtil.generateAccessToken(username);
        String refreshToken = jwtUtil.generateRefreshToken(username);

        // Сохраняем сессию
        sessionService.createSession(username, accessToken, refreshToken);

        return new AuthResponse(accessToken, refreshToken);
    }

    @Override
    public TokenResponse refreshTokens(String refreshToken) {
        // Обновление токенов через refresh token
        String username = jwtUtil.getUsernameFromRefreshToken(refreshToken);
        String newAccessToken = jwtUtil.generateAccessToken(username);
        String newRefreshToken = jwtUtil.generateRefreshToken(username);

        // Обновляем сессию
        sessionService.updateSession(username, newAccessToken, newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken, 3600000, username);
    }

    @Override
    public void logout(String refreshToken) {
        // Завершаем сессию
        String username = jwtUtil.getUsernameFromRefreshToken(refreshToken);
        sessionService.endSession(username);
    }
}