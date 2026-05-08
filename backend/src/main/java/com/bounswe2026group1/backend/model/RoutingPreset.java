package com.bounswe2026group1.backend.model;

import java.util.EnumSet;
import java.util.Set;

import static com.bounswe2026group1.backend.model.RoutingConstraint.*;

/**
 * Named bundles of {@link RoutingConstraint} values that the user can pick
 * with one tap. Constraints are stored on the user as the source of truth;
 * the preset is metadata so the UI can show "you have the Wheelchair user
 * preset selected" without re-deriving the bundle on every PUT.
 *
 * {@link #CUSTOM} is the sentinel returned when a user's constraint set
 * doesn't exactly match any preset — and {@link #NONE} is the explicit
 * empty bundle.
 */
public enum RoutingPreset {

    NONE("No preferences",
            EnumSet.noneOf(RoutingConstraint.class),
            null),

    WHEELCHAIR_USER("Wheelchair user",
            EnumSet.of(
                    AVOID_STAIRS,
                    REQUIRE_RAMPS,
                    AVOID_UNSAFE_RAMPS,
                    AVOID_NARROW_SIDEWALKS,
                    AVOID_BLOCKED_OR_MISSING_SIDEWALKS,
                    REQUIRE_CURB_RAMPS,
                    AVOID_INACCESSIBLE_CROSSINGS),
            TravelMode.WHEELCHAIR),

    BLIND_OR_LOW_VISION("Blind or low vision",
            EnumSet.of(
                    AVOID_LOW_CLEARANCE,
                    AVOID_BLOCKED_OR_MISSING_SIDEWALKS,
                    AVOID_MISSING_TACTILE_PAVING,
                    AVOID_INACCESSIBLE_CROSSINGS),
            TravelMode.WALKING),

    MOBILITY_LIMITED("Mobility limited (cane, walker, elderly)",
            EnumSet.of(
                    REQUIRE_RAMPS,
                    AVOID_UNSAFE_RAMPS,
                    AVOID_SLIPPERY_SURFACES,
                    REQUIRE_HANDRAILS,
                    AVOID_UNSAFE_STAIRS),
            TravelMode.WALKING),

    /** Sentinel: returned by {@link #detectFromConstraints} when no preset matches. */
    CUSTOM("Custom", EnumSet.noneOf(RoutingConstraint.class), null);

    private final String label;
    private final Set<RoutingConstraint> constraints;
    private final TravelMode defaultTravelMode;

    RoutingPreset(String label, Set<RoutingConstraint> constraints, TravelMode defaultTravelMode) {
        this.label = label;
        this.constraints = constraints;
        this.defaultTravelMode = defaultTravelMode;
    }

    public String getLabel() {
        return label;
    }

    public Set<RoutingConstraint> getConstraints() {
        return constraints;
    }

    public TravelMode getDefaultTravelMode() {
        return defaultTravelMode;
    }

    /**
     * Return the preset whose constraint set exactly matches the input, otherwise
     * {@link #CUSTOM}. Empty input returns {@link #NONE}. {@link #CUSTOM} itself
     * is never auto-detected.
     */
    public static RoutingPreset detectFromConstraints(Set<RoutingConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return NONE;
        }
        for (RoutingPreset preset : values()) {
            if (preset == NONE || preset == CUSTOM) continue;
            if (preset.constraints.equals(constraints)) {
                return preset;
            }
        }
        return CUSTOM;
    }
}
