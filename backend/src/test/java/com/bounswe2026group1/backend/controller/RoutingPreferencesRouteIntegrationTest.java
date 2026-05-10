package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.service.ObstacleService;
import com.bounswe2026group1.backend.service.OrsRoutingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test for the routing-prefs → /api/routes flow.
 *
 * Boots the full Spring container, persists a real {@link RegisteredUser} with
 * routing preferences set, mocks {@link OrsRoutingClient} so we don't hit
 * external HTTP, and asserts the response shape:
 *
 * <ul>
 *   <li>Anonymous callers: no alternative is flagged {@code preferred:true}.</li>
 *   <li>Authenticated user with {@code preferredTravelMode}: matching
 *       alternative is flagged {@code preferred:true}.</li>
 *   <li>Authenticated user with constraint set: the constraint set actually
 *       reaches {@link ObstacleService#buildAvoidPolygons(java.util.Set)}.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoutingPreferencesRouteIntegrationTest {

    private static final String EMAIL = "route-int@test.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private RegisteredUserRepository registeredUserRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private OrsRoutingClient orsRoutingClient;
    @MockitoBean private ObstacleService obstacleService;

    private RegisteredUser user;

    @BeforeEach
    void setUp() {
        registeredUserRepository.findByEmail(EMAIL).ifPresent(registeredUserRepository::delete);

        user = new RegisteredUser();
        user.setName("Route Tester");
        user.setEmail(EMAIL);
        user.setPassword("hashed-password-stub");
        user.setRole(UserRole.USER);
        user.setRoutingConstraints(new HashSet<>());
        user.setPreferredPreset(RoutingPreset.NONE);
        user = registeredUserRepository.save(user);

        // Canned ORS response for every fetchDirections call (any mode, any avoid arg).
        RoutingDirectionsResult canned = RoutingDirectionsResult.builder()
                .distanceMeters(500.0)
                .durationSeconds(360.0)
                .geometry("")
                .steps(List.of())
                .build();
        when(orsRoutingClient.fetchDirections(any(), any(), any(), any())).thenReturn(canned);
        when(obstacleService.findClosestRampInBoundingBox(any(), any())).thenReturn(null);
        when(obstacleService.findObstaclesOnPath(any(), any())).thenReturn(List.of());
        when(obstacleService.buildAvoidPolygons(any())).thenReturn(null);
    }

    private static String routeRequestBody() {
        return """
                {
                    "startLat": 41.0850,
                    "startLon": 29.0451,
                    "endLat":   41.0820,
                    "endLon":   29.0500,
                    "mode":     "WALKING"
                }
                """;
    }

    // ── Anonymous → no preferred flag ───────────────────────────────────────

    @Test
    void anonymous_routeResponse_hasNoPreferredAlternative() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeRequestBody()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode alternative : body) {
            assertThat(alternative.get("preferred").asBoolean()).isFalse();
        }
    }

    // ── User with preferredTravelMode → matching alternative flagged ────────

    @Test
    void authenticated_withWheelchairPreference_flagsWheelchairAlternative() throws Exception {
        user.setPreferredTravelMode(TravelMode.WHEELCHAIR);
        registeredUserRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/routes")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeRequestBody()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long preferredCount = 0;
        boolean wheelchairFlagged = false;
        for (JsonNode alternative : body) {
            if (alternative.get("preferred").asBoolean()) {
                preferredCount++;
                if ("WHEELCHAIR".equals(alternative.get("mode").asString())) {
                    wheelchairFlagged = true;
                }
            }
        }
        assertThat(preferredCount).as("at most one alternative should be flagged").isEqualTo(1);
        assertThat(wheelchairFlagged).as("the wheelchair alternative should be the flagged one").isTrue();
    }

    @Test
    void authenticated_withWalkingPreference_flagsAccessibleWalkingOverFastest() throws Exception {
        // Make avoid polygons non-null so the "Accessible Route" alternative is built.
        when(obstacleService.buildAvoidPolygons(any())).thenReturn(objectMapper.createObjectNode());

        user.setPreferredTravelMode(TravelMode.WALKING);
        registeredUserRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/routes")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeRequestBody()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // The Accessible Route is appended after the Fastest Route, so it wins the
        // last-match flag in markPreferred(). The Fastest Route must not be flagged.
        for (JsonNode alternative : body) {
            String label = alternative.get("routeLabel").asString();
            boolean preferred = alternative.get("preferred").asBoolean();
            if ("Accessible Route".equals(label)) {
                assertThat(preferred).as("Accessible Route should be the preferred alternative").isTrue();
            } else if ("Fastest Route".equals(label)) {
                assertThat(preferred).as("Fastest Route must NOT be the preferred alternative").isFalse();
            }
        }
    }

    // ── User with constraints → constraints reach ObstacleService ───────────

    @Test
    void authenticated_withWheelchairUserPreset_passesConstraintsToObstacleService() throws Exception {
        user.setPreferredPreset(RoutingPreset.WHEELCHAIR_USER);
        user.setRoutingConstraints(new HashSet<>(RoutingPreset.WHEELCHAIR_USER.getConstraints()));
        user.setPreferredTravelMode(TravelMode.WHEELCHAIR);
        registeredUserRepository.save(user);

        mockMvc.perform(post("/api/routes")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeRequestBody()))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<RoutingConstraint>> captor = ArgumentCaptor.forClass(Set.class);
        verify(obstacleService).buildAvoidPolygons(captor.capture());
        assertThat(captor.getValue())
                .as("constraint set passed to ObstacleService should match the user's stored set")
                .containsExactlyInAnyOrderElementsOf(RoutingPreset.WHEELCHAIR_USER.getConstraints());
    }

    @Test
    void anonymous_passesEmptyConstraintsToObstacleService() throws Exception {
        mockMvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeRequestBody()))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<RoutingConstraint>> captor = ArgumentCaptor.forClass(Set.class);
        verify(obstacleService).buildAvoidPolygons(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    // ── User without preferredTravelMode → no flag ──────────────────────────

    @Test
    void authenticated_withoutPreferredTravelMode_flagsNoAlternative() throws Exception {
        user.setPreferredTravelMode(null);
        registeredUserRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/routes")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeRequestBody()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode alternative : body) {
            assertThat(alternative.get("preferred").asBoolean()).isFalse();
        }
    }

    // ── NONE preset → null constraints reach ObstacleService (issue #544) ──

    /**
     * A signed-in user who explicitly chose {@code RoutingPreset.NONE} should
     * NOT have their reports avoided. The controller must distinguish them
     * from anonymous callers (who fall through to the {@code Set.of()}
     * baseline) by passing {@code null} instead.
     */
    @Test
    void authenticated_withNonePreset_passesNullConstraintsToObstacleService() throws Exception {
        // Default setup already has preset=NONE; reaffirm explicitly for clarity.
        user.setPreferredPreset(RoutingPreset.NONE);
        user.setRoutingConstraints(new HashSet<>());
        registeredUserRepository.save(user);

        mockMvc.perform(post("/api/routes")
                        .with(user(EMAIL).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeRequestBody()))
                .andExpect(status().isOk());

        // Anonymous would pass Set.of(); NONE-preset users pass null so
        // ObstacleService skips avoidance entirely.
        verify(obstacleService, atLeastOnce()).buildAvoidPolygons(isNull());
        verify(obstacleService, never()).buildAvoidPolygons(eq(Set.of()));
    }
}
