package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.model.UserReport;
import com.bionicpro.reports.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для ReportService.
 * Тестирует логику сервисного слоя для операций с отчетами.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MinioReportService minioReportService;

    @InjectMocks
    private ReportService reportService;

    private UserReport testReport;
    private final Long TEST_USER_ID = 123L;

    @BeforeEach
    void setUp() {
        // Настройка мока для генерации ключа отчета minio (lenient для тестов, которые его не используют)
        lenient().when(minioReportService.generateLatestReportKey(anyLong()))
            .thenReturn("reports/1/latest.json");

        // Инициализация testReport полными данными, соответствующими ожиданиям тестов
        testReport = UserReport.builder()
            .userId(TEST_USER_ID)
            .reportDate(LocalDate.of(2024, 1, 15))
            .totalSessions(45)
            .avgSignalAmplitude(0.75f)
            .maxSignalAmplitude(1.2f)
            .minSignalAmplitude(0.3f)
            .avgSignalFrequency(150.5f)
            .totalUsageHours(12.5f)
            .prosthesisType("upper_limb")
            .muscleGroup("biceps")
            .customerName("Ivan Ivanov")
            .customerEmail("ivanov@example.com")
            .customerAge(35)
            .customerGender("male")
            .customerCountry("Russia")
            .build();
    }

    @Test
    @DisplayName("test_extract_user_id_from_token - User ID is correctly extracted from JWT subject")
    void test_extract_user_id_from_token() {
        // Arrange
        when(reportRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportService.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getUserId()).isEqualTo(TEST_USER_ID);
        verify(reportRepository, times(1)).findByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("test_report_response_format - Report response contains all required fields")
    void test_report_response_format() {
        // Arrange
        when(reportRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportService.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(1);
        ReportResponse response = result.get(0);

        // Verify all fields are mapped correctly
        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getReportDate()).isEqualTo("2024-01-15");
        assertThat(response.getTotalSessions()).isEqualTo(45);
        // Use tolerance for Float->Double conversion precision
        assertThat(response.getAvgSignalAmplitude()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.getMaxSignalAmplitude()).isCloseTo(1.2, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.getMinSignalAmplitude()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.getAvgSignalFrequency()).isCloseTo(150.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.getTotalUsageHours()).isCloseTo(12.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.getProsthesisType()).isEqualTo("upper_limb");
        assertThat(response.getMuscleGroup()).isEqualTo("biceps");

        // Verify customer info
        assertThat(response.getCustomerInfo()).isNotNull();
        assertThat(response.getCustomerInfo().getName()).isEqualTo("Ivan Ivanov");
        assertThat(response.getCustomerInfo().getEmail()).isEqualTo("ivanov@example.com");
        assertThat(response.getCustomerInfo().getAge()).isEqualTo(35);
        assertThat(response.getCustomerInfo().getGender()).isEqualTo("male");
        assertThat(response.getCustomerInfo().getCountry()).isEqualTo("Russia");
    }

    @Test
    @DisplayName("test_empty_result_from_olap - Service handles empty result from OLAP database")
    void test_empty_result_from_olap() {
        // Arrange
        when(reportRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

        // Act
        List<ReportResponse> result = reportService.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
        verify(reportRepository, times(1)).findByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("Returns latest report for user")
    void test_get_latest_report_found() {
        // Arrange
        when(reportRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(testReport));

        // Act
        ReportResponse result = reportService.getLatestReport(TEST_USER_ID, TEST_USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.getProsthesisType()).isEqualTo("upper_limb");
    }

    @Test
    @DisplayName("Returns null when latest report not found")
    void test_get_latest_report_not_found() {
        // Arrange
        when(reportRepository.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Act
        ReportResponse result = reportService.getLatestReport(TEST_USER_ID, TEST_USER_ID);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Throws UnauthorizedAccessException when accessing another user's report")
    void test_get_latest_report_unauthorized_access() {
        // Act & Assert
        assertThatThrownBy(() -> reportService.getLatestReport(999L, TEST_USER_ID))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You don't have permission to access this report");
    }

    @Test
    @DisplayName("Returns all reports for admin")
    void test_get_all_reports() {
        // Arrange
        when(reportRepository.findAll())
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportService.getAllReports();

        // Assert
        assertThat(result).hasSize(1);
        verify(reportRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Multiple reports are correctly mapped to response DTOs")
    void test_multiple_reports_mapping() {
        // Arrange
        UserReport report2 = UserReport.builder()
                .userId(TEST_USER_ID)
                .reportDate(LocalDate.of(2024, 1, 16))
                .totalSessions(50)
                .avgSignalAmplitude(0.8f)
                .maxSignalAmplitude(1.3f)
                .minSignalAmplitude(0.4f)
                .avgSignalFrequency(155.0f)
                .totalUsageHours(13.0f)
                .prosthesisType("lower_limb")
                .muscleGroup("quadriceps")
                .customerName("Ivan Ivanov")
                .customerEmail("ivanov@example.com")
                .customerAge(35)
                .customerGender("male")
                .customerCountry("Russia")
                .build();

        when(reportRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Arrays.asList(testReport, report2));

        // Act
        List<ReportResponse> result = reportService.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReportDate()).isEqualTo("2024-01-15");
        assertThat(result.get(1).getReportDate()).isEqualTo("2024-01-16");
    }

    @Test
    @DisplayName("Returns recent reports with limit")
    void test_get_recent_reports() {
        // Arrange
        when(reportRepository.findLatestByUserId(TEST_USER_ID, 10))
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportService.getRecentReports(TEST_USER_ID, TEST_USER_ID, 10);

        // Assert
        assertThat(result).hasSize(1);
        verify(reportRepository, times(1)).findLatestByUserId(TEST_USER_ID, 10);
    }

    @Test
    @DisplayName("Throws UnauthorizedAccessException when getting recent reports for another user")
    void test_get_recent_reports_unauthorized_access() {
        // Act & Assert
        assertThatThrownBy(() -> reportService.getRecentReports(999L, TEST_USER_ID, 10))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You don't have permission to access these reports");
    }
}
