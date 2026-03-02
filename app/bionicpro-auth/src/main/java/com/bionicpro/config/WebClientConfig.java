package com.bionicpro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for web clients - RestTemplate for proxy functionality
 */
@Configuration
public class WebClientConfig {

    @Bean("proxyRestTemplate")
    public RestTemplate proxyRestTemplate() {
        return new RestTemplate();
    }
}