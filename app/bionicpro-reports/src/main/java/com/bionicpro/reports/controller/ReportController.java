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
 * REST контроллер для API эндпоинтов отчётов.
 * Предоставляет эндпоинты для получения пользовательских отчётов из ClickHouse.
 * 
 * Соответствует спецификации task2/impl/03_reports_api_service.md.
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
     * Получает последний отчёт для аутентифицированного пользователя.
     * 
     * @param jwt JWT токен, содержащий информацию о пользователе
     * @return последний отчёт или сообщение, если данные недоступны
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
     * Получает последний отчёт для конкретного пользователя.
     * Доступно только если аутентифицированный пользователь запрашивает свои собственные отчёты.
     * 
     * @param requestedUserId ID пользователя, для которого нужно получить отчёты
     * @param jwt JWT токен, содержащий информацию о пользователе
     * @return последний отчёт для указанного пользователя
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
     * Получает последние отчёты для конкретного пользователя.
     * Доступно только если аутентифицированный пользователь запрашивает свои собственные отчёты.
     * 
     * @param requestedUserId ID пользователя, для которого нужно получить отчёты
     * @param limit максимальное количество отчётов для возврата (по умолчанию 10)
     * @param jwt JWT токен, содержащий информацию о пользователе
     * @return список последних отчётов для указанного пользователя
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
     * Извлекает ID пользователя из JWT токена.
     * Пробует несколько claims по порядку: user_id, sub.
     * 
     * @param jwt JWT токен
     * @return ID пользователя как Long
     */
    private Long extractUserId(Jwt jwt) {
        // Сначала пробуем claim user_id (пользовательский claim)
        Object userIdClaim = jwt.getClaim("user_id");
        
        if (userIdClaim == null) {
            // Используем резервный вариант - subject claim
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
