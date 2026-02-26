package com.bionicpro.service;

import com.bionicpro.audit.AuditService;
import com.bionicpro.mapper.SessionDataMapper;
import com.bionicpro.model.SessionData;
import com.bionicpro.repository.SessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис для управления сессиями с хранением в Redis.
 * Обрабатывает создание, валидацию, ротацию сессий и хранение токенов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final BytesEncryptor bytesEncryptor;
    private final AuditService auditService;
    private final SessionDataMapper sessionDataMapper;

    @Value("${auth.session.timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    @Value("${auth.session.cookie-name:BIONICPRO_SESSION}")
    private String cookieName;

    @Value("${keycloak.server-url:http://keycloak:8080}")
    private String keycloakUrl;

    @Value("${keycloak.realm:reports-realm}")
    private String keycloakRealm;

    @Value("${keycloak.client-id:bionicpro-auth}")
    private String clientId;

    private static final String TOKEN_URL_FORMAT = "%s/realms/%s/protocol/openid-connect/token";

    // Redis шаблон для хранения запросов аутентификации
    private final RedisTemplate<String, Object> redisTemplate;

    // Префикс ключа Redis для запросов аутентификации
    private static final String AUTH_REQUEST_PREFIX = "auth:request:";
    // TTL для запросов аутентификации (10 минут)
    private static final Duration AUTH_REQUEST_TTL = Duration.ofMinutes(10);

    /**
     * Сохраняет параметры запроса аутентификации перед перенаправлением на Keycloak.
     */
    @Override
    public void storeAuthRequest(String state, String redirectUri) {
        // Для auth request storage используем Redis напрямую, так как это специфичная логика
        String key = AUTH_REQUEST_PREFIX + state;
        redisTemplate.opsForValue().set(key, redirectUri, AUTH_REQUEST_TTL);
        log.debug("Stored auth request for state: {}", state);
    }

    /**
     * Получает и удаляет сохранённый запрос аутентификации.
     */
    @Override
    public String getAuthRequest(String state) {
        // Для auth request storage используем Redis напрямую, так как это специфичная логика
        String key = AUTH_REQUEST_PREFIX + state;
        String redirectUri = (String) redisTemplate.opsForValue().getAndDelete(key);
        log.debug("Retrieved auth request for state: {}, redirectUri: {}", state, redirectUri);
        return redirectUri;
    }

    /**
     * Создаёт новую сессию с токенами.
     */
    @Override
    public void createSession(HttpServletRequest request, HttpServletResponse response,
                              OidcIdToken idToken, OAuth2AccessToken accessToken, OAuth2RefreshToken refreshToken) {
        
        String sessionId = UUID.randomUUID().toString();
        
        // Создаём данные сессии
        SessionData sessionData = SessionData.builder()
                .sessionId(sessionId)
                .userId(idToken.getSubject())
                .username(idToken.getClaimAsString("preferred_username"))
                .roles(idToken.getClaimAsStringList("roles"))
                .accessToken(encryptToken(accessToken.getTokenValue()))
                .refreshToken(refreshToken != null ? encryptToken(refreshToken.getTokenValue()) : null)
                .accessTokenExpiresAt(accessToken.getExpiresAt())
                .refreshTokenExpiresAt(refreshToken != null ? refreshToken.getExpiresAt() : null)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(sessionTimeoutMinutes)))
                .lastAccessedAt(Instant.now())
                .build();
        
        // Сохраняем в Redis
        String redisKey = getSessionKey(sessionId);
        redisTemplate.opsForValue().set(redisKey, sessionData, Duration.ofMinutes(sessionTimeoutMinutes));
        
        // Устанавливаем куку сессии
        setSessionCookie(response, sessionId);
        
        // Логирование аудита для созданной сессии
        auditService.logSessionCreated(sessionData.getUserId(), sessionId, request);
        
        log.info("Created session for user: {}", sessionData.getUserId());
    }

    /**
     * Получает данные сессии по ID сессии.
     */
    @Override
    public SessionData getSession(String sessionId) {
        return sessionRepository.findById(sessionId).orElse(null);
    }

    /**
     * Валидирует сессию и обновляет токены при необходимости.
     */
    @Override
    public SessionData validateAndRefreshSession(String sessionId) {
        SessionData sessionData = getSession(sessionId);
        
        if (sessionData == null) {
            return null;
        }
        
        // Проверяем, истекла ли сессия
        if (sessionData.getExpiresAt() != null && Instant.now().isAfter(sessionData.getExpiresAt())) {
            log.info("Session expired for user: {}", sessionData.getUserId());
            // Логирование аудита для истёкшей сессии
            auditService.logSessionExpired(sessionData.getUserId(), sessionId);
            invalidateSessionById(sessionId);
            return null;
        }
        
        // Проверяем, нужно ли обновить access токен (истёк или истекает в течение 30 секунд)
        if (sessionData.getAccessTokenExpiresAt() != null
                && Instant.now().plusSeconds(30).isAfter(sessionData.getAccessTokenExpiresAt())) {
            // Токен нуждается в обновлении - вызываем Keycloak для обновления
            log.debug("Access token needs refresh for user: {}", sessionData.getUserId());
            
            if (sessionData.getRefreshToken() != null) {
                sessionData = refreshAccessToken(sessionData);
                
                if (sessionData == null) {
                    // Обновление не удалось - аннулируем сессию
                    log.warn("Token refresh failed for user: {}", sessionData.getUserId());
                    invalidateSessionById(sessionId);
                    return null;
                }
            } else {
                log.warn("No refresh token available for user: {}", sessionData.getUserId());
                // Аннулируем сессию, если нет refresh токена
                invalidateSessionById(sessionId);
                return null;
            }
        }
        
        // Обновляем время последнего доступа
        sessionData.setLastAccessedAt(Instant.now());
        updateSession(sessionId, sessionData);
        
        return sessionData;
    }

    /**
     * Обновляет access токен с помощью refresh токена.
     * Вызывает endpoint Keycloak token с grant_type=refresh_token.
     */
    @Override
    public SessionData refreshAccessToken(SessionData sessionData) {
        try {
            String tokenUrl = String.format(TOKEN_URL_FORMAT, keycloakUrl, keycloakRealm);
            
            // Получаем расшифрованный refresh токен
            String refreshToken = decryptToken(sessionData.getRefreshToken());
            
            // Подготавливаем параметры запроса
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("client_id", clientId);
            params.add("refresh_token", refreshToken);
            
            // Устанавливаем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            // Выполняем POST запрос к Keycloak
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                
                // Извлекаем новые токены
                String newAccessToken = (String) tokenResponse.get("access_token");
                String newRefreshToken = (String) tokenResponse.get("refresh_token");
                Integer expiresIn = (Integer) tokenResponse.get("expires_in");
                
                // Обновляем данные сессии новыми токенами
                sessionData.setAccessToken(encryptToken(newAccessToken));
                if (newRefreshToken != null) {
                    sessionData.setRefreshToken(encryptToken(newRefreshToken));
                }
                
                // Вычисляем новые времена истечения
                Instant now = Instant.now();
                sessionData.setAccessTokenExpiresAt(now.plusSeconds(expiresIn != null ? expiresIn : 300));
                
                // Логирование аудита для обновления токена
                auditService.logTokenRefresh(sessionData.getUserId(), sessionData.getSessionId(), null);
                
                // Обновляем время истечения refresh токена, если предоставлено
                if (tokenResponse.get("refresh_expires_in") != null) {
                    Integer refreshExpiresIn = (Integer) tokenResponse.get("refresh_expires_in");
                    sessionData.setRefreshTokenExpiresAt(now.plusSeconds(refreshExpiresIn));
                }
                
                log.info("Successfully refreshed token for user: {}", sessionData.getUserId());
                return sessionData;
            } else {
                log.error("Unexpected response from Keycloak during token refresh: {}", response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("Failed to refresh access token for user: {} - Error: {}", 
                    sessionData.getUserId(), e.getMessage());
            return null;
        }
    }

    /**
     * Ротация сессии - генерирует новый ID сессии, аннулирует старый.
     * Этот метод принимает sessionId в качестве параметра и возвращает новые данные сессии.
     * Должен вызываться при каждом аутентифицированном запросе.
     */
    @Override
    public SessionData rotateSession(String sessionId) {
        if (sessionId == null) {
            log.debug("No session to rotate");
            return null;
        }
        
        SessionData oldSession = getSession(sessionId);
        
        if (oldSession == null) {
            log.debug("Session not found for rotation");
            return null;
        }
        
        // Генерируем новый ID сессии
        String newSessionId = UUID.randomUUID().toString();
        
        // Копируем данные в новую сессию с помощью маппера
        SessionData newSession = sessionDataMapper.copyForRotation(oldSession, newSessionId);
        
        // Сохраняем новую сессию
        String newRedisKey = getSessionKey(newSessionId);
        redisTemplate.opsForValue().set(newRedisKey, newSession, Duration.ofMinutes(sessionTimeoutMinutes));
        
        // Аннулируем старую сессию
        invalidateSessionById(sessionId);
        
        log.info("Rotated session for user: {} from {} to {}", oldSession.getUserId(), sessionId, newSessionId);
        
        return newSession;
    }

    /**
     * Ротация сессии из запроса - генерирует новый ID сессии, аннулирует старый.
     * Этот метод извлекает sessionId из запроса и устанавливает новую куку.
     */
    @Override
    public void rotateSession(HttpServletRequest request, HttpServletResponse response) {
        String oldSessionId = getSessionIdFromRequest(request);
        
        if (oldSessionId == null) {
            log.debug("No session to rotate");
            return;
        }
        
        SessionData newSession = rotateSession(oldSessionId);
        
        if (newSession != null) {
            // Устанавливаем новую куку с новым ID сессии
            setSessionCookie(response, newSession.getSessionId());
        }
    }

    /**
     * Отзывает access и refresh токены путём вызова endpoint выхода Keycloak.
     * Должен вызываться при выходе пользователя для аннулирования токенов в Keycloak.
     */
    @Override
    public boolean revokeTokens(String refreshToken) {
        if (refreshToken == null) {
            log.debug("No refresh token to revoke");
            return true; // Нет токена для отзыва, считаем успехом
        }
        
        try {
            String tokenUrl = String.format(TOKEN_URL_FORMAT, keycloakUrl, keycloakRealm);
            
            // Подготавливаем параметры запроса для endpoint выхода
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("refresh_token", refreshToken);
            
            // Устанавливаем заголовки
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            // Используем POST для endpoint выхода Keycloak
            restTemplate.postForEntity(tokenUrl + "/logout", request, String.class);
            
            log.info("Successfully revoked tokens in Keycloak");
            return true;
            
        } catch (Exception e) {
            log.warn("Failed to revoke tokens in Keycloak: {}", e.getMessage());
            // Всё равно возвращаем true - локальная сессия всё равно аннулируется
            return true;
        }
    }
    
    /**
     * Аннулирует сессию и отзывает токены.
     * Должен вызываться при выходе пользователя для корректного отзыва токенов в Keycloak.
     */
    @Override
    public void invalidateSessionWithTokenRevocation(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = getSessionIdFromRequest(request);
        
        if (sessionId != null) {
            // Получаем данные сессии для отзыва токенов
            SessionData sessionData = getSession(sessionId);
            if (sessionData != null && sessionData.getRefreshToken() != null) {
                // Отзываем токены в Keycloak
                String refreshToken = decryptToken(sessionData.getRefreshToken());
                revokeTokens(refreshToken);
            }
            
            // Аннулируем сессию
            invalidateSessionById(sessionId);
        }
        
        // Очищаем куку
        clearSessionCookie(response);
        
        log.info("Session invalidated with token revocation");
    }
    
    /**
     * Аннулирует сессию из запроса.
     */
    @Override
    public void invalidateSession(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = getSessionIdFromRequest(request);
        
        if (sessionId != null) {
            invalidateSessionById(sessionId);
        }
        
        // Очищаем куку
        clearSessionCookie(response);
        
        log.info("Session invalidated");
    }

    /**
     * Аннулирует сессию по ID.
     */
    @Override
    public void invalidateSessionById(String sessionId) {
        // Получаем данные сессии до аннулирования для логирования аудита
        SessionData sessionData = getSession(sessionId);
        String userId = sessionData != null ? sessionData.getUserId() : null;
        
        sessionRepository.deleteById(sessionId);
        
        // Логирование аудита для аннулированной сессии
        if (userId != null) {
            auditService.logSessionInvalidated(userId, sessionId);
        }
        
        log.debug("Deleted session: {}", sessionId);
    }

    /**
     * Получает время истечения сессии.
     */
    @Override
    public Instant getSessionExpiration(HttpServletRequest request) {
        String sessionId = getSessionIdFromRequest(request);
        
        if (sessionId != null) {
            SessionData sessionData = getSession(sessionId);
            if (sessionData != null) {
                return sessionData.getExpiresAt();
            }
        }
        
        return null;
    }

    /**
     * Получает расшифрованный access токен для сессии.
     */
    @Override
    public String getAccessToken(String sessionId) {
        SessionData sessionData = getSession(sessionId);
        
        if (sessionData != null && sessionData.getAccessToken() != null) {
            return decryptToken(sessionData.getAccessToken());
        }
        
        return null;
    }

    /**
     * Получает ID сессии из куки запроса.
     */
    @Override
    public String getSessionIdFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }

    /**
     * Устанавливает куку сессии с атрибутами безопасности.
     * Публичный метод для разрешения фильтрам устанавливать куки.
     */
    @Override
    public void setSessionCookieFromFilter(HttpServletResponse response, String sessionId) {
        setSessionCookie(response, sessionId);
    }

    /**
     * Обновляет сессию в репозитории.
     */
    private void updateSession(String sessionId, SessionData sessionData) {
        sessionRepository.save(sessionId, sessionData);
    }

    /**
     * Устанавливает куку сессии с атрибутами безопасности.
     */
    private void setSessionCookie(HttpServletResponse response, String sessionId) {
        Cookie cookie = new Cookie(cookieName, sessionId);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(sessionTimeoutMinutes * 60);
        cookie.setAttribute("SameSite", "Lax");
        
        response.addCookie(cookie);
        log.debug("Set session cookie: {}", sessionId);
    }

    /**
     * Очищает куку сессии.
     */
    private void clearSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        
        response.addCookie(cookie);
        log.debug("Cleared session cookie");
    }

    /**
     * Получает Redis ключ для сессии.
     */
    private String getSessionKey(String sessionId) {
        return "bionicpro:session:" + sessionId;
    }

    /**
     * Шифрует значение токена.
     */
    private String encryptToken(String token) {
        byte[] encrypted = bytesEncryptor.encrypt(token.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Расшифровывает значение токена.
     */
    private String decryptToken(String encryptedToken) {
        byte[] decoded = Base64.getDecoder().decode(encryptedToken);
        byte[] decrypted = bytesEncryptor.decrypt(decoded);
        return new String(decrypted);
    }
    
    private final RestTemplate restTemplate = new RestTemplate();
}