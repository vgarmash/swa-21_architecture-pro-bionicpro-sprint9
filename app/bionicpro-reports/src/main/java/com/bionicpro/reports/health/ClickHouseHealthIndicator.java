package com.bionicpro.reports.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Custom health indicator для проверки подключения к ClickHouse.
 *
 * <p>Этот индикатор:
 * <ul>
 *     <li>Выполняет простой запрос к базе данных для проверки соединения</li>
 *     <li>Использует асинхронную проверку с таймаутом</li>
 *     <li>Обрабатывает сетевые ошибки и таймауты корректно</li>
 *     <li>Не влияет на основной поток запросов</li>
 * </ul>
 */
@Component
public class ClickHouseHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ClickHouseHealthIndicator.class);
    
    private static final String HEALTH_CHECK_QUERY = "SELECT 1";
    private static final long HEALTH_CHECK_TIMEOUT_MS = 5000;

    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService healthCheckExecutor;

    /**
     * Создает новый экземпляр health indicator.
     *
     * @param jdbcTemplate JdbcTemplate для выполнения запросов к ClickHouse
     */
    @Autowired
    public ClickHouseHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // Создаем отдельный executor для health check запросов
        this.healthCheckExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "clickhouse-health-check");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public Health health() {
        // Выполняем проверку асинхронно с таймаутом
        CompletableFuture<Health> future = CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Executing ClickHouse health check query: {}", HEALTH_CHECK_QUERY);
                
                // Выполняем простой запрос для проверки соединения
                Integer result = jdbcTemplate.queryForObject(HEALTH_CHECK_QUERY, Integer.class);
                
                if (result != null && result == 1) {
                    logger.debug("ClickHouse health check passed");
                    return Health.up().withDetail("query", HEALTH_CHECK_QUERY).build();
                } else {
                    logger.warn("ClickHouse health check returned unexpected result: {}", result);
                    return Health.down().withDetail("error", "Unexpected query result").build();
                }
            } catch (Exception e) {
                logger.error("ClickHouse health check failed: {}", e.getMessage());
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
                logger.error("ClickHouse health check timed out after {} ms", HEALTH_CHECK_TIMEOUT_MS);
                return Health.down()
                        .withDetail("error", "Timeout")
                        .withDetail("timeoutMs", HEALTH_CHECK_TIMEOUT_MS)
                        .build();
            }
            logger.error("ClickHouse health check exception: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName())
                    .withDetail("message", e.getMessage())
                    .build();
        } catch (Exception e) {
            logger.error("ClickHouse health check exception: {}", e.getMessage());
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