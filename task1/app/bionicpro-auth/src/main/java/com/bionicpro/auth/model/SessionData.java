/**
 * Модель данных сессии пользователя.
 * Хранит информацию о пользовательской сессии, включая токены и данные пользователя.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Класс для хранения данных сессии пользователя.
 * Используется для передачи информации о сессии между компонентами системы.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionData {
    /**
     * ID сессии
     */
    private String sessionId;

    /**
     * Access токен пользователя
     */
    private String accessToken;

    /**
     * Зашифрованный refresh токен пользователя
     */
    private String encryptedRefreshToken;

    /**
     * Время истечения access токена в секундах
     */
    private Long accessTokenExpiresAt;

    /**
     * ID пользователя
     */
    private String userId;

    /**
     * Имя пользователя
     */
    private String userName;

    /**
     * Email пользователя
     */
    private String userEmail;

    /**
     * Роли пользователя
     */
    private String[] roles;

    /**
     * IP-адрес клиента
     */
    private String clientIpAddress;

    /**
     * User-Agent клиента
     */
    private String userAgent;
}