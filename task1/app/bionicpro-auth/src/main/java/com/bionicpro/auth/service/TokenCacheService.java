/**
 * Сервис для кэширования access токенов.
 * Использует Redis для временного хранения access токенов.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenCacheService {
    
    /**
     * Шаблон Redis для работы с данными
     */
    private final RedisTemplate<String, String> redisTemplate;
    
    /**
     * Конструктор сервиса кэширования токенов.
     *
     * @param redisTemplate шаблон Redis для работы с данными
     */
    public TokenCacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Получает access токен из кэша по ID сессии.
     *
     * @param sessionId ID сессии
     * @return access токен или null если токен не найден в кэше
     */
    public String getAccessToken(String sessionId) {
        return redisTemplate.opsForValue().get("access_token:" + sessionId);
    }
    
    /**
     * Кэширует access токен для сессии.
     *
     * @param sessionId ID сессии
     * @param accessToken access токен для кэширования
     */
    public void cacheAccessToken(String sessionId, String accessToken) {
        redisTemplate.opsForValue().set(
            "access_token:" + sessionId,
            accessToken,
            Duration.ofMinutes(2)
        );
    }
    
    /**
     * Удаляет access токен из кэша для сессии.
     *
     * @param sessionId ID сессии
     */
    public void invalidateAccessToken(String sessionId) {
        redisTemplate.delete("access_token:" + sessionId);
    }
}