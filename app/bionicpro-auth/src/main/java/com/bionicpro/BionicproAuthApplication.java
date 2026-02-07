package com.bionicpro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Главный класс приложения для BFF сервиса bionicpro-auth.
 * Предоставляет безопасную аутентификацию и управление сессиями с использованием OAuth2/OIDC и Keycloak.
 */
@SpringBootApplication
@EnableRedisHttpSession
public class BionicproAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(BionicproAuthApplication.class, args);
    }
}
