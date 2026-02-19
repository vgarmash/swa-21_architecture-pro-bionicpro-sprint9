package com.bionicpro.config;

import com.bionicpro.filter.TokenPropagationFilter;
import com.bionicpro.filter.RateLimitFilter;
import com.bionicpro.model.SessionData;
import com.bionicpro.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security configuration for bionicpro-auth BFF service.
 * Configures OAuth2 Login, session management, and custom authentication filters.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final SessionService sessionService;
    private final SecurityContextRepository securityContextRepository;
    private final TokenPropagationFilter tokenPropagationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    @Order(1)
    public SecurityFilterChain authSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/auth/**")
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/callback", "/error").permitAll()
                .requestMatchers("/api/auth/status", "/api/auth/logout", "/api/auth/refresh").authenticated()
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                .clientRegistrationRepository(clientRegistrationRepository)
                .authorizationEndpoint(endpoint -> endpoint
                    .baseUri("/api/auth/login"))
                .redirectionEndpoint(endpoint -> endpoint
                    .baseUri("/api/auth/callback"))
                .successHandler((request, response, authentication) -> {
                    log.info("OAuth2 login successful for user: {}", authentication.getName());
                    response.sendRedirect("/api/auth/status");
                })
                .failureHandler((request, response, exception) -> {
                    log.error("OAuth2 login failed: {}", exception.getMessage());
                    response.sendRedirect("/login?error=auth_failed");
                }))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    log.info("User logged out");
                    response.sendRedirect("/");
                })
                .invalidateHttpSession(true)
                .deleteCookies("BIONICPRO_SESSION"))
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new SessionRotationFilter(sessionService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiProxySecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").authenticated()
                .anyRequest().authenticated())
            .addFilterBefore(tokenPropagationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new SessionRotationFilter(sessionService), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"not_authenticated\",\"message\":\"User is not authenticated\"}");
                }));

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().permitAll())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));

        return http.build();
    }

    /**
     * Filter for session rotation on each authenticated request.
     * Rotates session ID for all authenticated requests to enhance security.
     */
    @Slf4j
    public static class SessionRotationFilter extends OncePerRequestFilter {

        private final SessionService sessionService;

        public SessionRotationFilter(SessionService sessionService) {
            this.sessionService = sessionService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                        FilterChain filterChain) throws ServletException, IOException {
            
            // Rotate session for all authenticated requests
            if (request.getUserPrincipal() != null) {
                String sessionId = getSessionIdFromRequest(request);
                if (sessionId != null) {
                    SessionData newSession = sessionService.rotateSession(sessionId);
                    if (newSession != null) {
                        // Set new cookie with rotated session ID
                        sessionService.setSessionCookieFromFilter(response, newSession.getSessionId());
                        log.debug("Session rotated for user: {}", newSession.getUserId());
                    }
                }
            }
            
            filterChain.doFilter(request, response);
        }
        
        private String getSessionIdFromRequest(HttpServletRequest request) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("BIONICPRO_SESSION".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
            return null;
        }
    }
}
