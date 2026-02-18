package com.bionicpro.reports.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a user report stored in ClickHouse.
 * Maps to the user_reports table in the OLAP database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReport {

    /**
     * Unique identifier of the report
     */
    private Long id;

    /**
     * User ID (corresponds to Keycloak user ID)
     */
    private String userId;

    /**
     * Report type (e.g., "daily_summary", "monthly_performance", etc.)
     */
    private String reportType;

    /**
     * Report title
     */
    private String title;

    /**
     * Report content/data in JSON format
     */
    private String content;

    /**
     * Timestamp when the report was generated
     */
    private LocalDateTime generatedAt;

    /**
     * Report period start date
     */
    private LocalDateTime periodStart;

    /**
     * Report period end date
     */
    private LocalDateTime periodEnd;

    /**
     * Status of the report (e.g., "READY", "PENDING", "FAILED")
     */
    private String status;
}
