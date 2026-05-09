package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.CreateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.dto.routing.CustomRoutingProfileResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import com.bounswe2026group1.backend.model.UserCustomRoutingProfile;
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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomRoutingProfileServiceTest {

    @Mock private UserCustomRoutingProfileRepository profileRepository;
    @Mock private RegisteredUserRepository userRepository;
    @Mock private RoutingPreferencesService routingPreferencesService;

    @InjectMocks
    private CustomRoutingProfileService service;

    private RegisteredUser user;

    @BeforeEach
    void setUp() {
        user = new RegisteredUser();
        user.setId(7L);
        user.setEmail("user@test.com");
        user.setPreferredPreset(RoutingPreset.NONE);
        user.setRoutingConstraints(new HashSet<>());
    }

    private void stubUserLookup() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
    }

    private void stubProfileSavePassthrough() {
        when(profileRepository.save(any(UserCustomRoutingProfile.class)))
                .thenAnswer(inv -> {
                    UserCustomRoutingProfile p = inv.getArgument(0);
                    if (p.getId() == null) p.setId(101L);
                    return p;
                });
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    void create_happyPath_persistsTrimmedNameAndConstraints() {
        stubUserLookup();
        when(profileRepository.countByUserId(7L)).thenReturn(0L);
        when(profileRepository.existsByUserIdAndName(7L, "Work commute")).thenReturn(false);
        stubProfileSavePassthrough();

        CreateCustomRoutingProfileRequest req = new CreateCustomRoutingProfileRequest(
                "  Work commute  ",
                EnumSet.of(RoutingConstraint.AVOID_STAIRS, RoutingConstraint.REQUIRE_RAMPS),
                TravelMode.WHEELCHAIR);

        CustomRoutingProfileResponse resp = service.create("user@test.com", req);

        ArgumentCaptor<UserCustomRoutingProfile> captor = ArgumentCaptor.forClass(UserCustomRoutingProfile.class);
        verify(profileRepository).save(captor.capture());
        UserCustomRoutingProfile saved = captor.getValue();

        assertEquals("Work commute", saved.getName());
        assertEquals(2, saved.getConstraints().size());
        assertEquals(TravelMode.WHEELCHAIR, saved.getPreferredTravelMode());
        assertSame(user, saved.getUser());
        assertEquals("Work commute", resp.getName());
    }

    @Test
    void create_atCap_returnsConflict() {
        stubUserLookup();
        when(profileRepository.countByUserId(7L))
                .thenReturn((long) CustomRoutingProfileService.MAX_CUSTOM_PROFILES);

        CreateCustomRoutingProfileRequest req = new CreateCustomRoutingProfileRequest(
                "Sixth", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create("user@test.com", req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void create_duplicateName_returnsConflict() {
        stubUserLookup();
        when(profileRepository.countByUserId(7L)).thenReturn(1L);
        when(profileRepository.existsByUserIdAndName(7L, "Work")).thenReturn(true);

        CreateCustomRoutingProfileRequest req = new CreateCustomRoutingProfileRequest(
                "Work", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create("user@test.com", req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void create_blankName_returnsBadRequest() {
        stubUserLookup();
        when(profileRepository.countByUserId(7L)).thenReturn(0L);

        CreateCustomRoutingProfileRequest req = new CreateCustomRoutingProfileRequest(
                "   ", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create("user@test.com", req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void create_unknownEmail_returnsUnauthorized() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
        CreateCustomRoutingProfileRequest req = new CreateCustomRoutingProfileRequest("X", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create("ghost@test.com", req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    // ── update ──────────────────────────────────────────────────────────────

    @Test
    void update_changesNameAndConstraints_propagatesToActiveUser() {
        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .id(50L).user(user).name("Old")
                .constraints(EnumSet.of(RoutingConstraint.AVOID_STAIRS))
                .build();
        user.setActiveCustomProfileId(50L);
        user.setRoutingConstraints(new HashSet<>(profile.getConstraints()));
        user.setPreferredPreset(RoutingPreset.CUSTOM);

        stubUserLookup();
        when(profileRepository.findByIdAndUserId(50L, 7L)).thenReturn(Optional.of(profile));
        when(profileRepository.existsByUserIdAndName(7L, "New")).thenReturn(false);
        stubProfileSavePassthrough();

        UpdateCustomRoutingProfileRequest req = new UpdateCustomRoutingProfileRequest(
                "New",
                EnumSet.of(RoutingConstraint.REQUIRE_HANDRAILS),
                TravelMode.WALKING);

        service.update("user@test.com", 50L, req);

        // Profile updated…
        assertEquals("New", profile.getName());
        assertEquals(EnumSet.of(RoutingConstraint.REQUIRE_HANDRAILS), profile.getConstraints());
        // …and the active user mirrored it.
        ArgumentCaptor<RegisteredUser> userCaptor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(userRepository).save(userCaptor.capture());
        RegisteredUser savedUser = userCaptor.getValue();
        assertEquals(EnumSet.of(RoutingConstraint.REQUIRE_HANDRAILS), savedUser.getRoutingConstraints());
        assertEquals(RoutingPreset.CUSTOM, savedUser.getPreferredPreset());
        assertEquals(TravelMode.WALKING, savedUser.getPreferredTravelMode());
    }

    @Test
    void update_inactiveProfile_doesNotTouchUser() {
        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .id(60L).user(user).name("Old").constraints(new HashSet<>()).build();
        user.setActiveCustomProfileId(99L); // a different profile is active

        stubUserLookup();
        when(profileRepository.findByIdAndUserId(60L, 7L)).thenReturn(Optional.of(profile));
        stubProfileSavePassthrough();

        UpdateCustomRoutingProfileRequest req = new UpdateCustomRoutingProfileRequest(
                null,
                EnumSet.of(RoutingConstraint.AVOID_STAIRS),
                null);

        service.update("user@test.com", 60L, req);

        verify(userRepository, never()).save(any());
    }

    @Test
    void update_renameToExistingName_returnsConflict() {
        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .id(60L).user(user).name("Current").constraints(new HashSet<>()).build();

        stubUserLookup();
        when(profileRepository.findByIdAndUserId(60L, 7L)).thenReturn(Optional.of(profile));
        when(profileRepository.existsByUserIdAndName(7L, "Taken")).thenReturn(true);

        UpdateCustomRoutingProfileRequest req = new UpdateCustomRoutingProfileRequest("Taken", null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.update("user@test.com", 60L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void update_unknownProfile_returnsNotFound() {
        stubUserLookup();
        when(profileRepository.findByIdAndUserId(999L, 7L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.update("user@test.com", 999L, new UpdateCustomRoutingProfileRequest()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_activeProfile_clearsUserRoutingState() {
        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .id(50L).user(user).name("Active")
                .constraints(EnumSet.of(RoutingConstraint.AVOID_STAIRS))
                .build();
        user.setActiveCustomProfileId(50L);
        user.setPreferredPreset(RoutingPreset.CUSTOM);
        user.setRoutingConstraints(new HashSet<>(profile.getConstraints()));

        stubUserLookup();
        when(profileRepository.findByIdAndUserId(50L, 7L)).thenReturn(Optional.of(profile));

        service.delete("user@test.com", 50L);

        verify(profileRepository).delete(profile);
        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(userRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();
        assertNull(saved.getActiveCustomProfileId());
        assertEquals(RoutingPreset.NONE, saved.getPreferredPreset());
        assertTrue(saved.getRoutingConstraints().isEmpty());
    }

    @Test
    void delete_inactiveProfile_doesNotTouchUser() {
        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .id(60L).user(user).name("Other").constraints(new HashSet<>()).build();
        user.setActiveCustomProfileId(50L); // different one

        stubUserLookup();
        when(profileRepository.findByIdAndUserId(60L, 7L)).thenReturn(Optional.of(profile));

        service.delete("user@test.com", 60L);

        verify(profileRepository).delete(profile);
        verify(userRepository, never()).save(any());
    }

    // ── activate ────────────────────────────────────────────────────────────

    @Test
    void activate_copiesConstraintsAndSetsCustomPreset() {
        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .id(50L).user(user).name("Heavy")
                .constraints(EnumSet.of(
                        RoutingConstraint.AVOID_STAIRS,
                        RoutingConstraint.REQUIRE_HANDRAILS))
                .preferredTravelMode(TravelMode.WHEELCHAIR)
                .build();

        stubUserLookup();
        when(profileRepository.findByIdAndUserId(50L, 7L)).thenReturn(Optional.of(profile));
        when(routingPreferencesService.toResponse(any(RegisteredUser.class)))
                .thenReturn(new RoutingPreferencesResponse());

        RoutingPreferencesResponse out = service.activate("user@test.com", 50L);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(userRepository).save(captor.capture());
        RegisteredUser saved = captor.getValue();
        assertEquals(RoutingPreset.CUSTOM, saved.getPreferredPreset());
        assertEquals(50L, saved.getActiveCustomProfileId());
        assertEquals(profile.getConstraints(), saved.getRoutingConstraints());
        assertEquals(TravelMode.WHEELCHAIR, saved.getPreferredTravelMode());
        assertNotNull(out);
    }

    @Test
    void activate_profileWithoutTravelMode_preservesUserTravelMode() {
        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .id(50L).user(user).name("Light")
                .constraints(EnumSet.of(RoutingConstraint.AVOID_LOW_CLEARANCE))
                .preferredTravelMode(null)
                .build();
        user.setPreferredTravelMode(TravelMode.WALKING);

        stubUserLookup();
        when(profileRepository.findByIdAndUserId(50L, 7L)).thenReturn(Optional.of(profile));
        when(routingPreferencesService.toResponse(any(RegisteredUser.class)))
                .thenReturn(new RoutingPreferencesResponse());

        service.activate("user@test.com", 50L);

        ArgumentCaptor<RegisteredUser> captor = ArgumentCaptor.forClass(RegisteredUser.class);
        verify(userRepository).save(captor.capture());
        assertEquals(TravelMode.WALKING, captor.getValue().getPreferredTravelMode());
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    void listForEmail_returnsAllProfilesInCreationOrder() {
        UserCustomRoutingProfile a = UserCustomRoutingProfile.builder().id(1L).user(user).name("A")
                .constraints(new HashSet<>()).build();
        UserCustomRoutingProfile b = UserCustomRoutingProfile.builder().id(2L).user(user).name("B")
                .constraints(new HashSet<>()).build();

        stubUserLookup();
        when(profileRepository.findAllByUserIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(a, b));

        List<CustomRoutingProfileResponse> resp = service.listForEmail("user@test.com");
        assertEquals(2, resp.size());
        assertEquals("A", resp.get(0).getName());
        assertEquals("B", resp.get(1).getName());
    }
}
