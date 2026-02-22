package com.bionicpro.reports.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое, когда запрошенный отчет не найден.
 * Возвращает статус HTTP 404 Not Found при выбрасывании.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ReportNotFoundException extends RuntimeException {
    
    public ReportNotFoundException(String message) {
        super(message);
    }
    
    public ReportNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
