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
import java.util.Map;

/**
 * REST Controller for Reports API endpoints.
 * Provides endpoints for retrieving user reports from ClickHouse.
 * 
 * Conforms to task2/impl/03_reports_api_service.md specification.
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
     * Retrieves the latest report for the authenticated user.
     * 
     * @param jwt the JWT token containing user information
     * @return the latest report or a message if no data available
     */
    @GetMapping
    public ResponseEntity<?> getReport(@AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        logger.info("Getting latest report for user: {}", userId);
        
        try {
            ReportResponse report = reportService.getLatestReport(userId, userId);
            
            if (report == null) {
                return ResponseEntity.ok(Map.of(
                    "message", "No report data available",
                    "userId", userId
                ));
            }
            
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            logger.error("Error retrieving report for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal Server Error", "message", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/reports/{requestedUserId}
     * Retrieves the latest report for a specific user.
     * Only accessible if the authenticated user is requesting their own reports.
     * 
     * @param requestedUserId the user ID to get reports for
     * @param jwt the JWT token containing user information
     * @return the latest report for the specified user
     */
    @GetMapping("/{requestedUserId}")
    public ResponseEntity<?> getReportByUserId(
            @PathVariable Long requestedUserId,
            @AuthenticationPrincipal Jwt jwt) {
        
        Long currentUserId = extractUserId(jwt);
        logger.info("User {} requesting report for user: {}", currentUserId, requestedUserId);
        
        try {
            ReportResponse report = reportService.getLatestReport(requestedUserId, currentUserId);
            
            if (report == null) {
                return ResponseEntity.ok(Map.of(
                    "message", "No report data available",
                    "userId", requestedUserId
                ));
            }
            
            return ResponseEntity.ok(report);
            
        } catch (com.bionicpro.reports.exception.UnauthorizedAccessException e) {
            logger.warn("Unauthorized access attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/reports/{requestedUserId}/history
     * Retrieves recent reports for a specific user.
     * Only accessible if the authenticated user is requesting their own reports.
     * 
     * @param requestedUserId the user ID to get reports for
     * @param limit maximum number of reports to return (default 10)
     * @param jwt the JWT token containing user information
     * @return list of recent reports for the specified user
     */
    @GetMapping("/{requestedUserId}/history")
    public ResponseEntity<?> getReportHistory(
            @PathVariable Long requestedUserId,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        
        Long currentUserId = extractUserId(jwt);
        logger.info("User {} requesting report history for user: {} (limit: {})", 
                currentUserId, requestedUserId, limit);
        
        try {
            List<ReportResponse> reports = reportService.getRecentReports(
                    requestedUserId, currentUserId, Math.min(limit, 100));
            
            return ResponseEntity.ok(reports);
            
        } catch (com.bionicpro.reports.exception.UnauthorizedAccessException e) {
            logger.warn("Unauthorized access attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", e.getMessage()));
        }
    }

    /**
     * Extracts the user ID from the JWT token.
     * Tries multiple claims in order: user_id, sub.
     * 
     * @param jwt the JWT token
     * @return the user ID as Long
     */
    private Long extractUserId(Jwt jwt) {
        // Try user_id claim first (custom claim)
        Object userIdClaim = jwt.getClaim("user_id");
        
        if (userIdClaim == null) {
            // Fall back to subject claim
            userIdClaim = jwt.getClaim("sub");
        }
        
        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).longValue();
        }
        
        try {
            return Long.parseLong(userIdClaim.toString());
        } catch (NumberFormatException e) {
            logger.error("Failed to parse user ID from JWT: {}", userIdClaim);
            throw new IllegalArgumentException("Invalid user ID in token");
        }
    }
}
