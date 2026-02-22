package com.bionicpro.reports.controller;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.service.ReportServiceCdc;
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
 * REST контроллер для API эндпоинтов отчётов CDC.
 * Предоставляет эндпоинты для получения пользовательских отчётов из таблицы CDC в ClickHouse.
 * 
 * Эндпоинты:
 * - GET /api/v1/reports/cdc - последний отчёт для текущего пользователя из CDC
 * - GET /api/v1/reports/cdc/{requestedUserId} - отчёт для конкретного пользователя из CDC
 * - GET /api/v1/reports/cdc/{requestedUserId}/history - история для конкретного пользователя из CDC
 */
@RestController
@RequestMapping("/api/v1/reports/cdc")
public class ReportControllerCdc {

    private static final Logger logger = LoggerFactory.getLogger(ReportControllerCdc.class);

    private final ReportServiceCdc reportServiceCdc;

    public ReportControllerCdc(ReportServiceCdc reportServiceCdc) {
        this.reportServiceCdc = reportServiceCdc;
    }

    /**
     * GET /api/v1/reports/cdc
     * Получает последний отчёт для аутентифицированного пользователя из таблицы CDC.
     * 
     * @param jwt JWT токен, содержащий информацию о пользователе
     * @return последний отчёт или сообщение, если данные недоступны
     */
    @GetMapping
    public ResponseEntity<?> getReport(@AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        logger.info("Getting latest CDC report for user: {}", userId);
        
        try {
            ReportResponse report = reportServiceCdc.getLatestReport(userId, userId);
            
            if (report == null) {
                return ResponseEntity.ok(Map.of(
                    "message", "No CDC report data available",
                    "userId", userId
                ));
            }
            
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            logger.error("Error retrieving CDC report for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal Server Error", "message", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/reports/cdc/{requestedUserId}
     * Получает последний отчёт для конкретного пользователя из таблицы CDC.
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
        logger.info("User {} requesting CDC report for user: {}", currentUserId, requestedUserId);
        
        try {
            ReportResponse report = reportServiceCdc.getLatestReport(requestedUserId, currentUserId);
            
            if (report == null) {
                return ResponseEntity.ok(Map.of(
                    "message", "No CDC report data available",
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
     * GET /api/v1/reports/cdc/{requestedUserId}/history
     * Получает последние отчёты для конкретного пользователя из таблицы CDC.
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
        logger.info("User {} requesting CDC report history for user: {} (limit: {})", 
                currentUserId, requestedUserId, limit);
        
        try {
            List<ReportResponse> reports = reportServiceCdc.getRecentReports(
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