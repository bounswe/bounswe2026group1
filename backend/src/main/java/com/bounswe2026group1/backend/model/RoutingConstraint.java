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
                    new IssueHazard(STAIR, OPEN_RISERS))),

    REQUIRE_RAMPS(
            "Prefer routes with ramps over steps",
            "Treats reports of missing ramps as obstacles to route around.",
            Set.of(new IssueHazard(RAMP, MISSING))),

    AVOID_UNSAFE_RAMPS(
            "Avoid unsafe ramps",
            "Routes will avoid ramps reported as too steep, too narrow, missing handrails, missing landings, or slippery.",
            Set.of(
                    new IssueHazard(RAMP, TOO_STEEP),
                    new IssueHazard(RAMP, TOO_NARROW),
                    new IssueHazard(RAMP, NO_LANDING),
                    new IssueHazard(RAMP, SLIPPERY_SURFACE),
                    new IssueHazard(RAMP, MISSING_HANDRAIL))),

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
            "Routes will avoid stairs and ramps reported as missing handrails.",
            Set.of(
                    new IssueHazard(RAMP, MISSING_HANDRAIL),
                    new IssueHazard(STAIR, MISSING_HANDRAIL))),

    AVOID_BLOCKED_OR_MISSING_SIDEWALKS(
            "Avoid blocked or missing sidewalks",
            "Routes will avoid sidewalk segments reported as blocked by obstacles or missing entirely.",
            Set.of(
                    new IssueHazard(SIDEWALK, BLOCKED),
                    new IssueHazard(SIDEWALK, MISSING))),

    AVOID_UNSAFE_STAIRS(
            "Avoid unsafe stairs (when stairs are unavoidable)",
            "Routes will avoid stairs reported with high risers, shallow treads, missing landings, or open risers.",
            Set.of(
                    new IssueHazard(STAIR, OPEN_RISERS),
                    new IssueHazard(STAIR, RISER_TOO_HIGH),
                    new IssueHazard(STAIR, TREAD_TOO_SHALLOW),
                    new IssueHazard(STAIR, NO_LANDING))),

    AVOID_DAMAGED_TACTILE_PAVING(
            "Avoid damaged tactile paving",
            "Routes will avoid sidewalks with reported tactile-paving issues that may misdirect visually impaired users.",
            // Currently maps to NO_TACTILE_PAVING. The IssueType will be renamed to
            // TACTILE_PAVING_DAMAGED in a follow-up; only this hazard set updates.
            Set.of(new IssueHazard(SIDEWALK, NO_TACTILE_PAVING)));

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
