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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * Конфигурация безопасности для BFF сервиса bionic-pro-auth.
 * Настраивает OAuth2 Login, управление сессиями и пользовательские фильтры аутентификации.
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

    /**
     * Custom OAuth2 authentication success handler.
     * This handler is called AFTER Spring Security OAuth2 has successfully processed the callback
     * and exchanged the authorization code for tokens. At this point, the authentication
     * object contains all the tokens in its principal's attributes.
     */
    @Bean
    public AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("=== OAuth2 Success Handler START ===");
            log.info("Authentication class: {}", authentication.getClass().getName());
            log.info("Authenticated: {}", authentication.isAuthenticated());
            log.info("User name: {}", authentication.getName());
            
            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2AuthenticationToken oauth2Auth = (OAuth2AuthenticationToken) authentication;
                Map<String, Object> attributes = oauth2Auth.getPrincipal().getAttributes();
                
                log.info("Principal attributes keys: {}", attributes.keySet());
                
                // Extract ID token
                OidcIdToken idToken = null;
                Object idTokenObj = attributes.get("id_token");
                if (idTokenObj instanceof OidcIdToken) {
                    idToken = (OidcIdToken) idTokenObj;
                } else if (attributes.containsKey("id_token")) {
                    String tokenValue = attributes.get("id_token").toString();
                    idToken = new OidcIdToken(
                        tokenValue,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        attributes
                    );
                }
                
                // Extract access token
                OAuth2AccessToken accessToken = null;
                Object accessTokenObj = attributes.get("access_token");
                if (accessTokenObj instanceof OAuth2AccessToken) {
                    accessToken = (OAuth2AccessToken) accessTokenObj;
                } else if (attributes.containsKey("access_token")) {
                    String tokenValue = attributes.get("access_token").toString();
                    accessToken = new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        tokenValue,
                        Instant.now(),
                        Instant.now().plusSeconds(3600)
                    );
                }
                
                // Extract refresh token
                OAuth2RefreshToken refreshToken = null;
                Object refreshTokenObj = attributes.get("refresh_token");
                if (refreshTokenObj instanceof OAuth2RefreshToken) {
                    refreshToken = (OAuth2RefreshToken) refreshTokenObj;
                } else if (attributes.containsKey("refresh_token")) {
                    String tokenValue = attributes.get("refresh_token").toString();
                    refreshToken = new OAuth2RefreshToken(tokenValue, Instant.now(), Instant.now().plusSeconds(86400));
                }
                
                // Create session with tokens
                if (idToken != null && accessToken != null) {
                    sessionService.createSession(request, response, idToken, accessToken, refreshToken);
                    log.info("Session created for user: {}", idToken.getSubject());
                } else {
                    log.error("Failed to extract tokens from OAuth2 authentication!");
                    log.error("idToken present: {}, accessToken present: {}", idToken != null, accessToken != null);
                }
            }
            
            log.info("=== OAuth2 Success Handler END ===");
            response.sendRedirect("/api/auth/status");
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/auth/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
                .successHandler(oauth2AuthenticationSuccessHandler())
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
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // Возвращаем 401 с JSON вместо редиректа для AJAX запросов
                    if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"authenticated\":false}");
                    } else {
                        // Для обычных запросов возвращаем 401 без редиректа
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"authenticated\":false}");
                    }
                }))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new SessionRotationFilter(sessionService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiProxySecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/oauth2/authorization/**").permitAll()
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
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().permitAll())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));

        return http.build();
    }

    /**
     * Фильтр для ротации сессии при каждом аутентифицированном запросе.
     * Выполняет ротацию ID сессии для всех аутентифицированных запросов в целях повышения безопасности.
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
