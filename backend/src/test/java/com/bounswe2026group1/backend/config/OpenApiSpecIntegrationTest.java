package com.bounswe2026group1.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that the generated OpenAPI document is well-formed and matches
 * the conformance contract documented in {@link OpenApiConfig}: no global auth,
 * no mapcessKey scheme, no /error path, sane info/servers/tags, and per-operation
 * security only on protected endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSpecIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_isOpenApi3_withMapcessMetadata() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Mapcess API"))
                .andExpect(jsonPath("$.info.version").value("0.1.0"))
                .andExpect(jsonPath("$.info.contact.name").value("BOUN CMPE 354 Group 1"))
                .andExpect(jsonPath("$.servers[*].url",
                        org.hamcrest.Matchers.hasItem("https://api.mapcess.live")))
                .andExpect(jsonPath("$.servers[*].url",
                        org.hamcrest.Matchers.hasItem("http://localhost:8080")));
    }

    @Test
    void apiDocs_dropsMapcessKeyScheme_andHasNoGlobalSecurity() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                // bearerAuth must be present, mapcessKey must be gone (TODO no-op filter).
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.mapcessKey").doesNotExist())
                // No top-level "security" array — security is per-operation only.
                .andExpect(jsonPath("$.security").doesNotExist());
    }

    @Test
    void apiDocs_excludesErrorDispatcher() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./error").doesNotExist());
    }

    @Test
    void apiDocs_authEndpoints_areAnonymous_andProtectedEndpointsRequireBearer() throws Exception {
        MvcResult res = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                // /auth/login does NOT carry a bearerAuth requirement.
                .andExpect(jsonPath("$.paths./auth/login.post.security").doesNotExist())
                // /auth/register does NOT carry a bearerAuth requirement.
                .andExpect(jsonPath("$.paths./auth/register.post.security").doesNotExist())
                // POST /api/reports — protected — must list bearerAuth.
                .andExpect(jsonPath("$.paths./api/reports.post.security[0].bearerAuth").exists())
                // GET /api/reports — public — must NOT list bearerAuth.
                .andExpect(jsonPath("$.paths./api/reports.get.security").doesNotExist())
                .andReturn();

        // Sanity check: the document advertises tags we declared at the root.
        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("\"name\":\"Auth\"");
        assertThat(body).contains("\"name\":\"Reports\"");
        assertThat(body).contains("\"name\":\"Admin\"");
    }

    @Test
    void swaggerUi_servesIndexHtml() throws Exception {
        // Sanity check: redirects to /swagger-ui/index.html and the static UI is reachable.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
