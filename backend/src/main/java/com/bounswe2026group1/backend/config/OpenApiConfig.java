package com.bounswe2026group1.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String bearerSchemeName = "bearerAuth";
        final String apiKeySchemeName = "mapcessKey";

        return new OpenAPI()
                .info(new Info()
                        .title("bounswe2026group1 API")
                        .description("REST API documentation for bounswe2026group1 backend")
                        .version("0.0.1"))
                .addSecurityItem(new SecurityRequirement()
                        .addList(bearerSchemeName)
                        .addList(apiKeySchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                                .name(bearerSchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(apiKeySchemeName, new SecurityScheme()
                                .name("Mapcess-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)));
    }
}
