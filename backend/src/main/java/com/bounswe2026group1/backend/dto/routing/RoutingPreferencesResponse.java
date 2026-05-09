package com.bounswe2026group1.backend.dto.routing;

import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.TravelMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Returned by {@code GET /api/users/me/routing-preferences} and after every
 * {@code PUT}. Self-describing: the catalog fields let the frontend render
 * settings without hard-coding any preset or constraint metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingPreferencesResponse {

    /** Currently selected preset. Always present; defaults to {@link RoutingPreset#NONE}. */
    private RoutingPreset preferredPreset;

    /** The user's selected constraints, sorted by enum name for stable output. */
    private List<String> constraints;

    /** Optional travel-mode preference; null if unset. */
    private TravelMode preferredTravelMode;

    /**
     * Id of the user's currently activated custom routing profile, or null
     * when a built-in preset (or no preset) is active.
     */
    private Long activeCustomProfileId;

    /** All preset values with their bundled constraints — stable across users. */
    private List<RoutingPresetInfo> availablePresets;

    /** All constraint values with labels and descriptions — stable across users. */
    private List<RoutingConstraintInfo> availableConstraints;

    /** Per-user named bundles, in creation order. */
    private List<CustomRoutingProfileResponse> customProfiles;
}
