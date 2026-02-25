package com.bionicpro.repository;

import com.bionicpro.model.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Фасад для репозитория сессий с поддержкой fallback на InMemorySessionRepository
 * в случае недоступности Redis.
 */
@Component
public class SessionRepositoryFacade implements SessionRepository {

    private static final Logger logger = LoggerFactory.getLogger(SessionRepositoryFacade.class);

    private final SessionRepository primaryRepository;
    private final SessionRepository fallbackRepository;
    private volatile boolean useFallback = false;

    @Autowired
    public SessionRepositoryFacade(
            @Qualifier("redisSessionRepository") SessionRepository primaryRepository,
            @Qualifier("inMemorySessionRepository") SessionRepository fallbackRepository) {
        this.primaryRepository = primaryRepository;
        this.fallbackRepository = fallbackRepository;
    }

    @Override
    public void save(String sessionId, SessionData sessionData) {
        try {
            primaryRepository.save(sessionId, sessionData);
        } catch (Exception e) {
            handleRedisError("save", e);
            fallbackRepository.save(sessionId, sessionData);
        }
    }

    @Override
    public Optional<SessionData> findById(String sessionId) {
        try {
            return primaryRepository.findById(sessionId);
        } catch (Exception e) {
            handleRedisError("findById", e);
            return fallbackRepository.findById(sessionId);
        }
    }

    @Override
    public void deleteById(String sessionId) {
        try {
            primaryRepository.deleteById(sessionId);
        } catch (Exception e) {
            handleRedisError("deleteById", e);
            fallbackRepository.deleteById(sessionId);
        }
    }

    @Override
    public boolean existsById(String sessionId) {
        try {
            return primaryRepository.existsById(sessionId);
        } catch (Exception e) {
            handleRedisError("existsById", e);
            return fallbackRepository.existsById(sessionId);
        }
    }

    private void handleRedisError(String operation, Exception e) {
        if (!useFallback) {
            logger.warn("Redis error during {}: {}. Switching to in-memory fallback.",
                    operation, e.getMessage());
            useFallback = true;
        }
    }
}