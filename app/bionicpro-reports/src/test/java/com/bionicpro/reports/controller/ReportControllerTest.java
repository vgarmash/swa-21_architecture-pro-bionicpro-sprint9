package com.bionicpro.reports.controller;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
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
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Модульные тесты для ReportController.
 * Тестирует все REST эндпоинты, связанные с получением отчетов.
 * 
 * Соответствует спецификации task2/impl/03_reports_api_service.md.
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
    private MinioReportService minioReportService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private ReportResponse testReport;

    @BeforeEach
    void setUp() {
        // Настройка мок-декодера JWT для возврата валидного JWT для любого токена
        when(jwtDecoder.decode(any())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            Long userId = 123L; // по умолчанию
            
            if (token != null && token.equals("other-token")) {
                userId = 456L;
            }
            
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject(String.valueOf(userId))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .claim("user_id", userId)
                    .claim("preferred_username", "testuser")
                    .claim("email", "test@example.com")
                    .build();
        });

        // Настройка тестового отчета, соответствующего новой схеме
        testReport = ReportResponse.builder()
                .userId(123L)
                .reportDate("2024-01-15")
                .totalSessions(45)
                .avgSignalAmplitude(0.75)
                .maxSignalAmplitude(1.2)
                .minSignalAmplitude(0.3)
                .avgSignalFrequency(150.5)
                .totalUsageHours(12.5)
                .prosthesisType("upper_limb")
                .muscleGroup("biceps")
                .customerInfo(ReportResponse.CustomerInfo.builder()
                        .name("Ivan Ivanov")
                        .email("ivanov@example.com")
                        .age(35)
                        .gender("male")
                        .country("Russia")
                        .build())
                .build();

        // Мок сервиса MinIO когда отключен
        when(minioReportService.reportExists(anyString())).thenReturn(false);
        doNothing().when(minioReportService).storeReport(anyString(), any(ReportResponse.class));
        when(minioReportService.getReport(anyString())).thenReturn(Optional.empty());
        when(minioReportService.getCdnUrl(anyString())).thenReturn("");
        doNothing().when(minioReportService).deleteReport(anyString());
        doNothing().when(minioReportService).deleteUserReports(anyLong());
    }

    @Test
    @DisplayName("test_get_report_success - Successfully retrieves latest report for authenticated user")
    void test_get_report_success() throws Exception {
        // Arrange
        when(reportService.getLatestReport(123L, 123L)).thenReturn(testReport);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt().jwt(builder -> builder
                                .subject("123")
                                .claim("user_id", 123)
                                .claim("preferred_username", "testuser")
                                .claim("email", "test@example.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(123))
                .andExpect(jsonPath("$.reportDate").value("2024-01-15"))
                .andExpect(jsonPath("$.totalSessions").value(45))
                .andExpect(jsonPath("$.avgSignalAmplitude").value(0.75))
                .andExpect(jsonPath("$.prosthesisType").value("upper_limb"))
                .andExpect(jsonPath("$.muscleGroup").value("biceps"))
                .andExpect(jsonPath("$.customerInfo.name").value("Ivan Ivanov"));
    }

    @Test
    @DisplayName("test_get_report_not_found - Returns message when no report data available")
    void test_get_report_not_found() throws Exception {
        // Arrange
        when(reportService.getLatestReport(123L, 123L)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt().jwt(builder -> builder.subject("123"))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No report data available"));
    }

    @Test
    @DisplayName("test_get_report_unauthorized - Returns 401 when no JWT token provided")
    void test_get_report_unauthorized() throws Exception {
        // Act & Assert - JWT токен не предоставлен
        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("test_get_report_forbidden_other_user - Returns 403 when accessing another user's report")
    void test_get_report_forbidden_other_user() throws Exception {
        // Arrange
        when(reportService.getLatestReport(123L, 456L))
                .thenThrow(new UnauthorizedAccessException("You don't have permission to access this report"));

        // Act & Assert - пользователь-456 пытается получить доступ к отчетам пользователя-123
        // Примечание: Spring Security возвращает 403 без тела при недостаточной области видимости
        mockMvc.perform(get("/api/v1/reports/123")
                        .with(jwt().jwt(builder -> builder.subject("456").claim("user_id", 456L))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Returns message when user has no reports")
    void test_get_reports_empty() throws Exception {
        // Arrange
        when(reportService.getLatestReport(123L, 123L)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt().jwt(builder -> builder.subject("123").claim("user_id", 123))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No report data available"));
    }

    @Test
    @DisplayName("User can access their own report by user ID")
    void test_get_report_by_user_id_success() throws Exception {
        // Arrange
        when(reportService.getLatestReport(123L, 123L)).thenReturn(testReport);

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports/123")
                        .with(jwt().jwt(builder -> builder.subject("123").claim("user_id", 123))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(123))
                .andExpect(jsonPath("$.prosthesisType").value("upper_limb"));
    }

    @Test
    @DisplayName("User can get report history")
    void test_get_report_history_success() throws Exception {
        // Arrange
        ReportResponse report2 = ReportResponse.builder()
                .userId(123L)
                .reportDate("2024-01-16")
                .totalSessions(50)
                .avgSignalAmplitude(0.8)
                .maxSignalAmplitude(1.3)
                .minSignalAmplitude(0.4)
                .avgSignalFrequency(155.0)
                .totalUsageHours(13.0)
                .prosthesisType("lower_limb")
                .muscleGroup("quadriceps")
                .customerInfo(ReportResponse.CustomerInfo.builder()
                        .name("Ivan Ivanov")
                        .email("ivanov@example.com")
                        .age(35)
                        .gender("male")
                        .country("Russia")
                        .build())
                .build();

        when(reportService.getRecentReports(eq(123L), eq(123L), anyInt()))
                .thenReturn(Arrays.asList(testReport, report2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/reports/123/history?limit=10")
                        .with(jwt().jwt(builder -> builder.subject("123").claim("user_id", 123))
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].reportDate").value("2024-01-15"))
                .andExpect(jsonPath("$[1].reportDate").value("2024-01-16"));
    }

    @Test
    @DisplayName("Extracts user ID from user_id claim in JWT")
    void test_extract_user_id_from_custom_claim() throws Exception {
        // Arrange
        when(reportService.getLatestReport(123L, 123L)).thenReturn(testReport);

        // Act & Assert - использование claim user_id вместо subject
        mockMvc.perform(get("/api/v1/reports")
                        .with(jwt().jwt(builder -> builder
                                .subject("some-uuid")  // Отличается от user_id
                                .claim("user_id", 123))  // Это должно использоваться
                                .authorities(new SimpleGrantedAuthority("ROLE_prothetic_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(123));
    }
}