package com.bionicpro.reports.controller;

import com.bionicpro.reports.service.MinioReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for cache administration endpoints.
 * Provides endpoints for invalidating cached reports when data is updated.
 * 
 * Security: All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/cache")
public class CacheAdminController {

    private static final Logger logger = LoggerFactory.getLogger(CacheAdminController.class);

    private final MinioReportService minioReportService;

    public CacheAdminController(MinioReportService minioReportService) {
        this.minioReportService = minioReportService;
    }

    /**
     * POST /api/v1/admin/cache/invalidate/user/{userId}
     * Invalidates all cached reports for a specific user.
     * Called when the ETL process updates data for a user.
     * 
     * @param userId the user ID whose cache should be invalidated
     * @return success message
     */
    @PostMapping("/invalidate/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> invalidateUserCache(@PathVariable Long userId) {
        logger.info("Invalidating cache for user: {}", userId);
        
        try {
            minioReportService.deleteUserReports(userId);
            
            logger.info("Cache invalidated successfully for user: {}", userId);
            return ResponseEntity.ok(Map.of(
                "message", "Cache invalidated for user",
                "userId", userId,
                "success", true
            ));
        } catch (Exception e) {
            logger.error("Failed to invalidate cache for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "message", "Failed to invalidate cache",
                "userId", userId,
                "error", e.getMessage(),
                "success", false
            ));
        }
    }

    /**
     * POST /api/v1/admin/cache/invalidate/users
     * Invalidates cached reports for multiple users.
     * Called by the ETL process after batch data updates.
     * 
     * @param userIds list of user IDs whose cache should be invalidated
     * @return summary of invalidation results
     */
    @PostMapping("/invalidate/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> invalidateMultipleUserCache(@RequestBody List<Long> userIds) {
        logger.info("Invalidating cache for {} users", userIds.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (Long userId : userIds) {
            try {
                minioReportService.deleteUserReports(userId);
                successCount++;
            } catch (Exception e) {
                logger.warn("Failed to invalidate cache for user {}: {}", userId, e.getMessage());
                failureCount++;
            }
        }
        
        logger.info("Cache invalidation complete: {} successful, {} failed", successCount, failureCount);
        
        return ResponseEntity.ok(Map.of(
            "message", "Cache invalidation complete",
            "totalUsers", userIds.size(),
            "successCount", successCount,
            "failureCount", failureCount,
            "success", failureCount == 0
        ));
    }

    /**
     * POST /api/v1/admin/cache/invalidate/all
     * Invalidates all cached reports.
     * Use with caution - primarily for testing or major data refreshes.
     * 
     * @return success message
     */
    @PostMapping("/invalidate/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> invalidateAllCache() {
        logger.warn("Invalidating ALL cached reports - this may impact performance");
        
        try {
            // Note: This would require implementing a deleteAllReports method
            // For now, log a warning and return a message
            logger.warn("Full cache invalidation requested - implementing deleteAllReports is recommended");
            
            return ResponseEntity.ok(Map.of(
                "message", "Full cache invalidation not implemented - use per-user invalidation",
                "success", false
            ));
        } catch (Exception e) {
            logger.error("Failed to invalidate all cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "message", "Failed to invalidate all cache",
                "error", e.getMessage(),
                "success", false
            ));
        }
    }

    /**
     * GET /api/v1/admin/cache/status/{userId}
     * Checks if a user has cached reports.
     * 
     * @param userId the user ID to check
     * @return cache status for the user
     */
    @GetMapping("/status/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getCacheStatus(@PathVariable Long userId) {
        logger.debug("Checking cache status for user: {}", userId);
        
        String latestKey = minioReportService.generateLatestReportKey(userId);
        boolean hasLatestReport = minioReportService.reportExists(latestKey);
        
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "hasCachedLatestReport", hasLatestReport,
            "latestReportKey", latestKey
        ));
    }
}
