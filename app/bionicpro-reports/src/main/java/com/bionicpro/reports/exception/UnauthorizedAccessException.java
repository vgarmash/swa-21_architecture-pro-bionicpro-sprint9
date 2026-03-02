package com.bionicpro.reports.exception;

/**
 * Исключение, выбрасываемое, когда пользователь пытается получить доступ к ресурсу, к которому он не авторизован.
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
