package com.bionicpro.reports.repository;

import com.bionicpro.reports.model.UserReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for interacting with the ClickHouse user_reports table.
 */
@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserReport> userReportRowMapper = (rs, rowNum) -> UserReport.builder()
            .id(rs.getLong("id"))
            .userId(rs.getString("user_id"))
            .reportType(rs.getString("report_type"))
            .title(rs.getString("title"))
            .content(rs.getString("content"))
            .generatedAt(rs.getTimestamp("generated_at") != null ? 
                    rs.getTimestamp("generated_at").toLocalDateTime() : null)
            .periodStart(rs.getTimestamp("period_start") != null ? 
                    rs.getTimestamp("period_start").toLocalDateTime() : null)
            .periodEnd(rs.getTimestamp("period_end") != null ? 
                    rs.getTimestamp("period_end").toLocalDateTime() : null)
            .status(rs.getString("status"))
            .build();

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves all reports for a specific user.
     *
     * @param userId the user ID
     * @return list of user reports
     */
    public List<UserReport> findByUserId(String userId) {
        String sql = "SELECT id, user_id, report_type, title, content, generated_at, " +
                     "period_start, period_end, status " +
                     "FROM user_reports " +
                     "WHERE user_id = ? " +
                     "ORDER BY generated_at DESC";
        
        return jdbcTemplate.query(sql, userReportRowMapper, userId);
    }

    /**
     * Retrieves a specific report by ID and user ID.
     *
     * @param id the report ID
     * @param userId the user ID
     * @return the user report or null if not found
     */
    public UserReport findByIdAndUserId(Long id, String userId) {
        String sql = "SELECT id, user_id, report_type, title, content, generated_at, " +
                     "period_start, period_end, status " +
                     "FROM user_reports " +
                     "WHERE id = ? AND user_id = ?";
        
        List<UserReport> results = jdbcTemplate.query(sql, userReportRowMapper, id, userId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Retrieves all reports from the database.
     *
     * @return list of all user reports
     */
    public List<UserReport> findAll() {
        String sql = "SELECT id, user_id, report_type, title, content, generated_at, " +
                     "period_start, period_end, status " +
                     "FROM user_reports " +
                     "ORDER BY generated_at DESC";
        
        return jdbcTemplate.query(sql, userReportRowMapper);
    }

    /**
     * Retrieves reports by status.
     *
     * @param status the report status
     * @return list of user reports with the specified status
     */
    public List<UserReport> findByStatus(String status) {
        String sql = "SELECT id, user_id, report_type, title, content, generated_at, " +
                     "period_start, period_end, status " +
                     "FROM user_reports " +
                     "WHERE status = ? " +
                     "ORDER BY generated_at DESC";
        
        return jdbcTemplate.query(sql, userReportRowMapper, status);
    }

    /**
     * Checks if a report exists by ID.
     *
     * @param id the report ID
     * @return true if exists, false otherwise
     */
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM user_reports WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }
}
