package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.model.UserCustomRoutingProfile;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.UserCustomRoutingProfileRepository;
import com.bounswe2026group1.backend.service.CustomRoutingProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-context integration test for the per-user custom routing-profile API.
 * Mirrors the pattern in {@link RoutingPreferencesIntegrationTest}: real
 * Spring container, H2 PostgreSQL-mode test DB, mocked auth via
 * {@code with(user(...))}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomRoutingProfileIntegrationTest {

    private static final String EMAIL = "profiles-test@test.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private RegisteredUserRepository registeredUserRepository;
    @Autowired private UserCustomRoutingProfileRepository profileRepository;
    @Autowired private ObjectMapper objectMapper;

    private RegisteredUser user;

    @BeforeEach
    void setUp() {
        registeredUserRepository.findByEmail(EMAIL).ifPresent(u -> {
            profileRepository.findAllByUserIdOrderByCreatedAtAsc(u.getId())
                    .forEach(profileRepository::delete);
            registeredUserRepository.delete(u);
        });
        RegisteredUser u = new RegisteredUser();
        u.setName("Profile Tester");
        u.setEmail(EMAIL);
        u.setPassword("hashed-password-not-used-in-tests");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setRoutingConstraints(new HashSet<>());
        u.setPreferredPreset(RoutingPreset.NONE);
        user = registeredUserRepository.save(u);
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    @Test
    void list_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/users/me/routing-profiles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_returns401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/users/me/routing-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Empty list ──────────────────────────────────────────────────────────

    @Test
    void list_freshUser_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/users/me/routing-profiles")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── Create ──────────────────────────────────────────────────────────────

    @Test
    void create_persistsProfileAndReturnsCreated() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Work commute",
                "constraints", List.of("AVOID_STAIRS", "REQUIRE_RAMPS"),
                "preferredTravelMode", "WHEELCHAIR"));

        mockMvc.perform(post("/api/users/me/routing-profiles")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Work commute"))
                .andExpect(jsonPath("$.constraints", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.preferredTravelMode").value("WHEELCHAIR"));

        assertThat(profileRepository.findAllByUserIdOrderByCreatedAtAsc(user.getId())).hasSize(1);
    }

    @Test
    void create_atCap_returnsConflict() throws Exception {
        for (int i = 0; i < CustomRoutingProfileService.MAX_CUSTOM_PROFILES; i++) {
            UserCustomRoutingProfile p = UserCustomRoutingProfile.builder()
                    .user(user).name("p" + i).constraints(new HashSet<>()).build();
            profileRepository.save(p);
        }

        mockMvc.perform(post("/api/users/me/routing-profiles")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"overflow\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_duplicateName_returnsConflict() throws Exception {
        UserCustomRoutingProfile existing = UserCustomRoutingProfile.builder()
                .user(user).name("Taken").constraints(new HashSet<>()).build();
        profileRepository.save(existing);

        mockMvc.perform(post("/api/users/me/routing-profiles")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Taken\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/me/routing-profiles")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Update ──────────────────────────────────────────────────────────────

    @Test
    void update_changesName_returnsOk_andPersists() throws Exception {
        UserCustomRoutingProfile p = profileRepository.save(UserCustomRoutingProfile.builder()
                .user(user).name("Old").constraints(new HashSet<>()).build());

        mockMvc.perform(put("/api/users/me/routing-profiles/" + p.getId())
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));

        assertThat(profileRepository.findById(p.getId()).orElseThrow().getName()).isEqualTo("New");
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        mockMvc.perform(put("/api/users/me/routing-profiles/999999")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    // ── Activate ────────────────────────────────────────────────────────────

    @Test
    void activate_setsCustomPresetAndConstraintsOnUser() throws Exception {
        UserCustomRoutingProfile p = profileRepository.save(UserCustomRoutingProfile.builder()
                .user(user).name("Heavy")
                .constraints(java.util.EnumSet.of(
                        RoutingConstraint.AVOID_STAIRS,
                        RoutingConstraint.REQUIRE_HANDRAILS))
                .preferredTravelMode(TravelMode.WHEELCHAIR)
                .build());

        mockMvc.perform(post("/api/users/me/routing-profiles/" + p.getId() + "/activate")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("CUSTOM"))
                .andExpect(jsonPath("$.activeCustomProfileId").value(p.getId()))
                .andExpect(jsonPath("$.preferredTravelMode").value("WHEELCHAIR"));

        RegisteredUser stored = registeredUserRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getPreferredPreset()).isEqualTo(RoutingPreset.CUSTOM);
        assertThat(stored.getActiveCustomProfile()).isNotNull();
        assertThat(stored.getActiveCustomProfile().getId()).isEqualTo(p.getId());
        assertThat(stored.getRoutingConstraints())
                .containsExactlyInAnyOrder(
                        RoutingConstraint.AVOID_STAIRS,
                        RoutingConstraint.REQUIRE_HANDRAILS);
    }

    @Test
    void activateThenSwitchToBuiltInPreset_clearsActiveCustomProfile() throws Exception {
        UserCustomRoutingProfile p = profileRepository.save(UserCustomRoutingProfile.builder()
                .user(user).name("Heavy")
                .constraints(java.util.EnumSet.of(RoutingConstraint.AVOID_STAIRS))
                .build());

        mockMvc.perform(post("/api/users/me/routing-profiles/" + p.getId() + "/activate")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk());

        // Switch to a built-in preset via the existing routing-preferences endpoint.
        mockMvc.perform(put("/api/users/me/routing-preferences")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredPreset\":\"WHEELCHAIR_USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredPreset").value("WHEELCHAIR_USER"))
                .andExpect(jsonPath("$.activeCustomProfileId").doesNotExist());

        RegisteredUser stored = registeredUserRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getActiveCustomProfile()).isNull();
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Test
    void deleteActiveProfile_clearsUserRoutingState() throws Exception {
        UserCustomRoutingProfile p = profileRepository.save(UserCustomRoutingProfile.builder()
                .user(user).name("Active")
                .constraints(java.util.EnumSet.of(RoutingConstraint.AVOID_STAIRS))
                .build());

        mockMvc.perform(post("/api/users/me/routing-profiles/" + p.getId() + "/activate")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/me/routing-profiles/" + p.getId())
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isNoContent());

        RegisteredUser stored = registeredUserRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getActiveCustomProfile()).isNull();
        assertThat(stored.getPreferredPreset()).isEqualTo(RoutingPreset.NONE);
        assertThat(stored.getRoutingConstraints()).isEmpty();
    }
}
