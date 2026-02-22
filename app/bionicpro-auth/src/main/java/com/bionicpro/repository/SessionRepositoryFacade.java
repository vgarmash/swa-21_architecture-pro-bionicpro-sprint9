package com.bionicpro.repository;

import com.bionicpro.model.SessionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Реализация репозитория сессий, использующая Redis как основное хранилище.
 * Эта реализация не использует запасное хранилище в памяти и предназначена для
 * распределённых сред, где все экземпляры должны использовать одни и те же данные сессии.
 */
@Component
public class SessionRepositoryFacade implements SessionRepository {
    
    private final SessionRepository primaryRepository;
    
    public SessionRepositoryFacade(
            @Qualifier("redisSessionRepository") SessionRepository primaryRepository) {
        this.primaryRepository = primaryRepository;
    }
    
    @Override
    public void save(String sessionId, SessionData sessionData) {
        primaryRepository.save(sessionId, sessionData);
    }
    
    @Override
    public Optional<SessionData> findById(String sessionId) {
        return primaryRepository.findById(sessionId);
    }
    
    @Override
    public void deleteById(String sessionId) {
        primaryRepository.deleteById(sessionId);
    }
    
    @Override
    public boolean existsById(String sessionId) {
        return primaryRepository.existsById(sessionId);
    }
}