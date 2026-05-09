package com.bounswe2026group1.backend.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoutingPresetTest {

    @Test
    void detectFromConstraints_emptyOrNull_returnsNone() {
        assertEquals(RoutingPreset.NONE, RoutingPreset.detectFromConstraints(null));
        assertEquals(RoutingPreset.NONE, RoutingPreset.detectFromConstraints(Set.of()));
        assertEquals(RoutingPreset.NONE,
                RoutingPreset.detectFromConstraints(EnumSet.noneOf(RoutingConstraint.class)));
    }

    @Test
    void detectFromConstraints_exactPresetMatch_returnsThatPreset() {
        for (RoutingPreset preset : RoutingPreset.values()) {
            if (preset == RoutingPreset.NONE || preset == RoutingPreset.CUSTOM) continue;
            assertEquals(preset,
                    RoutingPreset.detectFromConstraints(preset.getConstraints()),
                    preset.name() + " should round-trip through detectFromConstraints");
        }
    }

    @Test
    void detectFromConstraints_arbitrarySet_returnsCustom() {
        Set<RoutingConstraint> custom = EnumSet.of(
                RoutingConstraint.AVOID_LOW_CLEARANCE,
                RoutingConstraint.REQUIRE_HANDRAILS);
        // The mix above doesn't match any preset bundle.
        assertEquals(RoutingPreset.CUSTOM, RoutingPreset.detectFromConstraints(custom));
    }

    @Test
    void detectFromConstraints_supersetOfPreset_returnsCustom() {
        // WHEELCHAIR_USER plus an extra constraint → no longer matches any preset.
        Set<RoutingConstraint> superset = new HashSet<>(RoutingPreset.WHEELCHAIR_USER.getConstraints());
        superset.add(RoutingConstraint.AVOID_LOW_CLEARANCE);
        assertEquals(RoutingPreset.CUSTOM, RoutingPreset.detectFromConstraints(superset));
    }

    @Test
    void detectFromConstraints_subsetOfPreset_returnsCustom() {
        // Drop one constraint from WHEELCHAIR_USER — should not be auto-detected as
        // any preset because the bundle doesn't match exactly.
        Set<RoutingConstraint> subset = new HashSet<>(RoutingPreset.WHEELCHAIR_USER.getConstraints());
        subset.remove(subset.iterator().next());
        assertEquals(RoutingPreset.CUSTOM, RoutingPreset.detectFromConstraints(subset));
    }

    @Test
    void detectFromConstraints_neverReturnsCustomItself() {
        // CUSTOM is a sentinel — its own (empty) constraint set must not auto-detect to itself,
        // it should auto-detect to NONE.
        assertEquals(RoutingPreset.NONE,
                RoutingPreset.detectFromConstraints(RoutingPreset.CUSTOM.getConstraints()));
    }

    @Test
    void presetConstraints_areAllPathLevel_noDestinationOrIndoorTuples() {
        // Path-level discipline check: presets should only bundle constraints that
        // map to outdoor path-segment hazards (sidewalk/ramp/stair/pedestrian
        // crossing/curb ramp). Door and elevator hazards are destination-level;
        // washroom and room-sign hazards are indoor-only — both categories live
        // outside the routing-preference model.
        for (RoutingPreset preset : RoutingPreset.values()) {
            for (RoutingConstraint c : preset.getConstraints()) {
                for (IssueHazard h : c.getHazards()) {
                    assertNotEquals(ObjectType.DOOR, h.objectType(),
                            preset.name() + " bundles " + c.name()
                                    + " which contains a DOOR hazard — destination-level, not path-level");
                    assertNotEquals(ObjectType.ELEVATOR, h.objectType(),
                            preset.name() + " bundles " + c.name()
                                    + " which contains an ELEVATOR hazard — destination-level, not path-level");
                    assertNotEquals(ObjectType.WASHROOM, h.objectType(),
                            preset.name() + " bundles " + c.name()
                                    + " which contains a WASHROOM hazard — indoor, not path-level");
                    assertNotEquals(ObjectType.ROOM_SIGN, h.objectType(),
                            preset.name() + " bundles " + c.name()
                                    + " which contains a ROOM_SIGN hazard — indoor, not path-level");
                }
            }
        }
    }

    @Test
    void wheelchairPreset_defaultsToWheelchairTravelMode() {
        assertEquals(TravelMode.WHEELCHAIR, RoutingPreset.WHEELCHAIR_USER.getDefaultTravelMode());
    }

    @Test
    void blindAndMobilityPresets_defaultToWalking() {
        assertEquals(TravelMode.WALKING, RoutingPreset.BLIND_OR_LOW_VISION.getDefaultTravelMode());
        assertEquals(TravelMode.WALKING, RoutingPreset.MOBILITY_LIMITED.getDefaultTravelMode());
    }

    @Test
    void nonePreset_hasNoDefaultTravelMode() {
        assertNull(RoutingPreset.NONE.getDefaultTravelMode());
    }
}
