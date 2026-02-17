# План разработки Reports API Service

## 1. Обзор сервиса

Reports API Service - это Java/Spring Boot микросервис, который предоставляет REST API для получения отчётов о работе протезов из OLAP-базы ClickHouse.

### 1.1 Основные характеристики

| Характеристика | Значение |
|---------------|----------|
| Язык | Java 17+ |
| Фреймворк | Spring Boot 3.x |
| Порт | 8081 |
| База данных | ClickHouse |
| Аутентификация | Keycloak (OAuth 2.0) |

---

## 2. Структура проекта

```
bionicpro-reports/
├── pom.xml
├── Dockerfile
├── src/
│   └── main/
│       ├── java/com/bionicpro/reports/
│       │   ├── BionicproReportsApplication.java
│       │   ├── config/
│       │   │   ├── SecurityConfig.java
│       │   │   ├── ClickHouseConfig.java
│       │   │   └── KeycloakConfig.java
│       │   ├── controller/
│       │   │   └── ReportController.java
│       │   ├── service/
│       │   │   └── ReportService.java
│       │   ├── repository/
│       │   │   └── ReportRepository.java
│       │   ├── model/
│       │   │   └── UserReport.java
│       │   ├── dto/
│       │   │   └── ReportResponse.java
│       │   └── exception/
│       │       ├── ReportNotFoundException.java
│       │       └── UnauthorizedAccessException.java
│       └── resources/
│           └── application.yml
```

---

## 3. Зависимости (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.1</version>
        <relativePath/>
    </parent>
    
    <groupId>com.bionicpro</groupId>
    <artifactId>bionicpro-reports</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>BionicPRO Reports Service</name>
    
    <properties>
        <java.version>17</java.version>
        <clickhouse.version>0.5.0</clickhouse.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- Spring Security OAuth2 Resource Server -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        
        <!-- Spring Boot Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <!-- ClickHouse JDBC Driver -->
        <dependency>
            <groupId>com.clickhouse</groupId>
            <artifactId>clickhouse-jdbc</artifactId>
            <version>${clickhouse.version}</version>
        </dependency>
        
        <!-- Spring JDBC -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. Конфигурация

### 4.1 application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: bionicpro-reports
  
  datasource:
    url: jdbc:clickhouse://olap_db:8123/default
    driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
    username: default
    password: ""
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
  
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/reports-realm
          jwk-set-uri: http://keycloak:8080/realms/reports-realm/protocol/openid-connect/certs

reports:
  allowed-user-role: prothetic_user
