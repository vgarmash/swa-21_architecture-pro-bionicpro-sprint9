package com.bionicpro.reports.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for BionicPRO Reports API.
 * Configures Swagger/OpenAPI documentation metadata.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BionicPRO Reports API")
                        .version("1.0.0")
                        .description("API for managing user reports and CDC data")
                        .contact(new Contact()
                                .name("BionicPRO Team")));
    }
}
