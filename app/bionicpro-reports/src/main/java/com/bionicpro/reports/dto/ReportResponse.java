package com.bionicpro.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for API responses containing user report information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    /**
     * Unique identifier of the report
     */
    private Long id;

    /**
     * User ID who owns this report
     */
    private String userId;

    /**
     * Report type
     */
    private String reportType;

    /**
     * Report title
     */
    private String title;

    /**
     * Report content/data
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
     * Status of the report
     */
    private String status;
}