```

### 4.2 SecurityConfig.java

```java
package com.bionicpro.reports.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/reports/**").authenticated()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {}));
        
        return http.build();
    }
}
```

---

## 5. Модели и DTO

### 5.1 UserReport.java (Сущность)

```java
package com.bionicpro.reports.model;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UserReport {
    private Long userId;
    private LocalDate reportDate;
    private Integer totalSessions;
    private Float avgSignalAmplitude;
    private Float maxSignalAmplitude;
    private Float minSignalAmplitude;
    private Float avgSignalFrequency;
    private Float totalUsageHours;
    private String prosthesisType;
    private String muscleGroup;
    private String customerName;
    private String customerEmail;
    private Integer customerAge;
    private String customerGender;
    private String customerCountry;
}
```

### 5.2 ReportResponse.java (DTO ответа)

```java
package com.bionicpro.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private Long userId;
    private String reportDate;
    private Integer totalSessions;
    private Double avgSignalAmplitude;
    private Double maxSignalAmplitude;
    private Double minSignalAmplitude;
    private Double avgSignalFrequency;
    private Double totalUsageHours;
    private String prosthesisType;
    private String muscleGroup;
    private CustomerInfo customerInfo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private String name;
        private String email;
        private Integer age;
        private String gender;
        private String country;
    }
}
```

---

## 6. Репозиторий

### 6.1 ReportRepository.java

```java
package com.bionicpro.reports.repository;

import com.bionicpro.reports.model.UserReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;
    
    private final RowMapper<UserReport> reportRowMapper = (rs, rowNum) -> {
        UserReport report = new UserReport();
        report.setUserId(rs.getLong("user_id"));
        report.setReportDate(rs.getDate("report_date").toLocalDate());
        report.setTotalSessions(rs.getInt("total_sessions"));
        report.setAvgSignalAmplitude(rs.getFloat("avg_signal_amplitude"));
        report.setMaxSignalAmplitude(rs.getFloat("max_signal_amplitude"));
        report.setMinSignalAmplitude(rs.getFloat("min_signal_amplitude"));
        report.setAvgSignalFrequency(rs.getFloat("avg_signal_frequency"));
        report.setTotalUsageHours(rs.getFloat("total_usage_hours"));
        report.setProsthesisType(rs.getString("prosthesis_type"));
        report.setMuscleGroup(rs.getString("muscle_group"));
        report.setCustomerName(rs.getString("customer_name"));
        report.setCustomerEmail(rs.getString("customer_email"));
        report.setCustomerAge(rs.getInt("customer_age"));
        report.setCustomerGender(rs.getString("customer_gender"));
        report.setCustomerCountry(rs.getString("customer_country"));
        return report;
    };

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserReport> findByUserIdAndReportDate(Long userId, LocalDate reportDate) {
        String sql = """
            SELECT * FROM user_reports 
            WHERE user_id = ? AND report_date = ?
            """;
        
        List<UserReport> results = jdbcTemplate.query(
            sql, 
            reportRowMapper, 
            userId, 
            reportDate
        );
        
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<UserReport> findByUserIdOrderByReportDateDesc(Long userId) {
        String sql = """
            SELECT * FROM user_reports 
            WHERE user_id = ?
            ORDER BY report_date DESC
            """;
        
        return jdbcTemplate.query(sql, reportRowMapper, userId);
    }

    public List<UserReport> findLatestByUserId(Long userId, int limit) {
        String sql = """
            SELECT * FROM user_reports 
            WHERE user_id = ?
            ORDER BY report_date DESC
            LIMIT ?
            """;
        
        return jdbcTemplate.query(sql, reportRowMapper, userId, limit);
    }
}
```

---

## 7. Сервис

### 7.1 ReportService.java

```java
package com.bionicpro.reports.service;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.model.UserReport;
import com.bionicpro.reports.repository.ReportRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public ReportResponse getReportByUserId(Long authenticatedUserId, Long requestedUserId) {
        // Проверка: пользователь может получить только свой отчёт
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new UnauthorizedAccessException(
                "You can only access your own report"
            );
        }

        // Получение последнего отчёта
        List<UserReport> reports = reportRepository.findLatestByUserId(authenticatedUserId, 1);
        
        if (reports.isEmpty()) {
            return null; // Или бросить исключение
        }

        return mapToResponse(reports.get(0));
    }

    public List<ReportResponse> getReportsByUserId(Long authenticatedUserId, Long requestedUserId, int limit) {
        // Проверка доступа
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new UnauthorizedAccessException(
                "You can only access your own reports"
            );
        }

        List<UserReport> reports = reportRepository.findLatestByUserId(authenticatedUserId, limit);
        
        return reports.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private ReportResponse mapToResponse(UserReport report) {
        return ReportResponse.builder()
            .userId(report.getUserId())
            .reportDate(report.getReportDate().toString())
            .totalSessions(report.getTotalSessions())
            .avgSignalAmplitude(report.getAvgSignalAmplitude().doubleValue())
            .maxSignalAmplitude(report.getMaxSignalAmplitude().doubleValue())
            .minSignalAmplitude(report.getMinSignalAmplitude().doubleValue())
            .avgSignalFrequency(report.getAvgSignalFrequency().doubleValue())
            .totalUsageHours(report.getTotalUsageHours().doubleValue())
            .prosthesisType(report.getProsthesisType())
            .muscleGroup(report.getMuscleGroup())
            .customerInfo(ReportResponse.CustomerInfo.builder()
                .name(report.getCustomerName())
                .email(report.getCustomerEmail())
                .age(report.getCustomerAge())
                .gender(report.getCustomerGender())
                .country(report.getCustomerCountry())
                .build())
            .build();
    }
}
```

---

## 8. Контроллер

### 8.1 ReportController.java

```java
package com.bionicpro.reports.controller;

import com.bionicpro.reports.dto.ReportResponse;
import com.bionicpro.reports.exception.UnauthorizedAccessException;
import com.bionicpro.reports.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<?> getReport(@AuthenticationPrincipal Jwt jwt) {
        // Получение userId из JWT токена
        Long userId = extractUserId(jwt);
        
        try {
            ReportResponse report = reportService.getReportByUserId(userId, userId);
            
            if (report == null) {
                return ResponseEntity.ok(Map.of(
                    "message", "No report data available",
                    "userId", userId
                ));
            }
            
            return ResponseEntity.ok(report);
            
        } catch (UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "error", "Forbidden",
                    "message", e.getMessage()
                ));
        }
    }

    @GetMapping("/{requestedUserId}")
    public ResponseEntity<?> getReportByUserId(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestedUserId) {
        
        Long authenticatedUserId = extractUserId(jwt);
        
        try {
            ReportResponse report = reportService.getReportByUserId(
                authenticatedUserId, 
                requestedUserId
            );
            
            if (report == null) {
                return ResponseEntity.ok(Map.of(
                    "message", "No report data available",
                    "userId", requestedUserId
                ));
            }
            
            return ResponseEntity.ok(report);
            
        } catch (UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "error", "Forbidden",
                    "message", e.getMessage()
                ));
        }
    }

    private Long extractUserId(Jwt jwt) {
        // Попытка извлечь userId из различных claim
        Object userIdClaim = jwt.getClaim("user_id");
        if (userIdClaim == null) {
            userIdClaim = jwt.getClaim("sub");
        }
        
        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).longValue();
        }
        
        return Long.parseLong(userIdClaim.toString());
    }
}
```

---

## 9. Исключения

### 9.1 UnauthorizedAccessException.java

```java
package com.bionicpro.reports.exception;

public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
```

### 9.2 GlobalExceptionHandler.java

```java
package com.bionicpro.reports.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(
            UnauthorizedAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of(
                "error", "Forbidden",
                "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "error", "Internal Server Error",
                "message", ex.getMessage()
            ));
    }
}
```

---

## 10. Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 11. Docker-compose интеграция

Добавить в docker-compose.yaml:

```yaml
bionicpro-reports:
  build:
    context: ./bionicpro-reports
    dockerfile: Dockerfile
  ports:
    - "8081:8081"
  environment:
    SPRING_DATASOURCE_URL: jdbc:clickhouse://olap_db:8123/default
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://keycloak:8080/realms/reports-realm
  depends_on:
    - olap_db
    - keycloak
  networks:
    - app-network
```

---

## 12. Следующие шаги

Перейдите к документу [04_frontend_integration.md](04_frontend_integration.md) для детального плана интеграции с Frontend.