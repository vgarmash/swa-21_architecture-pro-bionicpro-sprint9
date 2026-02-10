/**
 * Сервис для кэширования токенов в Redis.
 * Хранит access_token и refresh_token в зашифрованном виде.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2.service;

import com.bionicpro.auth2.util.CryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Сервис для кэширования токенов в Redis.
 * Обеспечивает безопасное хранение токенов с использованием шифрования.
 */
@Service
public class TokenCacheService {
    
    /**
     * Префикс для ключей access_token в Redis
     */
    private static final String ACCESS_TOKEN_PREFIX = "auth2:access_token:";
    
    /**
     * Префикс для ключей refresh_token в Redis
     */
    private static final String REFRESH_TOKEN_PREFIX = "auth2:refresh_token:";
    
    /**
     * Префикс для ключей session_data в Redis
     */
    private static final String SESSION_DATA_PREFIX = "auth2:session:";
    
    /**
     * Ключ шифрования для зашифрования токенов
     */
    @Value("${bff.encryption.key}")
    private String encryptionKey;
    
    /**
     * Шаблон Redis для работы с данными
     */
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Конструктор сервиса кэширования токенов.
     *
     * @param redisTemplate шаблон Redis для работы с данными
     */
    public TokenCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Сохраняет access_token в Redis с привязкой к session ID.
     *
     * @param sessionId ID сессии
     * @param accessToken access token
     * @param expiresIn время истечения в секундах
     */
    public void storeAccessToken(String sessionId, String accessToken, int expiresIn) {
        String key = ACCESS_TOKEN_PREFIX + sessionId;
        String encryptedToken = CryptoUtil.encrypt(accessToken, encryptionKey);
        redisTemplate.opsForValue().set(key, encryptedToken, expiresIn, TimeUnit.SECONDS);
    }
    
    /**
     * Сохраняет refresh_token в Redis с привязкой к session ID.
     *
     * @param sessionId ID сессии
     * @param refreshToken refresh token
     * @param expiresIn время истечения в секундах
     */
    public void storeRefreshToken(String sessionId, String refreshToken, int expiresIn) {
        String key = REFRESH_TOKEN_PREFIX + sessionId;
        String encryptedToken = CryptoUtil.encrypt(refreshToken, encryptionKey);
        redisTemplate.opsForValue().set(key, encryptedToken, expiresIn, TimeUnit.SECONDS);
    }
    
    /**
     * Получает access_token из Redis по session ID.
     *
     * @param sessionId ID сессии
     * @return access token или null если не найден
     */
    public String getAccessToken(String sessionId) {
        String key = ACCESS_TOKEN_PREFIX + sessionId;
        Object encryptedToken = redisTemplate.opsForValue().get(key);
        if (encryptedToken == null) {
            return null;
        }
        return CryptoUtil.decrypt((String) encryptedToken, encryptionKey);
    }
    
    /**
     * Получает refresh_token из Redis по session ID.
     *
     * @param sessionId ID сессии
     * @return refresh token или null если не найден
     */
    public String getRefreshToken(String sessionId) {
        String key = REFRESH_TOKEN_PREFIX + sessionId;
        Object encryptedToken = redisTemplate.opsForValue().get(key);
        if (encryptedToken == null) {
            return null;
        }
        return CryptoUtil.decrypt((String) encryptedToken, encryptionKey);
    }
    
    /**
     * Удаляет access_token из Redis по session ID.
     *
     * @param sessionId ID сессии
     */
    public void removeAccessToken(String sessionId) {
        String key = ACCESS_TOKEN_PREFIX + sessionId;
        redisTemplate.delete(key);
    }
    
    /**
     * Удаляет refresh_token из Redis по session ID.
     *
     * @param sessionId ID сессии
     */
    public void removeRefreshToken(String sessionId) {
        String key = REFRESH_TOKEN_PREFIX + sessionId;
        redisTemplate.delete(key);
    }
    
    /**
     * Обновляет время истечения access_token в Redis.
     *
     * @param sessionId ID сессии
     * @param expiresIn новое время истечения в секундах
     */
    public void refreshAccessTokenTtl(String sessionId, int expiresIn) {
        String key = ACCESS_TOKEN_PREFIX + sessionId;
        redisTemplate.expire(key, expiresIn, TimeUnit.SECONDS);
    }
    
    /**
     * Обновляет время истечения refresh_token в Redis.
     *
     * @param sessionId ID сессии
     * @param expiresIn новое время истечения в секундах
     */
    public void refreshRefreshTokenTtl(String sessionId, int expiresIn) {
        String key = REFRESH_TOKEN_PREFIX + sessionId;
        redisTemplate.expire(key, expiresIn, TimeUnit.SECONDS);
    }
    
    /**
     * Сохраняет данные сессии в Redis.
     *
     * @param sessionId ID сессии
     * @param sessionData данные сессии
     * @param expiresIn время истечения в секундах
     */
    public void storeSessionData(String sessionId, Object sessionData, int expiresIn) {
        String key = SESSION_DATA_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, sessionData, expiresIn, TimeUnit.SECONDS);
    }
    
    /**
     * Получает данные сессии из Redis по session ID.
     *
     * @param sessionId ID сессии
     * @return данные сессии или null если не найдены
     */
    public Object getSessionData(String sessionId) {
        String key = SESSION_DATA_PREFIX + sessionId;
        return redisTemplate.opsForValue().get(key);
    }
    
    /**
     * Удаляет данные сессии из Redis по session ID.
     *
     * @param sessionId ID сессии
     */
    public void removeSessionData(String sessionId) {
        String key = SESSION_DATA_PREFIX + sessionId;
        redisTemplate.delete(key);
    }
}