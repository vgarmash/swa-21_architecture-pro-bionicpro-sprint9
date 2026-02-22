package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.model.UserReport;
import com.bionicpro.reports.repository.ReportRepositoryCdc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
 * Unit tests for ReportServiceCdc.
 * Tests the service layer logic for CDC report operations.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceCdcTest {

    @Mock
    private ReportRepositoryCdc reportRepositoryCdc;

    @InjectMocks
    private ReportServiceCdc reportServiceCdc;

    private UserReport testReport;
    private final Long TEST_USER_ID = 123L;

    @BeforeEach
    void setUp() {
        // Setup test report
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
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30, 0))
                .build();
    }

    @Test
    @DisplayName("getReportsForUser returns list of reports for user")
    void test_get_reports_for_user() {
        // Arrange
        when(reportRepositoryCdc.findByUserId(TEST_USER_ID))
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportServiceCdc.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getUserId()).isEqualTo(TEST_USER_ID);
        verify(reportRepositoryCdc, times(1)).findByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("getReportsForUser returns empty list when no reports")
    void test_get_reports_for_user_empty() {
        // Arrange
        when(reportRepositoryCdc.findByUserId(TEST_USER_ID))
                .thenReturn(Collections.emptyList());

        // Act
        List<ReportResponse> result = reportServiceCdc.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
        verify(reportRepositoryCdc, times(1)).findByUserId(TEST_USER_ID);
    }

    @Test
    @DisplayName("Report response contains all required fields")
    void test_report_response_format() {
        // Arrange
        when(reportRepositoryCdc.findByUserId(TEST_USER_ID))
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportServiceCdc.getReportsForUser(TEST_USER_ID);

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
    @DisplayName("getLatestReport returns latest report for user")
    void test_get_latest_report_found() {
        // Arrange
        when(reportRepositoryCdc.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(testReport));

        // Act
        ReportResponse result = reportServiceCdc.getLatestReport(TEST_USER_ID, TEST_USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.getProsthesisType()).isEqualTo("upper_limb");
    }

    @Test
    @DisplayName("getLatestReport returns null when not found")
    void test_get_latest_report_not_found() {
        // Arrange
        when(reportRepositoryCdc.findLatestByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Act
        ReportResponse result = reportServiceCdc.getLatestReport(TEST_USER_ID, TEST_USER_ID);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getLatestReport throws UnauthorizedAccessException when accessing another user's report")
    void test_get_latest_report_unauthorized_access() {
        // Act & Assert
        assertThatThrownBy(() -> reportServiceCdc.getLatestReport(999L, TEST_USER_ID))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You don't have permission to access this report");
    }

    @Test
    @DisplayName("getReportByUserIdAndDate returns report when found")
    void test_get_report_by_user_id_and_date_found() {
        // Arrange
        LocalDate reportDate = LocalDate.of(2024, 1, 15);
        when(reportRepositoryCdc.findByUserIdAndReportDate(TEST_USER_ID, reportDate))
                .thenReturn(Optional.of(testReport));

        // Act
        ReportResponse result = reportServiceCdc.getReportByUserIdAndDate(TEST_USER_ID, reportDate, TEST_USER_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getReportDate()).isEqualTo("2024-01-15");
    }

    @Test
    @DisplayName("getReportByUserIdAndDate returns null when not found")
    void test_get_report_by_user_id_and_date_not_found() {
        // Arrange
        LocalDate reportDate = LocalDate.of(2024, 1, 15);
        when(reportRepositoryCdc.findByUserIdAndReportDate(TEST_USER_ID, reportDate))
                .thenReturn(Optional.empty());

        // Act
        ReportResponse result = reportServiceCdc.getReportByUserIdAndDate(TEST_USER_ID, reportDate, TEST_USER_ID);

        // Assert
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getReportByUserIdAndDate throws UnauthorizedAccessException for unauthorized access")
    void test_get_report_by_user_id_and_date_unauthorized() {
        // Act & Assert
        LocalDate reportDate = LocalDate.of(2024, 1, 15);
        assertThatThrownBy(() -> reportServiceCdc.getReportByUserIdAndDate(999L, reportDate, TEST_USER_ID))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You don't have permission to access this report");
    }

    @Test
    @DisplayName("getAllReports returns all reports")
    void test_get_all_reports() {
        // Arrange
        when(reportRepositoryCdc.findAll())
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportServiceCdc.getAllReports();

        // Assert
        assertThat(result).hasSize(1);
        verify(reportRepositoryCdc, times(1)).findAll();
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

        when(reportRepositoryCdc.findByUserId(TEST_USER_ID))
                .thenReturn(Arrays.asList(testReport, report2));

        // Act
        List<ReportResponse> result = reportServiceCdc.getReportsForUser(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReportDate()).isEqualTo("2024-01-15");
        assertThat(result.get(1).getReportDate()).isEqualTo("2024-01-16");
    }

    @Test
    @DisplayName("getRecentReports returns reports with limit")
    void test_get_recent_reports() {
        // Arrange
        when(reportRepositoryCdc.findLatestByUserId(TEST_USER_ID, 10))
                .thenReturn(Arrays.asList(testReport));

        // Act
        List<ReportResponse> result = reportServiceCdc.getRecentReports(TEST_USER_ID, TEST_USER_ID, 10);

        // Assert
        assertThat(result).hasSize(1);
        verify(reportRepositoryCdc, times(1)).findLatestByUserId(TEST_USER_ID, 10);
    }

    @Test
    @DisplayName("getRecentReports throws UnauthorizedAccessException when accessing another user's reports")
    void test_get_recent_reports_unauthorized_access() {
        // Act & Assert
        assertThatThrownBy(() -> reportServiceCdc.getRecentReports(999L, TEST_USER_ID, 10))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You don't have permission to access these reports");
    }
}