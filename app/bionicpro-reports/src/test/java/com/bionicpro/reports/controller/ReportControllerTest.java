package com.bionicpro.reports.controller;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.GlobalExceptionHandler;
import com.bionicpro.reports.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ReportController.
 * Tests all REST endpoints related to report retrieval.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // Setup mock JWT decoder to return valid JWT for any token
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            String subject = "user-123"; // default
            
            if (token != null && token.equals("other-token")) {
                subject = "user-456";
            }
            
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject(subject)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .claim("preferred_username", "testuser")
                    .claim("email", "test@example.com")
                    .build();
        });
    }

    @Test
    @DisplayName("test_get_report_success - Successfully retrieves reports for authenticated user")
    void test_get_report_success() throws Exception {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        ReportResponse report = ReportResponse.builder()
                .id(1L)
                .userId("user-123")
                .reportType("daily_summary")
                .title("Daily Summary Report")
                .content("Report content")
                .generatedAt(now)
                .periodStart(now.minusDays(1))
                .periodEnd(now)
                .status("READY")
                .build();

        when(reportService.getReportsForUser("user-123"))
                .thenReturn(Arrays.asList(report));

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject("user-123")
                                        .claim("preferred_username", "testuser")
                                        .claim("email", "test@example.com"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].userId").value("user-123"))
                .andExpect(jsonPath("$[0].reportType").value("daily_summary"))
                .andExpect(jsonPath("$[0].title").value("Daily Summary Report"))
                .andExpect(jsonPath("$[0].status").value("READY"));
    }

    @Test
    @DisplayName("test_get_report_not_found - Returns 404 when report does not exist")
    void test_get_report_not_found() throws Exception {
        // Arrange
        when(reportService.getReportById(999L, "user-123"))
                .thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports/user-123/999")
                        .with(jwt()
                                .jwt(builder -> builder.subject("user-123"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("test_get_report_unauthorized - Returns 401 when no JWT token provided")
    void test_get_report_unauthorized() throws Exception {
        // Act & Assert - no JWT token provided
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("test_get_report_forbidden_other_user - Returns 403 when accessing another user's report")
    void test_get_report_forbidden_other_user() throws Exception {
        // Act & Assert - user-456 trying to access user-123's reports
        mockMvc.perform(get("/api/v1/reports/user-123")
                        .with(jwt().jwt(builder -> builder.subject("user-456"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Returns empty list when user has no reports")
    void test_get_reports_empty() throws Exception {
        // Arrange
        when(reportService.getReportsForUser("user-123"))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt()
                                .jwt(builder -> builder.subject("user-123"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("User can access their own specific report by ID")
    void test_get_report_by_id_success() throws Exception {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        ReportResponse report = ReportResponse.builder()
                .id(1L)
                .userId("user-123")
                .reportType("monthly_performance")
                .title("Monthly Performance Report")
                .content("Performance data")
                .generatedAt(now)
                .periodStart(now.withDayOfMonth(1))
                .periodEnd(now)
                .status("READY")
                .build();

        when(reportService.getReportById(1L, "user-123"))
                .thenReturn(report);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports/user-123/1")
                        .with(jwt()
                                .jwt(builder -> builder.subject("user-123"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Monthly Performance Report"));
    }
}