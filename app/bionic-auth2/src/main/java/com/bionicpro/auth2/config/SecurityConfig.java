/**
 * Конфигурация безопасности приложения.
 * Настройка Spring Security OAuth2 Resource Server для валидации JWT токенов.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2.config;

import com.bionicpro.auth2.security.SessionManagementFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Конфигурация безопасности для REST API.
 * Использует Spring Security OAuth2 Resource Server для валидации JWT токенов.
 * Настройка CORS, CSRF отключения и правил авторизации.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    /**
     * Фильтр управления сессиями для ротации и обновления токенов
     */
    private final SessionManagementFilter sessionManagementFilter;
    
    /**
     * Конструктор конфигурации безопасности.
     *
     * @param sessionManagementFilter фильтр управления сессиями
     */
    public SecurityConfig(SessionManagementFilter sessionManagementFilter) {
        this.sessionManagementFilter = sessionManagementFilter;
    }
    
    /**
     * Создает цепочку фильтров безопасности.
     * Настраивает OAuth2 Resource Server, CSRF отключение, CORS и правила авторизации.
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
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder -> jwtDecoder)
                )
            )
            .addFilterBefore(sessionManagementFilter, UsernamePasswordAuthenticationFilter.class);
        
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