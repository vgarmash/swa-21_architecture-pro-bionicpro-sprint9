package com.bionicpro.config;

import com.bionicpro.repository.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурационный класс для репозиториев сессий.
 * Настраивает основной репозиторий (Redis) для распределённого хранения сессий.
 */
@Configuration
public class SessionRepositoryConfig {
    
    @Bean
    public SessionRepository sessionRepository(
            RedisSessionRepository redisSessionRepository) {
        return new SessionRepositoryFacade(redisSessionRepository);
    }
}