package com.bionicpro.reports.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Конфигурация подключения к базе данных ClickHouse.
 */
@Configuration
public class ClickHouseConfig {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseConfig.class);

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:2000}")
    private long connectionTimeout;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * Создает HikariCP DataSource для пула подключений ClickHouse.
     */
    @Bean
    public DataSource clickHouseDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setDriverClassName(driverClassName);

        log.info("Creating HikariDataSource with parameters: jdbcUrl={}, username={}, maximumPoolSize={}, minimumIdle={}, connectionTimeout={}, driverClassName={}",
                jdbcUrl, username, maximumPoolSize, minimumIdle, connectionTimeout, driverClassName);

        return dataSource;
    }

    /**
     * Создает JdbcTemplate для операций с ClickHouse.
     */
    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }
}
