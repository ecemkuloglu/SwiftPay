package com.swiftpay.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html; every operation needs the bearer token unless marked otherwise (login). */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI swiftPayOpenApi() {
        return new OpenAPI()
                .info(new Info().title("SwiftPay API").version("v1")
                        .description("Instant money transfers between accounts. Money fields are strings; "
                                + "timestamps are ISO-8601 UTC. See API_SPEC.md."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
