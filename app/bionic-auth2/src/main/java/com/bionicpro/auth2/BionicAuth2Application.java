/**
 * Главный класс приложения BionicAuth2.
 * Запускает Spring Boot приложение с конфигурацией OAuth2 Resource Server.
 *
 * @author BionicPRO Team
 * @version 1.0
 */
package com.bionicpro.auth2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Класс для запуска Spring Boot приложения.
 */
@SpringBootApplication
public class BionicAuth2Application {
    
    public static void main(String[] args) {
        SpringApplication.run(BionicAuth2Application.class, args);
    }
}