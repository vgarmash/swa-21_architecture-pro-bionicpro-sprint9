package com.bionicpro.audit;

import com.bionicpro.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Реализация интерфейса AuditService.
 * Предоставляет методы для записи различных операций, связанных с аутентификацией,
 * для целей аудита безопасности и соответствия требованиям.
 * <p>
 * Использует SLF4J с именем логгера "AUDIT" для всего аудит-логирования.
 * Извлекает идентификатор корреляции из MDC для трассировки запросов.
 */
@Service
public class AuditServiceImpl implements AuditService {

    /**
     * Имя логгера для событий аудита.
     */
    private static final String AUDIT_LOGGER_NAME = "AUDIT";

    /**
     * Ключ MDC для идентификатора корреляции.
     */
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Ключ MDC для IP-адреса клиента.
     */
    private static final String CLIENT_IP_MDC_KEY = "clientIp";

    /**
     * Ключ MDC для идентификатора пользователя.
     */
    private static final String USER_ID_MDC_KEY = "userId";

    /**
     * Экземпляр логгера аудита.
     */
    private final Logger auditLogger;

    /**
     * Резолвер IP-адреса клиента для извлечения IP-клиента из запросов.
     */
    // ClientIpResolver - утилитарный класс со статическими методами, не требует внедрения
    private static final ClientIpResolver clientIpResolver = null;

    /**
     * Создаёт новый AuditServiceImpl.
     */
    public AuditServiceImpl() {
        this.auditLogger = LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(AuditEvent event) {
        if (event == null) {
            return;
        }

        // Устанавливаем контекст MDC для события логирования
        setMdcContext(event);

        try {
            // Логируем событие аудита как JSON
            auditLogger.info(event.toString());
        } finally {
            // Очищаем контекст MDC после логирования
            clearMdcContext();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logAuthenticationSuccess(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.AUTHENTICATION_SUCCESS, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logAuthenticationFailure(String username, String error, HttpServletRequest request) {
        // Очищаем имя пользователя для безопасности (никогда не логируем реальные имена пользователей при ошибке)
        String sanitizedUsername = sanitizeUsername(username);

        AuditEvent event = buildAuditEvent(AuditEventType.AUTHENTICATION_FAILURE, sanitizedUsername, null, request)
                .outcome("FAILURE")
                .errorType("INVALID_CREDENTIALS")
                .errorMessage(AuditEvent.sanitizeErrorMessage(error))
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logLogout(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.LOGOUT, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logTokenRefresh(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.TOKEN_REFRESH, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logSessionCreated(String userId, String sessionId, HttpServletRequest request) {
        AuditEvent event = buildAuditEvent(AuditEventType.SESSION_CREATED, userId, sessionId, request)
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logSessionExpired(String userId, String sessionId) {
        AuditEvent event = AuditEvent.builder()
                .timestamp(Instant.now())
                .correlationId(getCorrelationId())
                .eventType(AuditEventType.SESSION_EXPIRED)
                .principal(sanitizeUserId(userId))
                .clientIp(getMdcClientIp())
                .sessionId(sanitizeSessionId(sessionId))
                .outcome("EXPIRED")
                .build();

        log(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logSessionInvalidated(String userId, String sessionId) {
        AuditEvent event = AuditEvent.builder()
                .timestamp(Instant.now())
                .correlationId(getCorrelationId())
                .eventType(AuditEventType.SESSION_INVALIDATED)
                .principal(sanitizeUserId(userId))
                .clientIp(getMdcClientIp())
                .sessionId(sanitizeSessionId(sessionId))
                .outcome("SUCCESS")
                .build();

        log(event);
    }

    /**
     * Создаёт базовый AuditEvent с общими полями из запроса.
     *
     * @param eventType  тип события аудита
     * @param userId    идентификатор пользователя
     * @param sessionId идентификатор сессии (может быть null)
     * @param request   HTTP-запрос
     * @return строитель события аудита
     */
    private AuditEvent.AuditEventBuilder buildAuditEvent(AuditEventType eventType,
                                                          String userId,
                                                          String sessionId,
                                                          HttpServletRequest request) {
        String clientIp = ClientIpResolver.getClientIp(request);
        String userAgent = ClientIpResolver.sanitizeUserAgent(
                ClientIpResolver.getUserAgent(request));

        return AuditEvent.builder()
                .timestamp(Instant.now())
                .correlationId(getCorrelationId())
                .eventType(eventType)
                .principal(sanitizeUserId(userId))
                .clientIp(clientIp)
                .userAgent(userAgent)
                .sessionId(sanitizeSessionId(sessionId));
    }

    /**
     * Получает идентификатор корреляции из MDC, или null, если не установлен.
     *
     * @return идентификатор корреляции из MDC
     */
    private String getCorrelationId() {
        return MDC.get(CORRELATION_ID_MDC_KEY);
    }

    /**
     * Получает IP-адрес клиента из MDC, или null, если не установлен.
     *
     * @return IP-адрес клиента из MDC
     */
    private String getMdcClientIp() {
        return MDC.get(CLIENT_IP_MDC_KEY);
    }

    /**
     * Устанавливает контекст MDC из события аудита для логирования.
     *
     * @param event событие аудита
     */
    private void setMdcContext(AuditEvent event) {
        if (event.getCorrelationId() != null) {
            MDC.put(CORRELATION_ID_MDC_KEY, event.getCorrelationId());
        }
        if (event.getClientIp() != null) {
            MDC.put(CLIENT_IP_MDC_KEY, event.getClientIp());
        }
        if (event.getPrincipal() != null) {
            MDC.put(USER_ID_MDC_KEY, event.getPrincipal());
        }
    }

    /**
     * Очищает контекст MDC после логирования.
     */
    private void clearMdcContext() {
        MDC.remove(CORRELATION_ID_MDC_KEY);
        MDC.remove(CLIENT_IP_MDC_KEY);
        MDC.remove(USER_ID_MDC_KEY);
    }

    /**
     * Очищает имя пользователя для безопасного логирования.
     * При ошибке аутентификации мы никогда не логируем реальное имя пользователя, чтобы избежать
     * предоставления атакующим информации о действительных именах пользователей.
     *
     * @param username сырое имя пользователя
     * @return очищенное имя пользователя
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "unknown";
        }
        // При неудачных попытках аутентификации не раскрываем, существует ли имя пользователя,
        // вообще не логируя его - просто помечаем как попытку
        return "[REDACTED]";
    }

    /**
     * Очищает идентификатор пользователя для безопасного логирования.
     *
     * @param userId сырой идентификатор пользователя
     * @return очищенный идентификатор пользователя
     */
    private String sanitizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "unknown";
        }
        // Обрезаем, если слишком длинный
        if (userId.length() > 100) {
            return userId.substring(0, 100) + "...[truncated]";
        }
        return userId;
    }

    /**
     * Очищает идентификатор сессии для безопасного логирования.
     * Маскирует часть идентификатора сессии для предотвращения перехвата сессии.
     *
     * @param sessionId сырой идентификатор сессии
     * @return очищенный идентификатор сессии
     */
    private String sanitizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        // Маскируем идентификатор сессии, показывая только первые 8 и последние 8 символов
        if (sessionId.length() > 20) {
            return sessionId.substring(0, 8) + "..." + sessionId.substring(sessionId.length() - 8);
        }
        // Для коротких идентификаторов сессий маскируем полностью
        return "[SESSION_ID]";
    }
}
