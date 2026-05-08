package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-context integration test for the routing-preferences API.
 *
 * Boots the real Spring container, talks to the H2 PostgreSQL-mode test DB,
 * and round-trips actual {@link RegisteredUser} rows. Auth is mocked via
 * {@code with(user(...))} so we don't need a real JWT. The user record itself
 * is persisted before each test so the controller's {@code findByEmail} lookup
 * succeeds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoutingPreferencesIntegrationTest {

    private static final String EMAIL = "prefs-test@test.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private RegisteredUserRepository registeredUserRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        registeredUserRepository.findByEmail(EMAIL).ifPresent(registeredUserRepository::delete);
        RegisteredUser u = new RegisteredUser();
        u.setName("Prefs Tester");
        u.setEmail(EMAIL);
        u.setPassword("hashed-password-not-used-in-tests");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setRoutingConstraints(new HashSet<>());
        u.setPreferredPreset(RoutingPreset.NONE);
        registeredUserRepository.save(u);
    }

    // ── Anonymous → 401 ─────────────────────────────────────────────────────

    @Test
    void get_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/users/me/routing-preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void put_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredPreset\":\"WHEELCHAIR_USER\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET on a fresh user ─────────────────────────────────────────────────

    @Test
    void get_freshUser_returnsNonePresetAndFullCatalog() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("NONE"))
                .andExpect(jsonPath("$.constraints").isArray())
                .andExpect(jsonPath("$.constraints").isEmpty())
                .andExpect(jsonPath("$.preferredTravelMode").doesNotExist())
                .andExpect(jsonPath("$.availablePresets").isArray())
                .andExpect(jsonPath("$.availableConstraints").isArray())
                .andReturn();

        // Catalog must list every preset and every constraint, with non-empty labels/descriptions.
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("availablePresets")).hasSize(RoutingPreset.values().length);
        assertThat(body.get("availableConstraints")).hasSize(RoutingConstraint.values().length);
        for (JsonNode entry : body.get("availableConstraints")) {
            assertThat(entry.get("label").asString()).isNotBlank();
            assertThat(entry.get("description").asString()).isNotBlank();
        }
    }

    // ── PUT preset only → constraints + travel mode auto-fill ───────────────

    @Test
    void put_presetOnly_fillsConstraintsAndTravelMode_andPersists() throws Exception {
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("preferredPreset", "WHEELCHAIR_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("WHEELCHAIR_USER"))
                .andExpect(jsonPath("$.preferredTravelMode").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.constraints", hasSize(
                        RoutingPreset.WHEELCHAIR_USER.getConstraints().size())));

        // DB persisted the change — round-trip verification.
        RegisteredUser stored = registeredUserRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getPreferredPreset()).isEqualTo(RoutingPreset.WHEELCHAIR_USER);
        assertThat(stored.getRoutingConstraints())
                .containsExactlyInAnyOrderElementsOf(RoutingPreset.WHEELCHAIR_USER.getConstraints());
        assertThat(stored.getPreferredTravelMode()).isEqualTo(TravelMode.WHEELCHAIR);
    }

    // ── PUT constraints matching a preset → auto-detect ─────────────────────

    @Test
    void put_constraintsMatchingPreset_autoDetectsThePreset() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "constraints", RoutingPreset.MOBILITY_LIMITED.getConstraints().stream()
                        .map(Enum::name).toList()));

        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("MOBILITY_LIMITED"));
    }

    // ── PUT arbitrary constraints → CUSTOM ──────────────────────────────────

    @Test
    void put_arbitraryConstraints_setsPresetCustom() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "constraints", java.util.List.of("AVOID_LOW_CLEARANCE", "REQUIRE_HANDRAILS")));

        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("CUSTOM"))
                .andExpect(jsonPath("$.constraints", hasSize(2)));
    }

    // ── PUT both preset + constraints → constraints win ─────────────────────

    @Test
    void put_bothPresetAndArbitraryConstraints_constraintsWin_presetBecomesCustom() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "preferredPreset", "WHEELCHAIR_USER",
                "constraints", java.util.List.of("AVOID_LOW_CLEARANCE")));

        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("CUSTOM"))
                .andExpect(jsonPath("$.constraints", hasSize(1)))
                .andExpect(jsonPath("$.constraints[0]").value("AVOID_LOW_CLEARANCE"));
    }

    // ── PUT then GET → DB round-trip ────────────────────────────────────────

    @Test
    void putThenGet_roundTripsThroughTheDatabase() throws Exception {
        // Set BLIND_OR_LOW_VISION via PUT
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("preferredPreset", "BLIND_OR_LOW_VISION"))))
                .andExpect(status().isOk());

        // GET the same user — values come back identical
        mockMvc.perform(get("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("BLIND_OR_LOW_VISION"))
                .andExpect(jsonPath("$.preferredTravelMode").value("WALKING"))
                .andExpect(jsonPath("$.constraints", hasSize(
                        RoutingPreset.BLIND_OR_LOW_VISION.getConstraints().size())));
    }

    // ── Empty body → no-op ──────────────────────────────────────────────────

    @Test
    void put_emptyBody_isNoOp_returnsCurrentState() throws Exception {
        // Seed: user picked WHEELCHAIR_USER previously.
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("preferredPreset", "WHEELCHAIR_USER"))))
                .andExpect(status().isOk());

        // PUT empty body — should not clobber.
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("WHEELCHAIR_USER"))
                .andExpect(jsonPath("$.preferredTravelMode").value("WHEELCHAIR"));
    }

    // ── Toggle workflow (ergonomics) ────────────────────────────────────────

    @Test
    void toggleWorkflow_pickPresetEditConstraintRePickPresetSnapsBack() throws Exception {
        // 1. Pick WHEELCHAIR_USER preset
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("preferredPreset", "WHEELCHAIR_USER"))))
                .andExpect(status().isOk());

        // 2. Drop one constraint — preset becomes CUSTOM
        java.util.List<String> droppedOne = RoutingPreset.WHEELCHAIR_USER.getConstraints().stream()
                .skip(1)
                .map(Enum::name)
                .toList();
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("constraints", droppedOne))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("CUSTOM"));

        // 3. Re-add the dropped constraint — preset auto-snaps back to WHEELCHAIR_USER
        java.util.List<String> fullSet = RoutingPreset.WHEELCHAIR_USER.getConstraints().stream()
                .map(Enum::name)
                .toList();
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("constraints", fullSet))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("WHEELCHAIR_USER"));
    }

    private static org.hamcrest.Matcher<java.util.Collection<?>> hasSize(int expected) {
        return org.hamcrest.Matchers.hasSize(expected);
    }
}
