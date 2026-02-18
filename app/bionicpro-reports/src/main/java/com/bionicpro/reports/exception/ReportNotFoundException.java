package com.bionicpro.reports.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested report is not found.
 * Returns HTTP 404 Not Found status when thrown.
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
