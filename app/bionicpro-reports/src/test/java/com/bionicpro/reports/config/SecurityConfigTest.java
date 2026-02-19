package com.bionicpro.reports.config;

import com.bionicpro.reports.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for SecurityConfig.
 * Tests JWT token validation and security configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ReportRepository reportRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void setUp() {
        // Setup mock JWT decoder
        when(jwtDecoder.decode(anyString())).thenReturn(
            Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("realm_access.roles", new String[]{"user"})
                .build()
        );
    }

    @Test
    @DisplayName("test_valid_jwt_token - Request with valid JWT token succeeds")
    void test_valid_jwt_token() throws Exception {
        // Act & Assert - Using jwt() builder from spring-security-test
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt()
                                .jwt(builder -> builder.subject("user-123"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("test_missing_jwt_token - Request without JWT token returns 401")
    void test_missing_jwt_token() throws Exception {
        // Act & Assert - no authorization header
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Request with malformed Authorization header returns 401")
    void test_malformed_authorization_header() throws Exception {
        // Act & Assert - malformed authorization header
        mockMvc.perform(get("/api/v1/reports")
                        .header("Authorization", "NotBearer token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Request with empty Bearer token returns 401")
    void test_empty_bearer_token() throws Exception {
        // Act & Assert - empty bearer token
        mockMvc.perform(get("/api/v1/reports")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }
}
