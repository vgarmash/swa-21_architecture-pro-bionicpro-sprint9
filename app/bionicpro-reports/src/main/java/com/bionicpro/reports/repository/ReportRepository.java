package com.bionicpro.reports.repository;

import com.bionicpro.reports.model.UserReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for interacting with the ClickHouse user_reports table.
 * 
 * Table schema (from ETL DAG):
 * - user_id UInt32
 * - report_date Date
 * - total_sessions UInt32
 * - avg_signal_amplitude Float32
 * - max_signal_amplitude Float32
 * - min_signal_amplitude Float32
 * - avg_signal_frequency Float32
 * - total_usage_hours Float32
 * - prosthesis_type String
 * - muscle_group String
 * - customer_name String
 * - customer_email String
 * - customer_age UInt8
 * - customer_gender String
 * - customer_country String
 * - created_at DateTime
 */
@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * RowMapper for mapping ClickHouse result sets to UserReport entities.
     * Column names match the user_reports table schema.
     */
    private final RowMapper<UserReport> reportRowMapper = (rs, rowNum) -> UserReport.builder()
            .userId(rs.getLong("user_id"))
            .reportDate(rs.getDate("report_date") != null ? 
                    rs.getDate("report_date").toLocalDate() : null)
            .totalSessions(rs.getInt("total_sessions"))
            .avgSignalAmplitude(rs.getFloat("avg_signal_amplitude"))
            .maxSignalAmplitude(rs.getFloat("max_signal_amplitude"))
            .minSignalAmplitude(rs.getFloat("min_signal_amplitude"))
            .avgSignalFrequency(rs.getFloat("avg_signal_frequency"))
            .totalUsageHours(rs.getFloat("total_usage_hours"))
            .prosthesisType(rs.getString("prosthesis_type"))
            .muscleGroup(rs.getString("muscle_group"))
            .customerName(rs.getString("customer_name"))
            .customerEmail(rs.getString("customer_email"))
            .customerAge(rs.getInt("customer_age"))
            .customerGender(rs.getString("customer_gender"))
            .customerCountry(rs.getString("customer_country"))
            .createdAt(rs.getTimestamp("created_at") != null ? 
                    rs.getTimestamp("created_at").toLocalDateTime() : null)
            .build();

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * SQL columns for SELECT queries.
     */
    private static final String SELECT_COLUMNS = 
            "user_id, report_date, total_sessions, avg_signal_amplitude, " +
            "max_signal_amplitude, min_signal_amplitude, avg_signal_frequency, " +
            "total_usage_hours, prosthesis_type, muscle_group, " +
            "customer_name, customer_email, customer_age, customer_gender, " +
            "customer_country, created_at";

    /**
     * Retrieves all reports for a specific user.
     *
     * @param userId the user ID (Long to match ClickHouse UInt32)
     * @return list of user reports ordered by report date descending
     */
    public List<UserReport> findByUserId(Long userId) {
        String sql = String.format(
                "SELECT %s FROM user_reports WHERE user_id = ? ORDER BY report_date DESC",
                SELECT_COLUMNS);
        
        return jdbcTemplate.query(sql, reportRowMapper, userId);
    }

    /**
     * Retrieves a specific report by user ID and report date.
     *
     * @param userId the user ID
     * @param reportDate the report date
     * @return Optional containing the report if found
     */
    public Optional<UserReport> findByUserIdAndReportDate(Long userId, LocalDate reportDate) {
        String sql = String.format(
                "SELECT %s FROM user_reports WHERE user_id = ? AND report_date = ?",
                SELECT_COLUMNS);
        
        List<UserReport> results = jdbcTemplate.query(sql, reportRowMapper, userId, reportDate);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Retrieves the latest N reports for a user.
     *
     * @param userId the user ID
     * @param limit maximum number of reports to return
     * @return list of user reports ordered by report date descending
     */
    public List<UserReport> findLatestByUserId(Long userId, int limit) {
        String sql = String.format(
                "SELECT %s FROM user_reports WHERE user_id = ? ORDER BY report_date DESC LIMIT ?",
                SELECT_COLUMNS);
        
        return jdbcTemplate.query(sql, reportRowMapper, userId, limit);
    }

    /**
     * Retrieves the most recent report for a user.
     *
     * @param userId the user ID
     * @return Optional containing the latest report if found
     */
    public Optional<UserReport> findLatestByUserId(Long userId) {
        List<UserReport> results = findLatestByUserId(userId, 1);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Retrieves all reports from the database.
     *
     * @return list of all user reports ordered by report date descending
     */
    public List<UserReport> findAll() {
        String sql = String.format(
                "SELECT %s FROM user_reports ORDER BY report_date DESC",
                SELECT_COLUMNS);
        
        return jdbcTemplate.query(sql, reportRowMapper);
    }

    /**
     * Checks if any reports exist for a user.
     *
     * @param userId the user ID
     * @return true if reports exist, false otherwise
     */
    public boolean existsByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM user_reports WHERE user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }
}
