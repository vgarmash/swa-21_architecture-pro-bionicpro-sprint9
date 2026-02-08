/**
 * Конфигурация безопасности приложения.
 * Настройка фильтров безопасности, CORS и авторизации.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Конфигурация безопасности для REST API.
 * Включает настройку CORS, CSRF отключения, авторизации и фильтров сессий.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    /**
     * Фильтр проверки сессии
     */
    private final SessionValidationFilter sessionValidationFilter;
    
    /**
     * Конструктор конфигурации безопасности.
     *
     * @param sessionValidationFilter фильтр проверки сессии
     */
    public SecurityConfig(SessionValidationFilter sessionValidationFilter) {
        this.sessionValidationFilter = sessionValidationFilter;
    }
    
    /**
     * Создает цепочку фильтров безопасности.
     * Настраивает CSRF отключение, CORS и правила авторизации.
     *
     * @param http объект HttpSecurity для настройки
     * @return цепочка фильтров безопасности
     * @throws Exception если возникает ошибка при настройке
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(sessionValidationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
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
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}