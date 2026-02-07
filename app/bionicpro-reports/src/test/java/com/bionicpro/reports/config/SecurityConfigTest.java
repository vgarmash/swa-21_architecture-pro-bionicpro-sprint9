package com.bionicpro.reports.config;

import com.bionicpro.reports.repository.ReportRepository;
import com.bionicpro.reports.service.MinioReportService;
import com.bionicpro.reports.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bionicpro.reports.dto.ReportResponse;

/**
 * Модульные тесты для SecurityConfig.
 * Тестирует валидацию JWT токена и конфигурацию безопасности.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private MinioReportService minioReportService;

    @MockBean
    private ReportRepository reportRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Настройка мок-декодера JWT
        when(jwtDecoder.decode(any())).thenReturn(
            Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("user_id", 123L)
                .claim("realm_access.roles", new String[]{"user"})
                .build()
        );
        
        // Мок репозитория для возврата пустого значения для любого запроса
        when(reportRepository.findLatestByUserId(anyLong())).thenReturn(Optional.empty());
        
        // Мок сервиса MinIO когда отключен
        when(minioReportService.reportExists(anyString())).thenReturn(false);
        doNothing().when(minioReportService).storeReport(anyString(), any(ReportResponse.class));
        when(minioReportService.getReport(anyString())).thenReturn(Optional.empty());
        when(minioReportService.getCdnUrl(anyString())).thenReturn("");
        doNothing().when(minioReportService).deleteReport(anyString());
        doNothing().when(minioReportService).deleteUserReports(anyLong());
    }

    @Test
    @DisplayName("test_valid_jwt_token - Request with valid JWT token succeeds")
    void test_valid_jwt_token() throws Exception {
        // Act & Assert - Использование jwt() builder из spring-security-test
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt()
                                .jwt(builder -> builder.subject("123").claim("user_id", 123L))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("test_missing_jwt_token - Request without JWT token returns 401")
    void test_missing_jwt_token() throws Exception {
        // Act & Assert - без заголовка авторизации
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Request with malformed Authorization header returns 401")
    void test_malformed_authorization_header() throws Exception {
        // Act & Assert - неверный заголовок авторизации
        mockMvc.perform(get("/api/v1/reports")
                        .header("Authorization", "NotBearer token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Request with empty Bearer token returns 401")
    void test_empty_bearer_token() throws Exception {
        // Act & Assert - пустой bearer токен
        mockMvc.perform(get("/api/v1/reports")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }
}
