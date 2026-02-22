package com.bionicpro.repository;

import com.bionicpro.model.SessionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Duration;

/**
 * Redis реализация SessionRepository.
 * Этот репозиторий использует Redis для хранения сессий.
 */
@Repository("redisSessionRepository")
public class RedisSessionRepository implements SessionRepository {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public RedisSessionRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public void save(String sessionId, SessionData sessionData) {
        String redisKey = "bionicpro:session:" + sessionId;
        redisTemplate.opsForValue().set(redisKey, sessionData, Duration.ofMinutes(30));
    }
    
    @Override
    public Optional<SessionData> findById(String sessionId) {
        String redisKey = "bionicpro:session:" + sessionId;
        Object value = redisTemplate.opsForValue().get(redisKey);
        
        if (value instanceof SessionData) {
            return Optional.of((SessionData) value);
        }
        
        return Optional.empty();
    }
    
    @Override
    public void deleteById(String sessionId) {
        String redisKey = "bionicpro:session:" + sessionId;
        redisTemplate.delete(redisKey);
    }
    
    @Override
    public boolean existsById(String sessionId) {
        String redisKey = "bionicpro:session:" + sessionId;
        return redisTemplate.hasKey(redisKey);
    }
}