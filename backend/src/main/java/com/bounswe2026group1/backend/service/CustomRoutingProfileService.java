package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.CreateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.dto.routing.CustomRoutingProfileResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.UpdateCustomRoutingProfileRequest;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.UserCustomRoutingProfile;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.UserCustomRoutingProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomRoutingProfileService {

    /**
     * Hard cap on saved custom profiles per user. Picked low to keep the
     * preset picker scannable and to bound the avatar-page payload.
     */
    public static final int MAX_CUSTOM_PROFILES = 5;

    private final UserCustomRoutingProfileRepository profileRepository;
    private final RegisteredUserRepository userRepository;
    private final RoutingPreferencesService routingPreferencesService;

    @Transactional(readOnly = true)
    public List<CustomRoutingProfileResponse> listForEmail(String email) {
        RegisteredUser user = loadUser(email);
        return profileRepository.findAllByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(CustomRoutingProfileResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CustomRoutingProfileResponse create(String email, CreateCustomRoutingProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body required.");
        }
        RegisteredUser user = loadUser(email);

        long existing = profileRepository.countByUserId(user.getId());
        if (existing >= MAX_CUSTOM_PROFILES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Maximum of " + MAX_CUSTOM_PROFILES + " custom routing profiles reached.");
        }

        String name = normalizedName(request.getName());
        if (profileRepository.existsByUserIdAndName(user.getId(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A custom routing profile with this name already exists.");
        }

        UserCustomRoutingProfile profile = UserCustomRoutingProfile.builder()
                .user(user)
                .name(name)
                .constraints(sanitize(request.getConstraints()))
                .preferredTravelMode(request.getPreferredTravelMode())
                .build();
        profile = profileRepository.save(profile);
        return CustomRoutingProfileResponse.fromEntity(profile);
    }

    @Transactional
    public CustomRoutingProfileResponse update(String email, Long profileId, UpdateCustomRoutingProfileRequest request) {
        RegisteredUser user = loadUser(email);
        UserCustomRoutingProfile profile = loadProfile(profileId, user.getId());

        if (request == null) {
            return CustomRoutingProfileResponse.fromEntity(profile);
        }

        if (request.getName() != null) {
            String newName = normalizedName(request.getName());
            if (!newName.equals(profile.getName())
                    && profileRepository.existsByUserIdAndName(user.getId(), newName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A custom routing profile with this name already exists.");
            }
            profile.setName(newName);
        }
        if (request.getConstraints() != null) {
            profile.setConstraints(sanitize(request.getConstraints()));
        }
        if (request.getPreferredTravelMode() != null) {
            profile.setPreferredTravelMode(request.getPreferredTravelMode());
        }

        UserCustomRoutingProfile saved = profileRepository.save(profile);

        // If this profile is currently active, propagate any constraint /
        // travel-mode change onto the user so routing stays in sync.
        if (isActive(user, saved.getId())) {
            user.setRoutingConstraints(new HashSet<>(saved.getConstraints()));
            user.setPreferredPreset(RoutingPreset.CUSTOM);
            if (saved.getPreferredTravelMode() != null) {
                user.setPreferredTravelMode(saved.getPreferredTravelMode());
            }
            userRepository.save(user);
        }

        return CustomRoutingProfileResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(String email, Long profileId) {
        RegisteredUser user = loadUser(email);
        UserCustomRoutingProfile profile = loadProfile(profileId, user.getId());

        boolean wasActive = isActive(user, profile.getId());
        profileRepository.delete(profile);

        if (wasActive) {
            user.setActiveCustomProfile(null);
            user.setRoutingConstraints(new HashSet<>());
            user.setPreferredPreset(RoutingPreset.NONE);
            userRepository.save(user);
        }
    }

    @Transactional
    public RoutingPreferencesResponse activate(String email, Long profileId) {
        RegisteredUser user = loadUser(email);
        UserCustomRoutingProfile profile = loadProfile(profileId, user.getId());

        user.setRoutingConstraints(new HashSet<>(profile.getConstraints()));
        user.setPreferredPreset(RoutingPreset.CUSTOM);
        user.setActiveCustomProfile(profile);
        if (profile.getPreferredTravelMode() != null) {
            user.setPreferredTravelMode(profile.getPreferredTravelMode());
        }
        userRepository.save(user);

        return routingPreferencesService.toResponse(user);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RegisteredUser loadUser(String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    private UserCustomRoutingProfile loadProfile(Long profileId, Long userId) {
        if (profileId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found.");
        }
        return profileRepository.findByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found."));
    }

    private static String normalizedName(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name cannot be blank.");
        }
        if (trimmed.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name must be at most 50 characters.");
        }
        return trimmed;
    }

    private static Set<RoutingConstraint> sanitize(Set<RoutingConstraint> input) {
        if (input == null) return new HashSet<>();
        return input.stream().filter(c -> c != null).collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Compares the user's active custom profile id without materialising the
     * lazy {@link UserCustomRoutingProfile} association. Hibernate exposes
     * {@code getId()} on a lazy proxy without a database fetch, so this stays
     * cheap.
     */
    private static boolean isActive(RegisteredUser user, Long profileId) {
        UserCustomRoutingProfile active = user.getActiveCustomProfile();
        return active != null && profileId != null && profileId.equals(active.getId());
    }
}
