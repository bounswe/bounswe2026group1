package com.bounswe2026group1.backend.model;

import java.util.Set;
import java.util.stream.Collectors;

import static com.bounswe2026group1.backend.model.IssueType.*;
import static com.bounswe2026group1.backend.model.ObjectType.*;

/**
 * High-level user-facing routing constraints. Each value carries the set of
 * (ObjectType, IssueType) hazard tuples it covers. The avoid-polygon builder
 * unions hazards across the user's selected constraints to decide which
 * VERIFIED reports become avoidance polygons.
 *
 * Constraints intentionally cover only path-segment hazards (sidewalks, ramps,
 * stairs, low overhead clearance). Door and elevator issues are destination-level
 * and stay informational — they don't generate avoid polygons.
 */
public enum RoutingConstraint {

    AVOID_STAIRS(
            "Avoid stairs entirely",
            "Routes will avoid sidewalk segments containing reported stairways.",
            Set.of(
                    new IssueHazard(STAIR, MISSING_HANDRAIL),
                    new IssueHazard(STAIR, NO_LANDING),
                    new IssueHazard(STAIR, TOO_NARROW),
                    new IssueHazard(STAIR, SLIPPERY_SURFACE),
                    new IssueHazard(STAIR, RISER_TOO_HIGH),
                    new IssueHazard(STAIR, TREAD_TOO_SHALLOW),
                    new IssueHazard(STAIR, NO_ANTI_SLIP),
                    new IssueHazard(STAIR, OPEN_RISERS),
                    new IssueHazard(STAIR, IRREGULAR_STEPS),
                    new IssueHazard(STAIR, MISSING_NOSING_STRIP),
                    new IssueHazard(STAIR, HANDRAIL_TOO_LOW))),

    REQUIRE_RAMPS(
            "Prefer routes with ramps over steps",
            "Treats reports of missing ramps as obstacles to route around.",
            Set.of(new IssueHazard(RAMP, MISSING))),

    AVOID_UNSAFE_RAMPS(
            "Avoid unsafe ramps",
            "Routes will avoid ramps reported as too steep, too narrow, missing handrails, handrails too low, missing landings, or slippery.",
            Set.of(
                    new IssueHazard(RAMP, TOO_STEEP),
                    new IssueHazard(RAMP, TOO_NARROW),
                    new IssueHazard(RAMP, NO_LANDING),
                    new IssueHazard(RAMP, INSUFFICIENT_LANDING_AREA),
                    new IssueHazard(RAMP, SLIPPERY_SURFACE),
                    new IssueHazard(RAMP, MISSING_HANDRAIL),
                    new IssueHazard(RAMP, HANDRAIL_TOO_LOW))),

    AVOID_NARROW_SIDEWALKS(
            "Avoid narrow sidewalks",
            "Routes will avoid sidewalks reported as too narrow for accessible passage.",
            Set.of(new IssueHazard(SIDEWALK, TOO_NARROW))),

    AVOID_LOW_CLEARANCE(
            "Avoid low overhead clearance",
            "Routes will avoid sidewalk segments with low-hanging branches, signs, or other overhead obstructions.",
            Set.of(new IssueHazard(SIDEWALK, INSUFFICIENT_CLEARANCE))),

    AVOID_SLIPPERY_SURFACES(
            "Avoid slippery surfaces",
            "Routes will avoid ramps, sidewalks, and stairs reported as slippery or lacking anti-slip surfaces.",
            Set.of(
                    new IssueHazard(RAMP, SLIPPERY_SURFACE),
                    new IssueHazard(SIDEWALK, SLIPPERY_SURFACE),
                    new IssueHazard(STAIR, SLIPPERY_SURFACE),
                    new IssueHazard(STAIR, NO_ANTI_SLIP))),

    REQUIRE_HANDRAILS(
            "Require handrails on stairs and ramps",
            "Routes will avoid stairs and ramps reported as missing handrails or having handrails mounted too low.",
            Set.of(
                    new IssueHazard(RAMP, MISSING_HANDRAIL),
                    new IssueHazard(RAMP, HANDRAIL_TOO_LOW),
                    new IssueHazard(STAIR, MISSING_HANDRAIL),
                    new IssueHazard(STAIR, HANDRAIL_TOO_LOW))),

