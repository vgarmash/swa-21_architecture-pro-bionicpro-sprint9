package com.bionicpro.reports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главная точка входа для API сервиса отчетов BionicPRO.
 * Этот сервис предоставляет REST API эндпоинты для получения отчетов пользователей из ClickHouse.
 */
@SpringBootApplication
public class BionicproReportsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BionicproReportsApplication.class, args);
    }
}
