package com.bionicpro.service;

import com.bionicpro.model.AuthRequestData;
import com.bionicpro.model.SessionData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;

/**
 * Сервис для управления сессиями с хранением в Redis.
 * Обрабатывает создание, валидацию, ротацию сессий и хранение токенов.
 */
public interface SessionService {
    
    /**
     * Сохраняет параметры запроса аутентификации перед перенаправлением на Keycloak.
     */
    void storeAuthRequest(String state, String redirectUri);

    /**
     * Сохраняет расширенные параметры OAuth2 запроса аутентификации (redirectUri + PKCE + nonce).
     */
    void storeAuthRequest(String state, AuthRequestData authRequestData);

    /**
     * Получает и удаляет сохранённый redirectUri запроса аутентификации.
     */
    String getAuthRequest(String state);

    /**
     * Получает и удаляет расширенные данные OAuth2 запроса аутентификации.
     */
    AuthRequestData getAuthRequestData(String state);

    /**
     * Создаёт новую сессию с токенами.
     */
    void createSession(HttpServletRequest request, HttpServletResponse response,
                       OidcIdToken idToken, OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken);

    /**
     * Получает данные сессии по ID сессии.
     */
    SessionData getSession(String sessionId);

    /**
     * Валидирует сессию и обновляет токены при необходимости.
     */
    SessionData validateAndRefreshSession(String sessionId);

    /**
     * Обновляет access токен с помощью refresh токена.
     * Вызывает endpoint Keycloak token с grant_type=refresh_token.
     */
    SessionData refreshAccessToken(SessionData sessionData);

    /**
     * Ротация сессии - генерирует новый ID сессии, аннулирует старый.
     * Этот метод принимает sessionId в качестве параметра и возвращает новые данные сессии.
     * Должен вызываться при каждом аутентифицированном запросе.
     */
    SessionData rotateSession(String sessionId);

    /**
     * Ротация сессии из запроса - генерирует новый ID сессии, аннулирует старый.
     * Этот метод извлекает sessionId из запроса и устанавливает новую куку.
     */
    void rotateSession(HttpServletRequest request, HttpServletResponse response);

    /**
     * Отзывает access и refresh токены путём вызова endpoint выхода Keycloak.
     * Должен вызываться при выходе пользователя для аннулирования токенов в Keycloak.
     */
    boolean revokeTokens(String refreshToken);

    /**
     * Аннулирует сессию и отзывает токены.
     * Должен вызываться при выходе пользователя для корректного отзыва токенов в Keycloak.
     */
    void invalidateSessionWithTokenRevocation(HttpServletRequest request, HttpServletResponse response);

    /**
     * Аннулирует сессию из запроса.
     */
    void invalidateSession(HttpServletRequest request, HttpServletResponse response);

    /**
     * Аннулирует сессию по ID.
     */
    void invalidateSessionById(String sessionId);

    /**
     * Получает время истечения сессии.
     */
    Instant getSessionExpiration(HttpServletRequest request);

    /**
     * Получает расшифрованный access токен для сессии.
     */
    String getAccessToken(String sessionId);

    /**
     * Получает ID сессии из куки запроса.
     */
    String getSessionIdFromRequest(HttpServletRequest request);

    /**
     * Устанавливает куку сессии с атрибутами безопасности.
     * Публичный метод для разрешения фильтрам устанавливать куки.
     */
    void setSessionCookieFromFilter(HttpServletResponse response, String sessionId);
}
