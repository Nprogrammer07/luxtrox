package com.luxtrox.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI luxtroxOpenApi() {
        return new OpenAPI().info(
                new Info()
                        .title("LUXTROX ALGORITMO - API")
                        .description("Plataforma de trading algoritmico con cashback. "
                                + "Ver docs/domain-model.md en el repo para las reglas de negocio.")
                        .version("v0.1 (Fase 4 - Spring Boot Core)")
                        .contact(new Contact().name("Luxtrox"))
        );
    }
}
