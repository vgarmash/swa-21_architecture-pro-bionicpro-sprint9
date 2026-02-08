/**
 * Конфигурация CORS для приложения.
 * Настройка разрешений для кросс-доменных запросов.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Конфигурация CORS для обеспечения доступа к API с фронтенда.
 * Позволяет запросы с указанного домена и устанавливает необходимые заголовки.
 */
@Configuration
public class CorsConfig {
    
    /**
     * Создает источник конфигурации CORS.
     * Настраивает разрешения для кросс-доменных запросов.
     *
     * @return источник конфигурации CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Set-Cookie"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}