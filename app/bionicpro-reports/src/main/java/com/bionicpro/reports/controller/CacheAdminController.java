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
 * REST контроллер для эндпоинтов администрирования кэша.
 * Предоставляет эндпоинты для инвалидации кэшированных отчётов при обновлении данных.
 * 
 * Безопасность: Все эндпоинты требуют роль ADMIN.
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
     * Инвалидирует все кэшированные отчёты для конкретного пользователя.
     * Вызывается, когда процесс ETL обновляет данные для пользователя.
     * 
     * @param userId ID пользователя, чей кэш должен быть инвалидирован
     * @return сообщение об успехе
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
     * Инвалидирует кэшированные отчёты для нескольких пользователей.
     * Вызывается процессом ETL после пакетного обновления данных.
     * 
     * @param userIds список ID пользователей, чей кэш должен быть инвалидирован
     * @return сводка результатов инвалидации
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
     * Инвалидирует все кэшированные отчёты.
     * Использовать с осторожностью - в основном для тестирования или полного обновления данных.
     * 
     * @return сообщение об успехе
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
     * Проверяет, есть ли кэшированные отчёты у пользователя.
     * 
     * @param userId ID пользователя для проверки
     * @return статус кэша для пользователя
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
