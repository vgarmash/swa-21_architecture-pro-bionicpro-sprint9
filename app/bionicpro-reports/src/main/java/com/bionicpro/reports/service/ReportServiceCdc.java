package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.model.UserReport;
import com.bionicpro.reports.repository.ReportRepositoryCdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for CDC report operations with authorization checks.
 * Ensures users can only access their own reports.
 * Works directly with the CDC table without caching (cache-first strategy not needed).
 */
@Service
public class ReportServiceCdc {

    private static final Logger logger = LoggerFactory.getLogger(ReportServiceCdc.class);

    private final ReportRepositoryCdc reportRepositoryCdc;

    public ReportServiceCdc(ReportRepositoryCdc reportRepositoryCdc) {
        this.reportRepositoryCdc = reportRepositoryCdc;
    }

    /**
     * Retrieves all reports for the authenticated user from CDC table.
     *
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return list of report responses for the user
     */
    public List<ReportResponse> getReportsForUser(Long currentUserId) {
        logger.debug("Fetching CDC reports for user: {}", currentUserId);

        List<UserReport> reports = reportRepositoryCdc.findByUserId(currentUserId);

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific report by user ID and report date from CDC table.
     * Validates that the user has access to the requested report.
     *
     * @param requestedUserId the user ID from the request
     * @param reportDate the report date
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return the report response or null if not found
     * @throws UnauthorizedAccessException if user tries to access another user's report
     */
    public ReportResponse getReportByUserIdAndDate(Long requestedUserId, LocalDate reportDate, Long currentUserId) {
        logger.debug("Fetching CDC report for user {} on date {} (authenticated user: {})",
                requestedUserId, reportDate, currentUserId);

        // Authorization check: users can only access their own reports
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access CDC report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Query CDC database directly (no caching)
        Optional<UserReport> report = reportRepositoryCdc.findByUserIdAndReportDate(requestedUserId, reportDate);

        return report.map(this::mapToResponse).orElse(null);
    }

    /**
     * Retrieves the latest report for the authenticated user from CDC table.
     *
     * @param requestedUserId the user ID from the request
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return the report response or null if not found
     * @throws UnauthorizedAccessException if user tries to access another user's report
     */
    public ReportResponse getLatestReport(Long requestedUserId, Long currentUserId) {
        logger.debug("Fetching latest CDC report for user {} (authenticated user: {})",
                requestedUserId, currentUserId);

        // Authorization check: users can only access their own reports
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access CDC report for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }

        // Query CDC database directly (no caching)
        Optional<UserReport> report = reportRepositoryCdc.findLatestByUserId(requestedUserId);

        return report.map(this::mapToResponse).orElse(null);
    }

    /**
     * Retrieves a limited number of recent reports for the authenticated user from CDC table.
     *
     * @param requestedUserId the user ID from the request
     * @param currentUserId the ID of the authenticated user from JWT token
     * @param limit maximum number of reports to return
     * @return list of report responses
     * @throws UnauthorizedAccessException if user tries to access another user's report
     */
    public List<ReportResponse> getRecentReports(Long requestedUserId, Long currentUserId, int limit) {
        logger.debug("Fetching {} recent CDC reports for user {} (authenticated user: {})",
                limit, requestedUserId, currentUserId);

        // Authorization check: users can only access their own reports
        if (!currentUserId.equals(requestedUserId)) {
            logger.warn("User {} attempted to access CDC reports for user {}", currentUserId, requestedUserId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access these reports");
        }

        List<UserReport> reports = reportRepositoryCdc.findLatestByUserId(requestedUserId, limit);

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all reports from the CDC table (admin functionality).
     * Should be protected by role-based access control.
     *
     * @return list of all report responses
     */
    public List<ReportResponse> getAllReports() {
        logger.debug("Fetching all CDC reports");

        List<UserReport> reports = reportRepositoryCdc.findAll();

        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Maps UserReport entity to ReportResponse DTO.
     * Converts Float values to Double for API consistency.
     */
    private ReportResponse mapToResponse(UserReport report) {
        return ReportResponse.builder()
                .userId(report.getUserId())
                .reportDate(report.getReportDate() != null ? report.getReportDate().toString() : null)
                .totalSessions(report.getTotalSessions())
                .avgSignalAmplitude(report.getAvgSignalAmplitude() != null ? 
                        report.getAvgSignalAmplitude().doubleValue() : null)
                .maxSignalAmplitude(report.getMaxSignalAmplitude() != null ? 
                        report.getMaxSignalAmplitude().doubleValue() : null)
                .minSignalAmplitude(report.getMinSignalAmplitude() != null ? 
                        report.getMinSignalAmplitude().doubleValue() : null)
                .avgSignalFrequency(report.getAvgSignalFrequency() != null ? 
                        report.getAvgSignalFrequency().doubleValue() : null)
                .totalUsageHours(report.getTotalUsageHours() != null ? 
                        report.getTotalUsageHours().doubleValue() : null)
                .prosthesisType(report.getProsthesisType())
                .muscleGroup(report.getMuscleGroup())
                .customerInfo(ReportResponse.CustomerInfo.builder()
                        .name(report.getCustomerName())
                        .email(report.getCustomerEmail())
                        .age(report.getCustomerAge())
                        .gender(report.getCustomerGender())
                        .country(report.getCustomerCountry())
                        .build())
                .build();
    }
}