package com.smartenergy.storage.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI storageOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SmartEnergy Hub — Storage and consultation API")
                .version("1.0")
                .description("Consultation des mesures Cassandra et des métadonnées et résumés MongoDB."));
    }
}
