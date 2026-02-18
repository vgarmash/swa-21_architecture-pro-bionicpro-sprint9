package com.bionicpro.reports.exception;

/**
 * Exception thrown when a user attempts to access a resource they are not authorized to access.
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
