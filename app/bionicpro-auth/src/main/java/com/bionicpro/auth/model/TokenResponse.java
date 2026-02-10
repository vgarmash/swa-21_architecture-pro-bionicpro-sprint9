/**
 * Модель ответа от Keycloak при обмене кода на токены.
 * Содержит все необходимые токены и данные для аутентификации пользователя.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Класс для представления ответа от Keycloak с токенами.
 * Используется для обработки данных, возвращаемых Keycloak после успешной авторизации.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    /**
     * Access токен для доступа к ресурсам
     */
    private String accessToken;
    
    /**
     * Refresh токен для обновления access токена
     */
    private String refreshToken;
    
    /**
     * ID токен для идентификации пользователя
     */
    private String idToken;
    
    /**
     * Тип токена (обычно "Bearer")
     */
    private String tokenType;
    
    /**
     * Время истечения токена в секундах
     */
    private Integer expiresIn;
    
    /**
     * Области доступа (scope)
     */
    private String scope;
    
    /**
     * Дополнительные параметры ответа
     */
    private Map<String, Object> additionalParameters;
}