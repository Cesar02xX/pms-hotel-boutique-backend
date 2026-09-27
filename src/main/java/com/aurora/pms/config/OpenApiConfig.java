package com.aurora.pms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PMS Hotel Boutique API")
                        .description("API REST para el sistema de gestion del Hotel Boutique Aurora")
                        .version("v1"));
    }
}
