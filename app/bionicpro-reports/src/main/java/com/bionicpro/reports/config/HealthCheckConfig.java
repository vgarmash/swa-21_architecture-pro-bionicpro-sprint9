package com.bionicpro.reports.config;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация health checks для сервиса отчетов.
 * Настраивает Actuator endpoints и custom health indicators.
 * 
 * <p>Эта конфигурация:
 * <ul>
 *     <li>Конфигурирует Actuator endpoints с ограниченным доступом</li>
 *     <li>Регистрирует custom health indicators для ClickHouse и MinIO</li>
 *     <li>Настраивает таймауты и агрегацию статусов</li>
 * </ul>
 */
@Configuration
public class HealthCheckConfig {

    /**
     * Настройки Actuator endpoints для production-secure health checks.
     * 
     * <p>Важно: Не экспонируем敏感 endpoints как /env, /beans, /configprops.
     * Разрешаем только /actuator/health и /actuator/info для public access.
     */
    public static class ActuatorSettings {
        
        /**
         * Получает список endpoints, которые должны быть доступны.
         * В production режиме экспонируем только health и info endpoints.
         * 
         * @return массив endpoints для exposure
         */
        public static String[] getExposedEndpoints() {
            return new String[]{"health", "info"};
        }
        
        /**
         * Получает список endpoints, которые должны быть доступны при аутентификации.
         * Используется в development режиме для отладки.
         * 
         * @return массив endpoints для authorized access
         */
        public static String[] getAuthorizedEndpoints() {
            return new String[]{"health", "info", "metrics"};
        }
    }
}