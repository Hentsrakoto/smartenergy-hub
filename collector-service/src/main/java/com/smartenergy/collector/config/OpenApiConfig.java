package com.smartenergy.collector.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI collectorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SmartEnergy Hub — Collector API")
                .version("1.0")
                .description("Ingestion asynchrone des mesures. HTTP 202 confirme la publication dans Redis Streams, pas la persistance finale."));
    }
}
