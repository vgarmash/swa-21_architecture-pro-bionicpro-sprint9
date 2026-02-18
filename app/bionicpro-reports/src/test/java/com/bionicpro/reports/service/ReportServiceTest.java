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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportService.
 * Tests the service layer logic for report operations.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    private UserReport testReport;
    private final String TEST_USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        testReport = UserReport.builder()
                .id(1L)
                .userId(TEST_USER_ID)
                .reportType("daily_summary")
                .title("Daily Summary Report")
                .content("{\"key\": \"value\"}")
                .generatedAt(now)
                .periodStart(now.minusDays(1))
                .periodEnd(now)
                .status("READY")
                .build();
    }

    @Test
    @DisplayName("test_extract_user_id_from_token - User ID is correctly extracted from JWT subject")
    void test_extract_user_id_from_token() {
        // Arrange
        String userId = "user-123";
        when(reportRepository.findByUserId(userId))
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportService.getReportsForUser(userId);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        verify(reportRepository, times(1)).findByUserId(userId);
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
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getReportType()).isEqualTo("daily_summary");
        assertThat(response.getTitle()).isEqualTo("Daily Summary Report");
        assertThat(response.getContent()).isEqualTo("{\"key\": \"value\"}");
        assertThat(response.getStatus()).isEqualTo("READY");
        assertThat(response.getGeneratedAt()).isNotNull();
        assertThat(response.getPeriodStart()).isNotNull();
        assertThat(response.getPeriodEnd()).isNotNull();
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
    @DisplayName("Returns user's report by ID when found")
    void test_get_report_by_id_found() {
        // Arrange
        Long reportId = 1L;
        when(reportRepository.findByIdAndUserId(reportId, TEST_USER_ID))
                .thenReturn(testReport);

        // Act
        ReportResponse result = reportService.getReportById(reportId, TEST_USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(reportId);
        assertThat(result.getTitle()).isEqualTo("Daily Summary Report");
    }

    @Test
    @DisplayName("Returns null when report not found")
    void test_get_report_by_id_not_found() {
        // Arrange
        Long reportId = 999L;
        when(reportRepository.findByIdAndUserId(reportId, TEST_USER_ID))
                .thenReturn(null);
        when(reportRepository.existsById(reportId))
                .thenReturn(false);

        // Act
        ReportResponse result = reportService.getReportById(reportId, TEST_USER_ID);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Throws UnauthorizedAccessException when accessing another user's report")
    void test_get_report_by_id_unauthorized_access() {
        // Arrange
        Long reportId = 1L;
        when(reportRepository.findByIdAndUserId(reportId, TEST_USER_ID))
                .thenReturn(null);
        when(reportRepository.existsById(reportId))
                .thenReturn(true); // Report exists but belongs to another user

        // Act & Assert
        assertThatThrownBy(() -> reportService.getReportById(reportId, TEST_USER_ID))
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
        LocalDateTime now = LocalDateTime.now();
        UserReport report2 = UserReport.builder()
                .id(2L)
                .userId(TEST_USER_ID)
                .reportType("monthly_performance")
                .title("Monthly Performance")
                .content("Content 2")
                .generatedAt(now)
                .periodStart(now.minusMonths(1))
                .periodEnd(now)
                .status("READY")
                .build();

        when(reportRepository.findByUserId(TEST_USER_ID))
                .thenReturn(Arrays.asList(testReport, report2));

        // Act
        List<ReportResponse> result = reportService.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }
}
