package com.bionicpro.reports.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Конфигурация безопасности для API сервиса отчетов.
 * Настраивает OAuth2 Resource Server с проверкой JWT через Keycloak.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/liveness")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/readiness")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/info")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/v3/api-docs/**")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui.html")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/swagger-ui/index.html")).permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher("/api/v1/reports/**")).hasRole("prothetic_user")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Настраивает конвертер аутентификации JWT для извлечения полномочий из токена.
     * Извлекает роли из claim "realm_access.roles" в JWT от Keycloak.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractRoles);
        return converter;
    }

    /**
     * Извлекает роли из JWT токена.
     * Логгирует содержимое токена для отладки.
     */
    private Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        log.debug("JWT Subject: {}", jwt.getSubject());
        log.debug("JWT Claims: {}", jwt.getClaims());
        
        List<GrantedAuthority> authorities = new ArrayList<>();
        
        // Пытаемся извлечь realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            log.debug("realm_access.roles: {}", rolesObj);
            
            if (rolesObj instanceof Collection) {
                Collection<?> roles = (Collection<?>) rolesObj;
                for (Object role : roles) {
                    String roleName = "ROLE_" + role;
                    authorities.add(new SimpleGrantedAuthority(roleName));
                    log.debug("Added authority: {}", roleName);
                }
            }
        } else {
            log.debug("realm_access claim not found in JWT");
        }
        
        log.debug("Total authorities extracted: {}", authorities.size());
        return authorities;
    }

    /**
     * Настраивает CORS для разрешения запросов с localhost:3000.
     * Позволяет обращаться к API напрямую из браузера для тестирования.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
