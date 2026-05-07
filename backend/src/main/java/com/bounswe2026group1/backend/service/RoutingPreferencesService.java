package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RoutingConstraintInfo;
import com.bounswe2026group1.backend.dto.routing.RoutingPreferencesResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingPresetInfo;
import com.bounswe2026group1.backend.dto.routing.UpdateRoutingPreferencesRequest;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoutingPreferencesService {

    private final RegisteredUserRepository registeredUserRepository;

    private static final List<RoutingPresetInfo> PRESET_CATALOG =
            Arrays.stream(RoutingPreset.values())
                    .map(RoutingPresetInfo::fromEnum)
                    .toList();

    private static final List<RoutingConstraintInfo> CONSTRAINT_CATALOG =
            Arrays.stream(RoutingConstraint.values())
                    .map(RoutingConstraintInfo::fromEnum)
                    .toList();

    @Transactional(readOnly = true)
    public RoutingPreferencesResponse getForEmail(String email) {
        return toResponse(loadUser(email));
    }

    /**
     * Apply the request to the user's stored preferences. Resolves preset vs
     * constraints per the contract on
     * {@link UpdateRoutingPreferencesRequest}.
     */
    @Transactional
    public RoutingPreferencesResponse update(String email, UpdateRoutingPreferencesRequest request) {
        RegisteredUser user = loadUser(email);
        if (request == null) {
            return toResponse(user);
        }

        Set<RoutingConstraint> resolvedConstraints;
        if (request.getConstraints() != null) {
            // Constraints win when both fields are sent — see UpdateRoutingPreferencesRequest contract.
            resolvedConstraints = sanitize(request.getConstraints());
        } else if (request.getPreferredPreset() != null) {
            resolvedConstraints = EnumSet.copyOf(
                    request.getPreferredPreset() == RoutingPreset.CUSTOM
                            ? user.getRoutingConstraints()
                            : request.getPreferredPreset().getConstraints());
        } else {
            resolvedConstraints = user.getRoutingConstraints();
        }

        // Always derive the preset from the resolved constraint set so the
        // displayed preset stays consistent with what's actually applied.
        RoutingPreset detected = RoutingPreset.detectFromConstraints(resolvedConstraints);

        user.setRoutingConstraints(new HashSet<>(resolvedConstraints));
        user.setPreferredPreset(detected);

        if (request.getPreferredTravelMode() != null) {
            user.setPreferredTravelMode(request.getPreferredTravelMode());
        } else if (request.getPreferredPreset() != null
                && request.getPreferredPreset().getDefaultTravelMode() != null
                && request.getConstraints() == null) {
            // Only auto-fill the travel mode when the caller explicitly picked a preset
            // and didn't override constraints — preserves explicit nulls otherwise.
            user.setPreferredTravelMode(request.getPreferredPreset().getDefaultTravelMode());
        }

        registeredUserRepository.save(user);
        return toResponse(user);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private RegisteredUser loadUser(String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static Set<RoutingConstraint> sanitize(Set<RoutingConstraint> input) {
        // Drop nulls and duplicates that could slip through Jackson deserialization.
        return input.stream().filter(c -> c != null).collect(Collectors.toCollection(HashSet::new));
    }

    private RoutingPreferencesResponse toResponse(RegisteredUser user) {
        Set<RoutingConstraint> stored = user.getRoutingConstraints() == null
                ? Set.of()
                : user.getRoutingConstraints();
        List<String> constraintNames = stored.stream()
                .map(RoutingConstraint::name)
                .sorted()
                .toList();
        RoutingPreset preset = user.getPreferredPreset() == null
                ? RoutingPreset.NONE
                : user.getPreferredPreset();
        return RoutingPreferencesResponse.builder()
                .preferredPreset(preset)
                .constraints(constraintNames)
                .preferredTravelMode(user.getPreferredTravelMode())
                .availablePresets(PRESET_CATALOG)
                .availableConstraints(CONSTRAINT_CATALOG)
                .build();
    }
}
