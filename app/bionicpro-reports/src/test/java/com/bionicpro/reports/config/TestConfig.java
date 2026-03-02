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
        return null; // Возвращаем null или мок для избежания реальных вызовов MinIO
    }
}