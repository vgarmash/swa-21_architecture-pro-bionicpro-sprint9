package com.bionicpro.reports.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений для API отчетов.
 * Обрабатывает общие исключения и возвращает соответствующие ответы об ошибках.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Обрабатывает UnauthorizedAccessException - выбрасывается, когда пользователь пытается получить доступ к неавторизованным ресурсам.
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccessException(
            UnauthorizedAccessException ex) {
        
        logger.warn("Unauthorized access attempt: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                ex.getMessage()
        );
    }

    /**
     * Обрабатывает ReportNotFoundException - выбрасывается, когда запрошенный отчет не найден.
     */
    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleReportNotFoundException(
            ReportNotFoundException ex) {
        
        logger.warn("Report not found: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage()
        );
    }

    /**
     * Обрабатывает AccessDeniedException - выбрасывается Spring Security в сценариях отказа в доступе.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            AccessDeniedException ex) {
        
        logger.warn("Access denied: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You don't have permission to access this resource"
        );
    }

    /**
     * Обрабатывает IllegalArgumentException для недопустимых аргументов.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        
        logger.warn("Invalid argument: {}", ex.getMessage());
        
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage()
        );
    }

    /**
     * Обрабатывает все остальные непойманные исключения.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        
        logger.error("Unexpected error occurred", ex);
        
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred"
        );
    }

    /**
     * Формирует стандартизированный ответ об ошибке.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, 
            String error, 
            String message) {
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        
        return ResponseEntity.status(status).body(body);
    }
}
