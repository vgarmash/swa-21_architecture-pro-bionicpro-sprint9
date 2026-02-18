package com.bionicpro.reports.controller;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Reports API endpoints.
 * Provides endpoints for retrieving user reports from ClickHouse.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * GET /api/v1/reports
     * Retrieves all reports for the authenticated user.
     * 
     * @param jwt the JWT token containing user information
     * @return list of user's reports
     */
    @GetMapping
    public ResponseEntity<List<ReportResponse>> getReports(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        logger.info("Getting reports for user: {}", userId);
        
        List<ReportResponse> reports = reportService.getReportsForUser(userId);
        
        return ResponseEntity.ok(reports);
    }

    /**
     * GET /api/v1/reports/{userId}
     * Retrieves all reports for a specific user.
     * Only accessible if the authenticated user is requesting their own reports
     * or has admin privileges.
     * 
     * @param userId the user ID to get reports for
     * @param jwt the JWT token containing user information
     * @return list of reports for the specified user
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<ReportResponse>> getReportsByUserId(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String currentUserId = jwt.getSubject();
        logger.info("User {} requesting reports for user: {}", currentUserId, userId);
        
        // Authorization check: users can only access their own reports
        if (!userId.equals(currentUserId)) {
            logger.warn("User {} attempted to access reports for user {}", currentUserId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<ReportResponse> reports = reportService.getReportsForUser(userId);
        
        return ResponseEntity.ok(reports);
    }

    /**
     * GET /api/v1/reports/{userId}/{reportId}
     * Retrieves a specific report by ID for a user.
     * Only accessible if the authenticated user owns the report or has admin privileges.
     * 
     * @param userId the user ID
     * @param reportId the report ID
     * @param jwt the JWT token containing user information
     * @return the report if found and accessible
     */
    @GetMapping("/{userId}/{reportId}")
    public ResponseEntity<ReportResponse> getReportById(
            @PathVariable String userId,
            @PathVariable Long reportId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String currentUserId = jwt.getSubject();
        logger.info("User {} requesting report {} for user: {}", currentUserId, reportId, userId);
        
        // Authorization check: users can only access their own reports
        if (!userId.equals(currentUserId)) {
            logger.warn("User {} attempted to access report {} for user {}", 
                    currentUserId, reportId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ReportResponse report = reportService.getReportById(reportId, userId);
        
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(report);
    }
}
