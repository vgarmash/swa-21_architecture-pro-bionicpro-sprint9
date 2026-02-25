package com.bionicpro.config;

import com.bionicpro.repository.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Конфигурационный класс для репозиториев сессий.
 * Настраивает основной репозиторий (Redis) для распределённого хранения сессий.
 */
@Configuration
public class SessionRepositoryConfig {
    
    @Bean
    @Primary
    public SessionRepository customSessionRepository(
            RedisSessionRepository redisSessionRepository) {
        return new SessionRepositoryFacade(redisSessionRepository);
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