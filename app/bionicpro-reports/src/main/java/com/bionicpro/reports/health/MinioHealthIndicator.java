package com.bionicpro.reports.health;

import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Custom health indicator для проверки доступности MinIO хранилища.
 *
 * <p>Этот индикатор:
 * <ul>
 *     <li>Проверяет возможность подключения к MinIO серверу</li>
 *     <li>Проверяет существениея корзины (bucket)</li>
 *     <li>Использует асинхронную проверку с таймаутом</li>
 *     <li>Обрабатывает все MinIO ошибки корректно</li>
 * </ul>
 */
@Component
public class MinioHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(MinioHealthIndicator.class);
    
    private static final long HEALTH_CHECK_TIMEOUT_MS = 5000;

    private final MinioClient minioClient;
    private final String bucketName;
    private final ExecutorService healthCheckExecutor;

    /**
     * Создает новый экземпляр health indicator.
     *
     * @param minioClient MinIO client для взаимодействия с хранилищем
     * @param bucketName имя корзины для проверки
     */
    @Autowired
    public MinioHealthIndicator(MinioClient minioClient,
                                @org.springframework.beans.factory.annotation.Value("${app.minio.bucket-name:reports}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        // Создаем отдельный executor для health check запросов
        this.healthCheckExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "minio-health-check");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public Health health() {
        // Выполняем проверку асинхронно с таймаутом
        CompletableFuture<Health> future = CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Checking MinIO bucket existence: {}", bucketName);
                
                // Проверяем существование корзины
                boolean bucketExists = minioClient.bucketExists(
                        io.minio.BucketExistsArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                
                if (bucketExists) {
                    logger.debug("MinIO bucket exists: {}", bucketName);
                    return Health.up()
                            .withDetail("bucket", bucketName)
                            .withDetail("status", "connected")
                            .build();
                } else {
                    logger.warn("MinIO bucket does not exist: {}", bucketName);
                    return Health.down()
                            .withDetail("bucket", bucketName)
                            .withDetail("status", "not_found")
                            .build();
                }
            } catch (ErrorResponseException e) {
                logger.error("MinIO health check error response: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", "ErrorResponse")
                        .withDetail("code", e.errorResponse() != null ? e.errorResponse().code() : "unknown")
                        .withDetail("message", e.getMessage())
                        .build();
            } catch (InvalidResponseException e) {
                logger.error("MinIO health check invalid response: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", "InvalidResponse")
                        .withDetail("message", e.getMessage())
                        .build();
            } catch (ServerException e) {
                logger.error("MinIO health check server error: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", "ServerError")
                        .withDetail("message", e.getMessage())
                        .build();
            } catch (XmlParserException e) {
                logger.error("MinIO health check XML parsing error: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", "XmlParserError")
                        .withDetail("message", e.getMessage())
                        .build();
            } catch (Exception e) {
                logger.error("MinIO health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("error", e.getClass().getSimpleName())
                        .withDetail("message", e.getMessage())
                        .build();
            }
        }, healthCheckExecutor);

        try {
            // Ждем результат с таймаутом
            return future.orTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS).get();
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                logger.error("MinIO health check timed out after {} ms", HEALTH_CHECK_TIMEOUT_MS);
                return Health.down()
                        .withDetail("error", "Timeout")
                        .withDetail("timeoutMs", HEALTH_CHECK_TIMEOUT_MS)
                        .build();
            }
            logger.error("MinIO health check exception: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        } catch (Exception e) {
            logger.error("MinIO health check exception: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }

    /**
     * Закрывает executor при shutdown.
     * Вызывается Spring Container при shutdown приложения.
     */
    public void shutdown() {
        healthCheckExecutor.shutdown();
    }
}