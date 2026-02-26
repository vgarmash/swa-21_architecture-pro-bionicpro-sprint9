package com.bionicpro.config;

import com.bionicpro.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
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
    
    @Value("${oauth2.aes-key:}")
    private String aesKey;
    
    @Value("${oauth2.salt:}")
    private String salt;
    
    @Autowired
    @Qualifier("redisSessionRepository")
    private SessionRepository redisSessionRepository;
    
    @Autowired
    @Qualifier("inMemorySessionRepository")
    private SessionRepository inMemorySessionRepository;
    
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;
    
    /**
     * Бин BytesEncryptor для шифрования токенов в сессиях.
     * Использует ключ и соль из конфигурации oauth2.aes-key и oauth2.salt.
     */
    @Bean
    public BytesEncryptor bytesEncryptor() {
        String key = aesKey;
        String saltValue = salt;
        
        // Если ключи не предоставлены в конфигурации, используем значения по умолчанию для dev
        if (key == null || key.isEmpty()) {
            logger.warn("oauth2.aes-key не настроен, используется значение по умолчанию для разработки");
            key = "0123456789abcdef0123456789abcdef";
        }
        if (saltValue == null || saltValue.isEmpty()) {
            logger.warn("oauth2.salt не настроен, используется значение по умолчанию для разработки");
            saltValue = "0123456789abcdef";
        }
        
        logger.info("Инициализация BytesEncryptor с ключом и солью");
        return Encryptors.standard(key, saltValue);
    }
    
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