/**
 * Сервис для управления сессиями пользователей.
 * Обеспечивает создание, обновление, проверку и инвалидацию сессий.
 * Использует Redis для хранения данных сессий.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.service;

import com.bionicpro.auth.model.SessionData;
import com.bionicpro.auth.model.TokenResponse;
import com.bionicpro.auth.util.CryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class SessionService {
    
    /**
     * Ключ шифрования для зашифрования токенов
     */
    @Value("${bff.encryption.key}")
    private String encryptionKey;
    
    /**
     * Репозиторий сессий Spring Session
     */
    private final SessionRepository<? extends Session> sessionRepository;
    
    /**
     * Шаблон Redis для работы с данными
     */
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Конструктор сервиса сессий.
     *
     * @param sessionRepository репозиторий сессий Spring Session
     * @param redisTemplate шаблон Redis для работы с данными
     */
    public SessionService(SessionRepository<? extends Session> sessionRepository,
                         RedisTemplate<String, Object> redisTemplate) {
        this.sessionRepository = sessionRepository;
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Создает новую сессию пользователя.
     *
     * @param tokenResponse объект с токенами от Keycloak
     * @param userId ID пользователя
     * @param userName имя пользователя
     * @param userEmail email пользователя
     * @param roles роли пользователя
     * @return объект SessionData с данными сессии
     */
    public SessionData createSession(TokenResponse tokenResponse, String userId,
                                    String userName, String userEmail, String[] roles) {
        String sessionId = UUID.randomUUID().toString();
        
        String encryptedRefreshToken = CryptoUtil.encrypt(
            tokenResponse.getRefreshToken(), encryptionKey
        );
        
        SessionData sessionData = new SessionData();
        sessionData.setSessionId(sessionId);
        sessionData.setAccessToken(tokenResponse.getAccessToken());
        sessionData.setEncryptedRefreshToken(encryptedRefreshToken);
        sessionData.setAccessTokenExpiresAt(System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn());
        sessionData.setUserId(userId);
        sessionData.setUserName(userName);
        sessionData.setUserEmail(userEmail);
        sessionData.setRoles(roles);
        
        // Store in Redis with 10 minute TTL
        redisTemplate.opsForValue().set(
            "session:" + sessionId,
            sessionData,
            Duration.ofMinutes(10)
        );
        
        return sessionData;
    }
    
    /**
     * Получает данные сессии по ID.
     *
     * @param sessionId ID сессии
     * @return объект SessionData с данными сессии или null если сессия не найдена
     */
    public SessionData getSessionData(String sessionId) {
        return (SessionData) redisTemplate.opsForValue().get("session:" + sessionId);
    }
    
    /**
     * Обновляет токены в существующей сессии.
     *
     * @param sessionId ID сессии
     * @param tokenResponse объект с новыми токенами от Keycloak
     */
    public void updateSessionTokens(String sessionId, TokenResponse tokenResponse) {
        SessionData sessionData = getSessionData(sessionId);
        if (sessionData != null) {
            String encryptedRefreshToken = CryptoUtil.encrypt(
                tokenResponse.getRefreshToken(), encryptionKey
            );
            sessionData.setAccessToken(tokenResponse.getAccessToken());
            sessionData.setEncryptedRefreshToken(encryptedRefreshToken);
            sessionData.setAccessTokenExpiresAt(System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn());
            
            redisTemplate.opsForValue().set(
                "session:" + sessionId,
                sessionData,
                Duration.ofMinutes(10)
            );
        }
    }
    
    /**
     * Инвалидирует сессию пользователя.
     *
     * @param sessionId ID сессии для инвалидации
     */
    public void invalidateSession(String sessionId) {
        redisTemplate.delete("session:" + sessionId);
    }
    
    /**
     * Получает зашифрованный refresh токен из сессии.
     *
     * @param sessionId ID сессии
     * @return зашифрованный refresh токен или null если сессия не найдена
     */
    public String getEncryptedRefreshToken(String sessionId) {
        SessionData sessionData = getSessionData(sessionId);
        return sessionData != null ? sessionData.getEncryptedRefreshToken() : null;
    }
    
    /**
     * Получает access токен из сессии.
     *
     * @param sessionId ID сессии
     * @return access токен или null если сессия не найдена
     */
    public String getAccessToken(String sessionId) {
        SessionData sessionData = getSessionData(sessionId);
        return sessionData != null ? sessionData.getAccessToken() : null;
    }
}