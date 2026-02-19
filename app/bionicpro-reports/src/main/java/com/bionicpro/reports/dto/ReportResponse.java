package com.bionicpro.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for API responses containing user report information.
 * Conforms to task2/impl/03_reports_api_service.md specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    /**
     * User ID who owns this report
     */
    private Long userId;

    /**
     * Date of the report in ISO format (yyyy-MM-dd)
     */
    private String reportDate;

    /**
     * Total number of EMG signal sessions recorded
     */
    private Integer totalSessions;

    /**
     * Average signal amplitude across all sessions
     */
    private Double avgSignalAmplitude;

    /**
     * Maximum signal amplitude recorded
     */
    private Double maxSignalAmplitude;

    /**
     * Minimum signal amplitude recorded
     */
    private Double minSignalAmplitude;

    /**
     * Average signal frequency in Hz
     */
    private Double avgSignalFrequency;

    /**
     * Total usage time in hours
     */
    private Double totalUsageHours;

    /**
     * Type of prosthesis (e.g., "upper_limb", "lower_limb")
     */
    private String prosthesisType;

    /**
     * Muscle group being monitored (e.g., "biceps", "quadriceps")
     */
    private String muscleGroup;

    /**
     * Customer information from CRM
     */
    private CustomerInfo customerInfo;

    /**
     * Nested DTO for customer information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        /**
         * Customer's full name
         */
        private String name;

        /**
         * Customer's email address
         */
        private String email;

        /**
         * Customer's age
         */
        private Integer age;

        /**
         * Customer's gender
         */
        private String gender;

        /**
         * Customer's country
         */
        private String country;
    }
}
