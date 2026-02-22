package com.bionicpro.reports.repository;

import com.bionicpro.reports.model.UserReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для взаимодействия с таблицей user_reports в ClickHouse.
 * 
 * Схема таблицы (из ETL DAG):
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
     * RowMapper для маппинга результатов ClickHouse в сущности UserReport.
     * Имена колонок соответствуют схеме таблицы user_reports.
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
     * SQL колонки для SELECT запросов.
     */
    private static final String SELECT_COLUMNS = 
            "user_id, report_date, total_sessions, avg_signal_amplitude, " +
            "max_signal_amplitude, min_signal_amplitude, avg_signal_frequency, " +
            "total_usage_hours, prosthesis_type, muscle_group, " +
            "customer_name, customer_email, customer_age, customer_gender, " +
            "customer_country, created_at";

    /**
     * Возвращает все отчеты для указанного пользователя.
     *
     * @param userId ID пользователя (Long для соответствия ClickHouse UInt32)
     * @return список отчетов пользователя, отсортированный по дате отчета по убыванию
     */
    public List<UserReport> findByUserId(Long userId) {
        String sql = String.format(
                "SELECT %s FROM user_reports WHERE user_id = ? ORDER BY report_date DESC",
                SELECT_COLUMNS);
        
        return jdbcTemplate.query(sql, reportRowMapper, userId);
    }

    /**
     * Возвращает конкретный отчет по ID пользователя и дате отчета.
     *
     * @param userId ID пользователя
     * @param reportDate дата отчета
     * @return Optional, содержащий отчет, если он найден
     */
    public Optional<UserReport> findByUserIdAndReportDate(Long userId, LocalDate reportDate) {
        String sql = String.format(
                "SELECT %s FROM user_reports WHERE user_id = ? AND report_date = ?",
                SELECT_COLUMNS);
        
        List<UserReport> results = jdbcTemplate.query(sql, reportRowMapper, userId, reportDate);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Возвращает последние N отчетов для пользователя.
     *
     * @param userId ID пользователя
     * @param limit максимальное количество отчетов для возврата
     * @return список отчетов пользователя, отсортированный по дате отчета по убыванию
     */
    public List<UserReport> findLatestByUserId(Long userId, int limit) {
        String sql = String.format(
                "SELECT %s FROM user_reports WHERE user_id = ? ORDER BY report_date DESC LIMIT ?",
                SELECT_COLUMNS);
        
        return jdbcTemplate.query(sql, reportRowMapper, userId, limit);
    }

    /**
     * Возвращает самый последний отчет для пользователя.
     *
     * @param userId ID пользователя
     * @return Optional, содержащий последний отчет, если он найден
     */
    public Optional<UserReport> findLatestByUserId(Long userId) {
        List<UserReport> results = findLatestByUserId(userId, 1);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Возвращает все отчеты из базы данных.
     *
     * @return список всех отчетов пользователей, отсортированный по дате отчета по убыванию
     */
    public List<UserReport> findAll() {
        String sql = String.format(
                "SELECT %s FROM user_reports ORDER BY report_date DESC",
                SELECT_COLUMNS);
        
        return jdbcTemplate.query(sql, reportRowMapper);
    }

    /**
     * Проверяет, существуют ли отчеты для пользователя.
     *
     * @param userId ID пользователя
     * @return true, если отчеты существуют, false в противном случае
     */
    public boolean existsByUserId(Long userId) {
        String sql = "SELECT COUNT(*) FROM user_reports WHERE user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }
}
