package com.bionicpro.reports.config;

import com.bionicpro.reports.service.MinioReportService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestConfig {
    
    @Bean
    @Primary
    public MinioReportService minioReportService() {
        return null; // Return null or a mock to avoid actual MinIO calls
    }
}