package com.bionicpro.reports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the BionicPRO Reports API Service.
 * This service provides REST API endpoints for retrieving user reports from ClickHouse.
 */
@SpringBootApplication
public class BionicproReportsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BionicproReportsApplication.class, args);
    }
}
