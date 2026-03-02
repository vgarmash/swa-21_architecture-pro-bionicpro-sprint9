package com.bionicpro.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.util.Map;

/**
 * Сервис для управления аутентификацией.
 * Обрабатывает OAuth2 потоки, обмен токенами и аутентификацию пользователя.
 */
public interface AuthService {
    
    /**
     * Инициирует поток аутентификации путём перенаправления на Keycloak.
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param redirectUri Опциональный URI для перенаправления
     */
    void initiateAuthentication(HttpServletRequest request, HttpServletResponse response, String redirectUri);

    /**
     * Обрабатывает OAuth2 callback от Keycloak.
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param code Код авторизации от Keycloak
     * @param state Параметр state для валидации
     * @param sessionState Состояние сессии от Keycloak
     * @return Map, содержащая URL перенаправления или информацию об ошибке
     */
    Map<String, String> handleCallback(HttpServletRequest request, HttpServletResponse response,
                                       String code, String state, String sessionState);

    /**
     * Проверяет статус аутентификации.
     * @param request HTTP запрос
     * @return Информация о статусе аутентификации
     */
    Map<String, Object> getAuthStatus(HttpServletRequest request);

    /**
     * Выход пользователя и аннулирование сессии.
     * @param request HTTP запрос
     * @param response HTTP ответ
     */
    void logout(HttpServletRequest request, HttpServletResponse response);

    /**
     * Обновление сессии.
     * @param request HTTP запрос
     * @param response HTTP ответ
     */
    void refreshSession(HttpServletRequest request, HttpServletResponse response);

    /**
     * Проверяет и обновляет сессию при необходимости.
     * @param request HTTP запрос
     * @return Данные сессии если валидны, иначе null
     */
    Object validateAndRefreshSession(HttpServletRequest request);

    /**
     * Получает данные пользователя из ID токена.
     * @param idToken ID токен
     * @return Map с данными пользователя
     */
    Map<String, Object> getUserDetails(OidcIdToken idToken);
}