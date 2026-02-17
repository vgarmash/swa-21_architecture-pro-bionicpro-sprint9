package com.bionicpro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Main application class for bionicpro-auth BFF service.
 * Provides secure authentication and session management using OAuth2/OIDC with Keycloak.
 */
@SpringBootApplication
@EnableRedisHttpSession
public class BionicproAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(BionicproAuthApplication.class, args);
    }
}
