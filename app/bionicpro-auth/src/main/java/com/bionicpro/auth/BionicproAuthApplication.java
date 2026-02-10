/**
 * Основной класс приложения для сервиса аутентификации BionicPRO.
 * Этот класс запускает Spring Boot приложение и инициализирует
 * необходимые компоненты для работы сессий через Redis.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableRedisHttpSession
public class BionicproAuthApplication {
    /**
     * Точка входа в приложение.
     * Запускает Spring Boot приложение с указанными аргументами.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(BionicproAuthApplication.class, args);
    }
}