package com.bionicpro.reports.exception;

/**
 * Исключение, выбрасываемое при сбое операций хранилища MinIO.
 * Оборачивает базовые исключения SDK MinIO контекстом приложения.
 */
public class MinioStorageException extends RuntimeException {

    public MinioStorageException(String message) {
        super(message);
    }

    public MinioStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
