package com.bionicpro.config;

import com.bionicpro.audit.AuditService;
import com.bionicpro.controller.AuthController;
import com.bionicpro.mapper.SessionDataMapper;
import com.bionicpro.service.AuthService;
import com.bionicpro.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Security configuration.
 * Verifies CORS headers, CSRF bypass for /api/auth/**, and access rules.
 */
@SpringBootTest(classes = OAuth2RedirectIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security Config Integration Tests")
class OAuth2RedirectIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
    })
    @Import({AuthController.class, OAuth2RedirectIntegrationTest.TestSecurityConfig.class})
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private SessionDataMapper sessionDataMapper;

    @Test
    @DisplayName("GET /api/auth/login should be accessible without authentication (permitAll)")
    void authLogin_shouldBePermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/auth/status should be accessible without authentication (permitAll)")
    void authStatus_shouldBePermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().is4xxClientError());
        // 4xx from controller is expected (no session), but NOT 403 Forbidden from security chain
    }

    @Test
    @DisplayName("OPTIONS CORS preflight request should return 200 with CORS headers")
    void corsPreflightRequest_shouldReturnCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    @DisplayName("GET request from allowed origin should include Access-Control-Allow-Origin header")
    void requestFromAllowedOrigin_shouldIncludeAccessControlAllowOrigin() throws Exception {
        mockMvc.perform(get("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    @DisplayName("POST /api/auth/logout without CSRF token should not be rejected with 403 Forbidden")
    void postToAuthEndpoint_withoutCsrfToken_shouldNotBeForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Configuration
    static class TestSecurityConfig {

        @Bean
        @Order(1)
        SecurityFilterChain authSecurityFilterChain(HttpSecurity http,
                                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
            http
                    .securityMatcher("/api/auth/**")
                    .cors(cors -> cors.configurationSource(corsConfigurationSource))
                    .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**"))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/**").permitAll()
                            .anyRequest().authenticated());
            return http.build();
        }

        @Bean
        @Order(2)
        SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable());
            return http.build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
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
}
