package com.bionicpro.config;

import com.bionicpro.repository.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for session repositories.
 * Sets up the primary repository (Redis) for distributed session storage.
 */
@Configuration
public class SessionRepositoryConfig {
    
    @Bean
    public SessionRepository sessionRepository(
            RedisSessionRepository redisSessionRepository) {
        return new SessionRepositoryFacade(redisSessionRepository);
    }
}