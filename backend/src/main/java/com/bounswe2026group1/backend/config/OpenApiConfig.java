package com.bounswe2026group1.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Value("${app.server.url:}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Mapcess API")
                        .description("""
                                REST API for **Mapcess**, a crowdsourced accessibility mapping platform.

                                Browse user-submitted accessibility reports (missing ramps, broken
                                elevators, wet floors, construction, etc.), vote on them, and request
                                accessible routes that detour around verified obstacles.

                                Most read endpoints (reports, comments, public SSE, routing) are open
                                to anonymous clients. Endpoints that mutate state or surface
                                user-specific information require a Bearer JWT obtained from
                                `POST /auth/login`. Operations marked with a lock icon need that
                                token; everything else is public.
                                """)
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("BOUN CMPE 354 Group 1")
                                .url("https://github.com/bounswe/bounswe2026group1"))
                        .license(new License()
                                .name("Project Wiki")
                                .url("https://github.com/bounswe/bounswe2026group1/wiki")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Bearer JWT issued by `POST /auth/login`. " +
                                        "Send as `Authorization: Bearer <token>`.")))
                .tags(List.of(
                        new Tag().name("Auth").description("Account registration and login. Issues JWTs used by every authenticated endpoint."),
                        new Tag().name("Users").description("User profiles, contribution stats, and avatar management."),
                        new Tag().name("Reports").description("Accessibility reports — create, browse, edit, vote, and verify."),
                        new Tag().name("Report Follow").description("Subscribe to status changes on a specific report."),
                        new Tag().name("Fix Requests").description("Crowdsourced 'this issue is fixed' submissions and their voting flow."),
                        new Tag().name("Comments").description("Discussion threads attached to reports."),
                        new Tag().name("Media").description("Upload report media to S3 and attach it to reports."),
                        new Tag().name("Object Types").description("Static catalogue of report object types and their issue/measurement schemas."),
                        new Tag().name("Routing").description("Accessible route planning that detours around verified obstacles."),
                        new Tag().name("Notifications").description("Per-user in-app notifications."),
                        new Tag().name("SSE — Public").description("Server-Sent Events stream of report-level changes; no auth required."),
                        new Tag().name("SSE — Notifications").description("Authenticated per-user Server-Sent Events stream for in-app notifications."),
                        new Tag().name("SSE — Token").description("Mints a short-lived cookie token so EventSource handshakes can authenticate without putting the JWT in a URL."),
                        new Tag().name("Admin").description("Administrative operations — user/report/comment moderation and stats. Requires the ADMIN role.")
                ));

        // Always advertise the canonical local and production hosts so "Try it out" works
        // out of the box. APP_SERVER_URL, when set, is added as the first (default) entry —
        // useful behind a reverse proxy that rewrites the public origin.
        if (serverUrl != null && !serverUrl.isBlank()) {
            openAPI.addServersItem(new Server()
                    .url(serverUrl)
                    .description("Configured server (APP_SERVER_URL)"));
        }
        openAPI.addServersItem(new Server()
                .url("https://api.mapcess.live")
                .description("Production"));
        openAPI.addServersItem(new Server()
                .url("http://localhost:8080")
                .description("Local development"));

        return openAPI;
    }
}
