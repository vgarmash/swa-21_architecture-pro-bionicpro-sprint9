package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.model.UserReport;
import com.bionicpro.reports.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for report operations with authorization checks.
 * Ensures users can only access their own reports.
 */
@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Retrieves all reports for the authenticated user.
     *
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return list of report responses for the user
     */
    public List<ReportResponse> getReportsForUser(String currentUserId) {
        logger.debug("Fetching reports for user: {}", currentUserId);
        
        List<UserReport> reports = reportRepository.findByUserId(currentUserId);
        
        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific report by ID for the authenticated user.
     * Validates that the user has access to the requested report.
     *
     * @param reportId the ID of the requested report
     * @param currentUserId the ID of the authenticated user from JWT token
     * @return the report response
     * @throws UnauthorizedAccessException if user tries to access another user's report
     */
    public ReportResponse getReportById(Long reportId, String currentUserId) {
        logger.debug("Fetching report {} for user: {}", reportId, currentUserId);
        
        UserReport report = reportRepository.findByIdAndUserId(reportId, currentUserId);
        
        if (report == null) {
            // Check if report exists at all to provide appropriate error message
            if (!reportRepository.existsById(reportId)) {
                logger.warn("Report not found: {}", reportId);
                return null;
            }
            // Report exists but belongs to another user - throw authorization error
            logger.warn("User {} attempted to access report {} belonging to another user", 
                    currentUserId, reportId);
            throw new UnauthorizedAccessException(
                    "You don't have permission to access this report");
        }
        
        return mapToResponse(report);
    }

    /**
     * Retrieves all reports from the database (admin functionality).
     * Should be protected by role-based access control.
     *
     * @return list of all report responses
     */
    public List<ReportResponse> getAllReports() {
        logger.debug("Fetching all reports");
        
        List<UserReport> reports = reportRepository.findAll();
        
        return reports.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Maps UserReport entity to ReportResponse DTO.
     */
    private ReportResponse mapToResponse(UserReport report) {
        return ReportResponse.builder()
                .id(report.getId())
                .userId(report.getUserId())
                .reportType(report.getReportType())
                .title(report.getTitle())
                .content(report.getContent())
                .generatedAt(report.getGeneratedAt())
                .periodStart(report.getPeriodStart())
                .periodEnd(report.getPeriodEnd())
                .status(report.getStatus())
                .build();
    }
}
