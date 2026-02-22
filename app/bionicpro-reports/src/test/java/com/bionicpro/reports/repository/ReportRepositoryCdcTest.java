package com.bionicpro.reports.repository;

import com.bionicpro.reports.model.UserReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Модульные тесты для ReportRepositoryCdc.
 * Тестирует слой репозитория для операций с таблицами CDC.
 */
@ExtendWith(MockitoExtension.class)
class ReportRepositoryCdcTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportRepositoryCdc reportRepositoryCdc;

    private UserReport testReport;
    private final Long TEST_USER_ID = 123L;

    @BeforeEach
    void setUp() {
        // Настройка тестового отчета
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
    @DisplayName("findByUserId returns list of reports for user")
    void test_find_by_user_id() {
        // Arrange
        List<UserReport> expectedReports = Arrays.asList(testReport);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TEST_USER_ID)))
                .thenReturn(expectedReports);

        // Act
        List<UserReport> result = reportRepositoryCdc.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.get(0).getProsthesisType()).isEqualTo("upper_limb");
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("findByUserId returns empty list when no reports found")
    void test_find_by_user_id_empty() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TEST_USER_ID)))
                .thenReturn(Collections.emptyList());

        // Act
        List<UserReport> result = reportRepositoryCdc.findByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("findByUserIdAndReportDate returns report when found")
    void test_find_by_user_id_and_report_date_found() {
        // Arrange
        LocalDate reportDate = LocalDate.of(2024, 1, 15);
        List<UserReport> expectedReports = Arrays.asList(testReport);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TEST_USER_ID), eq(reportDate)))
                .thenReturn(expectedReports);

        // Act
        Optional<UserReport> result = reportRepositoryCdc.findByUserIdAndReportDate(TEST_USER_ID, reportDate);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getReportDate()).isEqualTo(reportDate);
        assertThat(result.get().getTotalSessions()).isEqualTo(45);
    }

    @Test
    @DisplayName("findByUserIdAndReportDate returns empty when not found")
    void test_find_by_user_id_and_report_date_not_found() {
        // Arrange
        LocalDate reportDate = LocalDate.of(2024, 1, 15);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TEST_USER_ID), eq(reportDate)))
                .thenReturn(Collections.emptyList());

        // Act
        Optional<UserReport> result = reportRepositoryCdc.findByUserIdAndReportDate(TEST_USER_ID, reportDate);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findLatestByUserId returns latest report")
    void test_find_latest_by_user_id() {
        // Arrange
        List<UserReport> expectedReports = Arrays.asList(testReport);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TEST_USER_ID), eq(1)))
                .thenReturn(expectedReports);

        // Act
        Optional<UserReport> result = reportRepositoryCdc.findLatestByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("findLatestByUserId returns empty when no reports")
    void test_find_latest_by_user_id_empty() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TEST_USER_ID), eq(1)))
                .thenReturn(Collections.emptyList());

        // Act
        Optional<UserReport> result = reportRepositoryCdc.findLatestByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findLatestByUserId with limit returns correct number of reports")
    void test_find_latest_by_user_id_with_limit() {
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

        List<UserReport> expectedReports = Arrays.asList(report2, testReport);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(TEST_USER_ID), eq(10)))
                .thenReturn(expectedReports);

        // Act
        List<UserReport> result = reportRepositoryCdc.findLatestByUserId(TEST_USER_ID, 10);

        // Assert
        assertThat(result).hasSize(2);
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), eq(TEST_USER_ID), eq(10));
    }

    @Test
    @DisplayName("findAll returns all reports from CDC table")
    void test_find_all() {
        // Arrange
        List<UserReport> expectedReports = Arrays.asList(testReport);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(expectedReports);

        // Act
        List<UserReport> result = reportRepositoryCdc.findAll();

        // Assert
        assertThat(result).hasSize(1);
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class));
    }

    @Test
    @DisplayName("existsByUserId returns true when reports exist")
    void test_exists_by_user_id_true() {
        // Arrange
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TEST_USER_ID)))
                .thenReturn(5);

        // Act
        boolean result = reportRepositoryCdc.existsByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isTrue();
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Integer.class), eq(TEST_USER_ID));
    }

    @Test
    @DisplayName("existsByUserId returns false when no reports")
    void test_exists_by_user_id_false() {
        // Arrange
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TEST_USER_ID)))
                .thenReturn(0);

        // Act
        boolean result = reportRepositoryCdc.existsByUserId(TEST_USER_ID);

        // Assert
        assertThat(result).isFalse();
    }
}