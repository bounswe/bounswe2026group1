package com.bounswe2026group1.backend.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoutingConstraintTest {

    @Test
    void everyConstraint_carriesAtLeastOneHazard() {
        for (RoutingConstraint c : RoutingConstraint.values()) {
            assertNotNull(c.getHazards(), c.name() + " hazard set must not be null");
            assertFalse(c.getHazards().isEmpty(), c.name() + " must have at least one hazard");
            assertNotNull(c.getLabel(), c.name() + " must have a label");
            assertNotNull(c.getDescription(), c.name() + " must have a description");
        }
    }

    @Test
    void hazardSets_areInternallyConsistent() {
        // Every hazard's IssueType must be valid for its ObjectType — catches a typo
        // in the constraint definition that would otherwise generate dead polygons.
        for (RoutingConstraint c : RoutingConstraint.values()) {
            for (IssueHazard h : c.getHazards()) {
                assertTrue(h.issueType().isValidFor(h.objectType()),
                        c.name() + " maps a hazard (" + h.objectType() + ", " + h.issueType()
                                + ") that the IssueType.validFor catalog rejects.");
            }
        }
    }

    @Test
    void expand_emptyOrNullInput_returnsEmptySet() {
        assertTrue(RoutingConstraint.expand(null).isEmpty());
        assertTrue(RoutingConstraint.expand(Set.of()).isEmpty());
        assertTrue(RoutingConstraint.expand(EnumSet.noneOf(RoutingConstraint.class)).isEmpty());
    }

    @Test
    void expand_singleConstraint_returnsItsHazards() {
        Set<IssueHazard> result = RoutingConstraint.expand(EnumSet.of(RoutingConstraint.AVOID_LOW_CLEARANCE));
        assertEquals(Set.of(new IssueHazard(ObjectType.SIDEWALK, IssueType.INSUFFICIENT_CLEARANCE)), result);
    }

    @Test
    void expand_multipleConstraints_unionsHazardSets() {
        Set<IssueHazard> result = RoutingConstraint.expand(EnumSet.of(
                RoutingConstraint.REQUIRE_HANDRAILS,
                RoutingConstraint.AVOID_LOW_CLEARANCE));
        assertEquals(Set.of(
                new IssueHazard(ObjectType.RAMP, IssueType.MISSING_HANDRAIL),
                new IssueHazard(ObjectType.RAMP, IssueType.HANDRAIL_TOO_LOW),
                new IssueHazard(ObjectType.STAIR, IssueType.MISSING_HANDRAIL),
                new IssueHazard(ObjectType.STAIR, IssueType.HANDRAIL_TOO_LOW),
                new IssueHazard(ObjectType.SIDEWALK, IssueType.INSUFFICIENT_CLEARANCE)),
                result);
    }

    @Test
    void expand_overlappingConstraints_dedupesHazards() {
        // Both AVOID_UNSAFE_RAMPS and REQUIRE_HANDRAILS contain (RAMP, MISSING_HANDRAIL).
        // The union must not duplicate it.
        Set<IssueHazard> result = RoutingConstraint.expand(EnumSet.of(
                RoutingConstraint.AVOID_UNSAFE_RAMPS,
                RoutingConstraint.REQUIRE_HANDRAILS));
        long handrailHazards = result.stream()
                .filter(h -> h.objectType() == ObjectType.RAMP && h.issueType() == IssueType.MISSING_HANDRAIL)
                .count();
        assertEquals(1, handrailHazards, "Overlap between constraints must not duplicate hazards");
    }

    @Test
    void avoidStairs_coversEveryStairIssue() {
        // The AVOID_STAIRS constraint is meant to be a catch-all for any reported
        // stair-related issue. If a new stair IssueType is added later, this test
        // surfaces the gap so it gets explicitly added to the constraint.
        Set<IssueHazard> hazards = RoutingConstraint.AVOID_STAIRS.getHazards();
        Set<IssueType> stairIssues = Set.of(
                IssueType.MISSING_HANDRAIL, IssueType.NO_LANDING, IssueType.TOO_NARROW,
                IssueType.SLIPPERY_SURFACE, IssueType.RISER_TOO_HIGH, IssueType.TREAD_TOO_SHALLOW,
                IssueType.NO_ANTI_SLIP, IssueType.OPEN_RISERS,
                IssueType.IRREGULAR_STEPS, IssueType.MISSING_NOSING_STRIP, IssueType.HANDRAIL_TOO_LOW);
        for (IssueType issue : stairIssues) {
            assertTrue(hazards.contains(new IssueHazard(ObjectType.STAIR, issue)),
                    "AVOID_STAIRS missing (STAIR, " + issue + ")");
        }
    }
}
