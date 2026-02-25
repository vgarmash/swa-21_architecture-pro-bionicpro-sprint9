package com.bionicpro.config;

import com.bionicpro.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Конфигурационный класс для репозиториев сессий.
 * Настраивает основной репозиторий (Redis) для распределённого хранения сессий
 * с fallback на InMemorySessionRepository при недоступности Redis.
 */
@Configuration
public class SessionRepositoryConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionRepositoryConfig.class);
    
    @Autowired
    @Qualifier("redisSessionRepository")
    private SessionRepository redisSessionRepository;
    
    @Autowired
    @Qualifier("inMemorySessionRepository")
    private SessionRepository inMemorySessionRepository;
    
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;
    
    @Bean
    @Primary
    public SessionRepository customSessionRepository() {
        try {
            // Используем PING для проверки доступности Redis без побочных эффектов
            RedisConnection connection = redisConnectionFactory.getConnection();
            try {
                String pong = connection.ping();
                logger.info("Redis available (PING={}) - using RedisSessionRepository", pong);
                return redisSessionRepository;
            } finally {
                connection.close();
            }
        } catch (Exception e) {
            logger.warn("Redis unavailable - falling back to InMemorySessionRepository: {}", e.getMessage());
            return inMemorySessionRepository;
        }
    }

    /**
     * Бин SecurityContextRepository для Spring Security.
     * Использует HttpSession для хранения контекста безопасности.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}