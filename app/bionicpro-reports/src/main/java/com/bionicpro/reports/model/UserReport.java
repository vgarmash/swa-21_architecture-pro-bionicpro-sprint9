package com.bionicpro.reports.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a user report stored in ClickHouse.
 * Maps to the user_reports table in the OLAP database.
 *
 * Schema matches the table created by ETL DAG:
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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReport {

    /**
     * User ID (corresponds to Keycloak user ID)
     */
    private Long userId;

    /**
     * Date of the report
     */
    private LocalDate reportDate;

    /**
     * Total number of EMG signal sessions recorded
     */
    private Integer totalSessions;

    /**
     * Average signal amplitude across all sessions
     */
    private Float avgSignalAmplitude;

    /**
     * Maximum signal amplitude recorded
     */
    private Float maxSignalAmplitude;

    /**
     * Minimum signal amplitude recorded
     */
    private Float minSignalAmplitude;

    /**
     * Average signal frequency in Hz
     */
    private Float avgSignalFrequency;

    /**
     * Total usage time in hours
     */
    private Float totalUsageHours;

    /**
     * Type of prosthesis (e.g., "upper_limb", "lower_limb")
     */
    private String prosthesisType;

    /**
     * Muscle group being monitored (e.g., "biceps", "quadriceps")
     */
    private String muscleGroup;

    /**
     * Customer's full name from CRM
     */
    private String customerName;

    /**
     * Customer's email from CRM
     */
    private String customerEmail;

    /**
     * Customer's age from CRM
     */
    private Integer customerAge;

    /**
     * Customer's gender from CRM
     */
    private String customerGender;

    /**
     * Customer's country from CRM
     */
    private String customerCountry;

    /**
     * Timestamp when the report was created in ClickHouse
     */
    private LocalDateTime createdAt;
}
