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
import com.bionicpro.auth.service.KeycloakService;
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
     * Сервис кэширования access токенов
     */
    private final TokenCacheService tokenCacheService;

    /**
     * Конструктор сервиса сессий.
     *
     * @param sessionRepository репозиторий сессий Spring Session
     * @param redisTemplate     шаблон Redis для работы с данными
     * @param tokenCacheService сервис кэширования access токенов
     */
    public SessionService(SessionRepository<? extends Session> sessionRepository,
                          RedisTemplate<String, Object> redisTemplate,
                          TokenCacheService tokenCacheService) {
        this.sessionRepository = sessionRepository;
        this.redisTemplate = redisTemplate;
        this.tokenCacheService = tokenCacheService;
    }

    /**
     * Создает новую сессию пользователя.
     *
     * @param tokenResponse   объект с токенами от Keycloak
     * @param userId          ID пользователя
     * @param userName        имя пользователя
     * @param userEmail       email пользователя
     * @param roles           роли пользователя
     * @param clientIpAddress IP-адрес клиента
     * @param userAgent       User-Agent клиента
     * @return объект SessionData с данными сессии
     */
    public SessionData createSession(TokenResponse tokenResponse, String userId,
                                     String userName, String userEmail, String[] roles,
                                     String clientIpAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();

        String encryptedRefreshToken = CryptoUtil.encrypt(
                tokenResponse.getRefreshToken(), encryptionKey
        );

        SessionData sessionData = new SessionData();
        sessionData.setSessionId(sessionId);
        sessionData.setEncryptedRefreshToken(encryptedRefreshToken);
        sessionData.setAccessTokenExpiresAt(System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn());
        sessionData.setUserId(userId);
        sessionData.setUserName(userName);
        sessionData.setUserEmail(userEmail);
        sessionData.setRoles(roles);
        sessionData.setClientIpAddress(clientIpAddress);
        sessionData.setUserAgent(userAgent);

        // Store in Redis with 2 hour TTL
        redisTemplate.opsForValue().set(
                "session:" + sessionId,
                sessionData,
                Duration.ofHours(2)
        );

        // Cache access token with 2 minutes TTL
        tokenCacheService.cacheAccessToken(sessionId, tokenResponse.getAccessToken());

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
     * @param sessionId     ID сессии
     * @param tokenResponse объект с новыми токенами от Keycloak
     */
    public void updateSessionTokens(String sessionId, TokenResponse tokenResponse) {
        SessionData sessionData = getSessionData(sessionId);
        if (sessionData != null) {
            String encryptedRefreshToken = CryptoUtil.encrypt(
                    tokenResponse.getRefreshToken(), encryptionKey
            );
            sessionData.setEncryptedRefreshToken(encryptedRefreshToken);
            sessionData.setAccessTokenExpiresAt(System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn());

            redisTemplate.opsForValue().set(
                    "session:" + sessionId,
                    sessionData,
                    Duration.ofMinutes(10)
            );

            // Update access token in cache with 2 minutes TTL
            tokenCacheService.cacheAccessToken(sessionId, tokenResponse.getAccessToken());
        }
    }

    /**
     * Инвалидирует сессию пользователя.
     *
     * @param sessionId ID сессии для инвалидации
     */
    public void invalidateSession(String sessionId) {
        // Invalidate access token in cache
        tokenCacheService.invalidateAccessToken(sessionId);
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

    /**
     * Ротирует session ID при каждом запросе.
     * Генерирует новый session ID, сохраняет все остальные данные в Redis.
     *
     * @param sessionId текущий ID сессии
     * @return новый session ID
     */
    public String rotateSessionId(String sessionId) {
        SessionData sessionData = getSessionData(sessionId);
        if (sessionData == null) {
            return null;
        }

        // Генерируем новый session ID
        String newSessionId = UUID.randomUUID().toString();

        // Получаем access token из кэша для переноса в новую сессию
        String accessToken = tokenCacheService.getAccessToken(sessionId);

        // Обновляем session ID в объекте данных
        sessionData.setSessionId(newSessionId);

        // Удаляем старую запись в Redis
        redisTemplate.delete("session:" + sessionId);

        // Удаляем старый access token из кэша
        tokenCacheService.invalidateAccessToken(sessionId);

        // Сохраняем новую запись в Redis с тем же TTL
        redisTemplate.opsForValue().set(
                "session:" + newSessionId,
                sessionData,
                Duration.ofHours(2)
        );

        // Сохраняем новый access token в кэше с 2 минутами TTL
        if (accessToken != null) {
            tokenCacheService.cacheAccessToken(newSessionId, accessToken);
        }

        return newSessionId;
    }

    /**
     * Проверяет и обновляет access_token при истечении.
     * Если access_token истек (осталось меньше 30 секунд), обновляет его через refresh_token.
     *
     * @param sessionId       ID сессии
     * @param keycloakService сервис Keycloak для обновления токенов
     * @return true, если access_token был обновлен, false в противном случае
     */
    public boolean checkAndRefreshAccessToken(String sessionId, KeycloakService keycloakService) {
        // Получаем access token из кэша
        String accessToken = tokenCacheService.getAccessToken(sessionId);
        if (accessToken == null) {
            return false;
        }

        // Получаем данные сессии для получения времени истечения access_token
        SessionData sessionData = getSessionData(sessionId);
        if (sessionData == null) {
            return false;
        }

        long expiresAt = sessionData.getAccessTokenExpiresAt();
        long currentTime = System.currentTimeMillis() / 1000;
        long remainingTime = expiresAt - currentTime;

        // Если осталось меньше 30 секунд до истечения, обновляем access_token
        if (remainingTime < 30) {
            // Получаем refresh token из зашифрованного значения
            String encryptedRefreshToken = sessionData.getEncryptedRefreshToken();
            if (encryptedRefreshToken == null) {
                return false;
            }

            // Расшифровываем refresh token
            String refreshToken = CryptoUtil.decrypt(encryptedRefreshToken, encryptionKey);
            if (refreshToken == null) {
                return false;
            }

            // Обновляем токены через Keycloak
            TokenResponse tokenResponse = keycloakService.refreshTokens(refreshToken);
            if (tokenResponse == null) {
                return false;
            }

            // Обновляем access token в кэше с TTL 2 минуты
            tokenCacheService.cacheAccessToken(sessionId, tokenResponse.getAccessToken());

            // Обновляем данные сессии с новым временем истечения
            sessionData.setAccessTokenExpiresAt(System.currentTimeMillis() / 1000 + tokenResponse.getExpiresIn());
            redisTemplate.opsForValue().set(
                    "session:" + sessionId,
                    sessionData,
                    Duration.ofHours(2)
            );

            return true;
        }

        return false;
    }
}