package com.bionicpro.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Интерфейс сервиса аудита аутентификации для логирования событий.
 * Предоставляет методы для записи различных операций, связанных с аутентификацией,
 * в целях безопасности и соответствия требованиям.
 */
public interface AuditService {

    /**
     * Логирует общее событие аудита.
     *
     * @param event событие аудита для логирования
     */
    void log(AuditEvent event);

    /**
     * Логирует событие успешной аутентификации.
     *
     * @param userId    ID пользователя, который прошёл аутентификацию
     * @param sessionId ID сессии, связанной с аутентификацией
     * @param request   HTTP запрос (для извлечения IP и user agent)
     */
    void logAuthenticationSuccess(String userId, String sessionId, HttpServletRequest request);

    /**
     * Логирует событие неуспешной аутентификации.
     *
     * @param username имя пользователя, не прошедшего аутентификацию
     * @param error    сообщение об ошибке или тип ошибки
     * @param request  HTTP запрос (для извлечения IP и user agent)
     */
    void logAuthenticationFailure(String username, String error, HttpServletRequest request);

    /**
     * Логирует событие выхода из системы.
     *
     * @param userId    ID пользователя, вышедшего из системы
     * @param sessionId ID сессии, которая была завершена
     * @param request   HTTP запрос (для извлечения IP и user agent)
     */
    void logLogout(String userId, String sessionId, HttpServletRequest request);

    /**
     * Логирует событие обновления токена.
     *
     * @param userId    ID пользователя, чей токен был обновлён
     * @param sessionId ID сессии, связанной с обновлением
     * @param request   HTTP запрос (для извлечения IP и user agent)
     */
    void logTokenRefresh(String userId, String sessionId, HttpServletRequest request);

    /**
     * Логирует событие создания сессии.
     *
     * @param userId    ID пользователя, для которого была создана сессия
     * @param sessionId вновь созданный ID сессии
     * @param request   HTTP запрос (для извлечения IP и user agent)
     */
    void logSessionCreated(String userId, String sessionId, HttpServletRequest request);

    /**
     * Логирует событие истечения срока действия сессии.
     *
     * @param userId    ID пользователя, чья сессия истекла
     * @param sessionId ID сессии, которая истекла
     */
    void logSessionExpired(String userId, String sessionId);

    /**
     * Логирует событие аннулирования сессии.
     *
     * @param userId    ID пользователя, чья сессия была аннулирована
     * @param sessionId ID сессии, которая была аннулирована
     */
    void logSessionInvalidated(String userId, String sessionId);
}
