package com.bionicpro.repository;

import com.bionicpro.model.SessionData;

import java.util.Optional;

/**
 * Интерфейс репозитория сессий для хранения и получения данных сессии.
 * Этот интерфейс предоставляет методы для операций управления сессиями.
 */
public interface SessionRepository {

    /**
     * Сохраняет данные сессии.
     * @param sessionId ID сессии
     * @param sessionData данные сессии для сохранения
     */
    void save(String sessionId, SessionData sessionData);

    /**
     * Находит данные сессии по ID сессии.
     * @param sessionId ID сессии
     * @return Optional, содержащий данные сессии, если найдены, иначе пустой
     */
    Optional<SessionData> findById(String sessionId);

    /**
     * Удаляет данные сессии по ID сессии.
     * @param sessionId ID сессии
     */
    void deleteById(String sessionId);

    /**
     * Проверяет, существует ли сессия по ID сессии.
     * @param sessionId ID сессии
     * @return true, если сессия существует, иначе false
     */
    boolean existsById(String sessionId);
}