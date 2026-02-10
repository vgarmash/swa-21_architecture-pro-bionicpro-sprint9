/**
 * Конфигурация Redis для приложения.
 * Настройка шаблона Redis для работы с данными сессий и кэша.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Конфигурация Redis для хранения данных сессий и кэша.
 * Настройка сериализаторов для корректной работы с JSON данными.
 */
@Configuration
public class RedisConfig {
    
    /**
     * Создает шаблон Redis для работы с данными.
     * Настраивает сериализаторы для ключей и значений.
     *
     * @param connectionFactory фабрика подключений к Redis
     * @return шаблон Redis с настроенными сериализаторами
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        return template;
    }
}