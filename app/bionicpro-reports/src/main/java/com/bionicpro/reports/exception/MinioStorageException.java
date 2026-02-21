package com.bionicpro.reports.exception;

/**
 * Exception thrown when MinIO storage operations fail.
 * Wraps underlying MinIO SDK exceptions with application-specific context.
 */
public class MinioStorageException extends RuntimeException {

    public MinioStorageException(String message) {
        super(message);
    }

    public MinioStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