    AVOID_BLOCKED_OR_MISSING_SIDEWALKS(
            "Avoid blocked, missing, or damaged sidewalks",
            "Routes will avoid sidewalk segments reported as blocked by obstacles, missing entirely, having uneven surfaces, or having unramped level differences.",
            Set.of(
                    new IssueHazard(SIDEWALK, BLOCKED),
                    new IssueHazard(SIDEWALK, MISSING),
                    new IssueHazard(SIDEWALK, UNEVEN_SURFACE),
                    new IssueHazard(SIDEWALK, UNRAMPED_LEVEL_DIFFERENCE))),

    AVOID_UNSAFE_STAIRS(
            "Avoid unsafe stairs (when stairs are unavoidable)",
            "Routes will avoid stairs reported with high risers, shallow treads, missing landings, open risers, irregular steps, or missing nosing strips.",
            Set.of(
                    new IssueHazard(STAIR, OPEN_RISERS),
                    new IssueHazard(STAIR, RISER_TOO_HIGH),
                    new IssueHazard(STAIR, TREAD_TOO_SHALLOW),
                    new IssueHazard(STAIR, NO_LANDING),
                    new IssueHazard(STAIR, IRREGULAR_STEPS),
                    new IssueHazard(STAIR, MISSING_NOSING_STRIP))),

    AVOID_MISSING_TACTILE_PAVING(
            "Avoid sidewalks lacking tactile paving",
            "Routes will avoid sidewalks reported as missing tactile guidance strips for visually impaired users.",
            Set.of(new IssueHazard(SIDEWALK, NO_TACTILE_PAVING))),

    REQUIRE_CURB_RAMPS(
            "Prefer crossings with curb ramps",
            "Routes will avoid crossings reported as missing curb ramps, having unsafe slopes, with raised lips, or lacking tactile warnings.",
            Set.of(
                    new IssueHazard(CURB_RAMP, MISSING),
                    new IssueHazard(CURB_RAMP, TOO_STEEP),
                    new IssueHazard(CURB_RAMP, TOO_NARROW),
                    new IssueHazard(CURB_RAMP, RAISED_LIP),
                    new IssueHazard(CURB_RAMP, NO_TACTILE_WARNING))),

    AVOID_INACCESSIBLE_CROSSINGS(
            "Avoid inaccessible pedestrian crossings",
            "Routes will avoid pedestrian crossings reported as missing, blocked, lacking dropped curbs, lacking tactile paving, lacking audio signals, or lacking pedestrian refuges.",
            Set.of(
                    new IssueHazard(PEDESTRIAN_CROSSING, MISSING),
                    new IssueHazard(PEDESTRIAN_CROSSING, BLOCKED),
                    new IssueHazard(PEDESTRIAN_CROSSING, NO_DROPPED_CURB),
                    new IssueHazard(PEDESTRIAN_CROSSING, NO_TACTILE_PAVING),
                    new IssueHazard(PEDESTRIAN_CROSSING, NO_AUDIO_SIGNAL),
                    new IssueHazard(PEDESTRIAN_CROSSING, SIGNAL_TOO_SHORT),
                    new IssueHazard(PEDESTRIAN_CROSSING, FADED_MARKINGS),
                    new IssueHazard(PEDESTRIAN_CROSSING, NO_PEDESTRIAN_REFUGE)));

    private final String label;
    private final String description;
    private final Set<IssueHazard> hazards;

    RoutingConstraint(String label, String description, Set<IssueHazard> hazards) {
        this.label = label;
        this.description = description;
        this.hazards = hazards;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public Set<IssueHazard> getHazards() {
        return hazards;
    }

    /**
     * Union the hazard sets of the given constraints. Empty / null input → empty set
     * (caller falls back to baseline behaviour).
     */
    public static Set<IssueHazard> expand(Set<RoutingConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            return Set.of();
        }
        return constraints.stream()
                .flatMap(c -> c.hazards.stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
