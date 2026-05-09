package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateRoutingPreferencesRequest;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.UserCustomRoutingProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingPreferencesServiceTest {

    @Mock private RegisteredUserRepository registeredUserRepository;
    @Mock private UserCustomRoutingProfileRepository customRoutingProfileRepository;

    @InjectMocks
    private RoutingPreferencesService service;

    private RegisteredUser user;

    @BeforeEach
    void setUp() {
        user = new RegisteredUser();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setPreferredPreset(RoutingPreset.NONE);
        user.setRoutingConstraints(new HashSet<>());
        // toResponse always reads the user's custom profile list; default empty
        // keeps every existing test focused on preset / constraint behaviour.
        org.mockito.Mockito.lenient()
                .when(customRoutingProfileRepository.findAllByUserIdOrderByCreatedAtAsc(any()))
                .thenReturn(Collections.emptyList());
    }

    /**
     * Stub the happy-path repository calls. Called from tests that actually exercise
     * the load + save flow; tests asserting unauthorized paths skip this so Mockito
     * strict-stubbing stays clean.
     */
    private void stubHappyPathRepo() {
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(registeredUserRepository.save(any(RegisteredUser.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getForEmail ──────────────────────────────────────────────────────────

    @Test
    void getForEmail_withNullEmail_throwsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getForEmail(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void getForEmail_unknownEmail_throwsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getForEmail("ghost@test.com"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void getForEmail_freshUser_returnsNonePresetAndFullCatalog() {
        when(registeredUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        RoutingPreferencesResponse response = service.getForEmail("user@test.com");

        assertEquals(RoutingPreset.NONE, response.getPreferredPreset());
        assertTrue(response.getConstraints().isEmpty());
        assertNull(response.getPreferredTravelMode());
        // Catalog should expose every preset and every constraint by name.
        assertEquals(RoutingPreset.values().length, response.getAvailablePresets().size());
        assertEquals(RoutingConstraint.values().length, response.getAvailableConstraints().size());
    }

    // ── update — preset only ────────────────────────────────────────────────

    @Test
    void update_withPresetOnly_fillsConstraintsAndTravelModeFromPreset() {
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.WHEELCHAIR_USER);

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();

        assertEquals(RoutingPreset.WHEELCHAIR_USER, saved.getPreferredPreset());
        assertEquals(RoutingPreset.WHEELCHAIR_USER.getConstraints(), saved.getRoutingConstraints());
        assertEquals(TravelMode.WHEELCHAIR, saved.getPreferredTravelMode());
    }

    @Test
    void update_withNonePresetOnly_clearsConstraintsButLeavesTravelModeUnset() {
        // Seed the user with prior state to confirm clearing.
        user.setRoutingConstraints(new HashSet<>(RoutingPreset.WHEELCHAIR_USER.getConstraints()));
        user.setPreferredPreset(RoutingPreset.WHEELCHAIR_USER);
        user.setPreferredTravelMode(TravelMode.WHEELCHAIR);

        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.NONE);

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();

        assertEquals(RoutingPreset.NONE, saved.getPreferredPreset());
        assertTrue(saved.getRoutingConstraints().isEmpty());
        // NONE has no defaultTravelMode, so the prior value is preserved (caller can override).
        assertEquals(TravelMode.WHEELCHAIR, saved.getPreferredTravelMode());
    }

    // ── update — constraints only ───────────────────────────────────────────

    @Test
    void update_withConstraintsMatchingPreset_autoDetectsThePreset() {
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setConstraints(new HashSet<>(RoutingPreset.MOBILITY_LIMITED.getConstraints()));

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();

        assertEquals(RoutingPreset.MOBILITY_LIMITED, saved.getPreferredPreset());
        assertEquals(RoutingPreset.MOBILITY_LIMITED.getConstraints(), saved.getRoutingConstraints());
    }

    @Test
    void update_withConstraintsNotMatchingAnyPreset_setsPresetToCustom() {
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setConstraints(EnumSet.of(
                RoutingConstraint.AVOID_LOW_CLEARANCE,
                RoutingConstraint.REQUIRE_HANDRAILS));

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals(RoutingPreset.CUSTOM, captor.getValue().getPreferredPreset());
    }

    @Test
    void update_withEmptyConstraintSet_setsPresetToNone() {
        user.setPreferredPreset(RoutingPreset.WHEELCHAIR_USER);
        user.setRoutingConstraints(new HashSet<>(RoutingPreset.WHEELCHAIR_USER.getConstraints()));

        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setConstraints(new HashSet<>());

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();
        assertEquals(RoutingPreset.NONE, saved.getPreferredPreset());
        assertTrue(saved.getRoutingConstraints().isEmpty());
    }

    // ── update — both fields together ───────────────────────────────────────

    @Test
    void update_withBothPresetAndMatchingConstraints_constraintsWin_presetMatches() {
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.MOBILITY_LIMITED);          // claim
        req.setConstraints(new HashSet<>(RoutingPreset.WHEELCHAIR_USER.getConstraints())); // reality

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();

        // Constraints win → stored set is WHEELCHAIR_USER's; detected preset matches reality.
        assertEquals(RoutingPreset.WHEELCHAIR_USER, saved.getPreferredPreset());
        assertEquals(RoutingPreset.WHEELCHAIR_USER.getConstraints(), saved.getRoutingConstraints());
    }

    @Test
    void update_withBothPresetAndArbitraryConstraints_setsCustom() {
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.WHEELCHAIR_USER);
        req.setConstraints(EnumSet.of(RoutingConstraint.AVOID_LOW_CLEARANCE));

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals(RoutingPreset.CUSTOM, captor.getValue().getPreferredPreset());
    }

    // ── update — travel mode handling ───────────────────────────────────────

    @Test
    void update_withExplicitTravelMode_overridesPresetDefault() {
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.WHEELCHAIR_USER); // default WHEELCHAIR
        req.setPreferredTravelMode(TravelMode.WALKING);         // explicit override

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals(TravelMode.WALKING, captor.getValue().getPreferredTravelMode());
    }

    @Test
    void update_constraintsSentWithoutPreset_doesNotChangeTravelMode() {
        user.setPreferredTravelMode(TravelMode.WALKING);

        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setConstraints(EnumSet.of(RoutingConstraint.AVOID_LOW_CLEARANCE));

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        assertEquals(TravelMode.WALKING, captor.getValue().getPreferredTravelMode(),
                "Sending only constraints must not silently change travelMode");
    }

    @Test
    void update_emptyRequest_isNoOp_returnsCurrentState() {
        user.setPreferredPreset(RoutingPreset.MOBILITY_LIMITED);
        user.setRoutingConstraints(new HashSet<>(RoutingPreset.MOBILITY_LIMITED.getConstraints()));
        user.setPreferredTravelMode(TravelMode.WALKING);

        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        RoutingPreferencesResponse resp = service.update("user@test.com", req);

        assertEquals(RoutingPreset.MOBILITY_LIMITED, resp.getPreferredPreset());
        assertEquals(TravelMode.WALKING, resp.getPreferredTravelMode());
        assertEquals(RoutingPreset.MOBILITY_LIMITED.getConstraints().size(), resp.getConstraints().size());
    }

    @Test
    void update_returnedConstraintsAreSorted() {
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setConstraints(EnumSet.of(
                RoutingConstraint.REQUIRE_HANDRAILS,
                RoutingConstraint.AVOID_LOW_CLEARANCE,
                RoutingConstraint.AVOID_STAIRS));

        RoutingPreferencesResponse resp = service.update("user@test.com", req);

        var sorted = resp.getConstraints().stream().sorted().toList();
        assertEquals(sorted, resp.getConstraints(), "Returned constraints must be sorted by name for stable output");
    }

    // ── CUSTOM preset semantics ──────────────────────────────────────────────

    @Test
    void update_withCustomPresetOnly_freshUserNoConstraints_resolvesToNonePreset() {
        // Regression for EnumSet.copyOf rejecting empty non-EnumSet collections.
        // A fresh user's routingConstraints is an empty HashSet; sending
        // {"preferredPreset":"CUSTOM"} used to throw IllegalArgumentException.
        // The empty set then auto-detects as NONE (not CUSTOM) per detectFromConstraints.
        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.CUSTOM);

        RoutingPreferencesResponse resp = assertDoesNotThrow(
                () -> service.update("user@test.com", req));

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();
        assertTrue(saved.getRoutingConstraints().isEmpty());
        assertEquals(RoutingPreset.NONE, saved.getPreferredPreset());
        assertEquals(RoutingPreset.NONE, resp.getPreferredPreset());
    }

    @Test
    void update_switchingToBuiltInPreset_clearsActiveCustomProfileId() {
        // User had a custom profile activated; switching to a built-in preset
        // must drop the active id so the UI doesn't keep showing it as active.
        user.setActiveCustomProfileId(42L);
        user.setPreferredPreset(RoutingPreset.CUSTOM);
        user.setRoutingConstraints(EnumSet.of(RoutingConstraint.AVOID_LOW_CLEARANCE));

        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.WHEELCHAIR_USER);

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();
        assertEquals(RoutingPreset.WHEELCHAIR_USER, saved.getPreferredPreset());
        assertNull(saved.getActiveCustomProfileId());
    }

    @Test
    void update_withCustomPresetOnly_keepsExistingConstraintsAndDetectsCustom() {
        // The user previously set CUSTOM with two arbitrary constraints. Sending
        // CUSTOM as the preset (and nothing else) should keep those constraints.
        Set<RoutingConstraint> existing = EnumSet.of(
                RoutingConstraint.AVOID_LOW_CLEARANCE,
                RoutingConstraint.REQUIRE_HANDRAILS);
        user.setRoutingConstraints(new HashSet<>(existing));
        user.setPreferredPreset(RoutingPreset.CUSTOM);

        stubHappyPathRepo();
        UpdateRoutingPreferencesRequest req = new UpdateRoutingPreferencesRequest();
        req.setPreferredPreset(RoutingPreset.CUSTOM);

        service.update("user@test.com", req);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(registeredUserRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();
        assertEquals(existing, saved.getRoutingConstraints());
        assertEquals(RoutingPreset.CUSTOM, saved.getPreferredPreset());
    }
}
