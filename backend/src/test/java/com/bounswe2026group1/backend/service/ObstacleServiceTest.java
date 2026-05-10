package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportObject;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.repository.ReportRepository;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObstacleServiceTest {

    // Bogazici University campus area
    private static final Location START = new Location(41.0850, 29.0450);
    private static final Location END   = new Location(41.0820, 29.0500);

    @Mock
    private ReportRepository reportRepository;

    private ObstacleService obstacleService;

    @BeforeEach
    void setUp() {
        obstacleService = new ObstacleService(reportRepository, JsonMapper.builder().build());
    }

    // -------------------------------------------------------------------------
    // findClosestRampInBoundingBox — optimal ramp selection
    // -------------------------------------------------------------------------

    @Test
    void findClosestRamp_returnsNull_whenNoCandidates() {
        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE.name()),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of());

        assertNull(obstacleService.findClosestRampInBoundingBox(START, END));
    }

    @Test
    void findClosestRamp_returnsSingleRamp_whenOnlyOneCandidate() {
        Report ramp = rampNear(41.0840, 29.0460, 41.0838, 29.0465);
        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE.name()),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(ramp));

        assertSame(ramp, obstacleService.findClosestRampInBoundingBox(START, END));
    }

    @Test
    void findClosestRamp_picksRampWithLowerTotalRouteEstimate_notJustNearestToStart() {
        // rampA is very close to START but far from END
        Report rampA = rampNear(41.0849, 29.0451,  // entry ≈ 11 m from START
                                41.0700, 29.0200);  // exit far from END

        // rampB is slightly farther from START but its exit is close to END
        Report rampB = rampNear(41.0845, 29.0455,  // entry ≈ 60 m from START
                                41.0821, 29.0499);  // exit ≈ 14 m from END

        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE.name()),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(rampA, rampB));

        Report best = obstacleService.findClosestRampInBoundingBox(START, END);

        // rampA: start→entry ≈ 11m + exit→end is huge (≈ 3+ km)
        // rampB: start→entry ≈ 60m + exit→end ≈ 14m → far smaller total
        assertSame(rampB, best);
    }

    @Test
    void findClosestRamp_skipsRampsWithNullEntryOrExit() {
        Report bad = new Report();  // no entry/exit set
        bad.setReportType(ReportType.FEATURE);
        bad.getObjects().add(new ReportObject(bad, ObjectType.RAMP, Set.of(), null));
        Report good = rampNear(41.0840, 29.0460, 41.0838, 29.0465);

        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE.name()),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(bad, good));

        assertSame(good, obstacleService.findClosestRampInBoundingBox(START, END));
    }

    @Test
    void findClosestRamp_skipsFeatureReportsWithoutRampObject() {
        Report rampReport = rampNear(41.0840, 29.0460, 41.0838, 29.0465);
        // FEATURE report with ELEVATOR object — should be skipped (not a RAMP)
        Report elevatorFeature = new Report();
        elevatorFeature.setReportType(ReportType.FEATURE);
        elevatorFeature.setEntryPoint(new Location(41.0841, 29.0461));
        elevatorFeature.setExitPoint(new Location(41.0839, 29.0466));
        elevatorFeature.setStatus(ReportStatus.VERIFIED);
        elevatorFeature.getObjects().add(new ReportObject(elevatorFeature, ObjectType.ELEVATOR, Set.of(), null));

        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE.name()),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(elevatorFeature, rampReport));

        assertSame(rampReport, obstacleService.findClosestRampInBoundingBox(START, END));
    }

    // -------------------------------------------------------------------------
    // buildAvoidPolygons — geodesic buffer
    // -------------------------------------------------------------------------

    @Test
    void buildAvoidPolygons_returnsNull_whenNoObstacles() {
        when(reportRepository.findByTypeAndStatusIn(eq(ReportType.OBSTACLE), anyList()))
                .thenReturn(List.of());

        assertNull(obstacleService.buildAvoidPolygons());
    }

    // -------------------------------------------------------------------------
    // buildAvoidPolygons(constraints) — preference-aware filtering (#365)
    // -------------------------------------------------------------------------

    @Test
    void buildAvoidPolygons_emptyConstraints_includesEveryVerifiedObstacle() {
        // Anonymous / NONE-preset baseline: all VERIFIED obstacles become polygons.
        Report stairsReport = obstacleAt(41.0850, 29.0451, ObjectType.STAIR, IssueType.MISSING_HANDRAIL);
        Report blockedSidewalk = obstacleAt(41.0851, 29.0452, ObjectType.SIDEWALK, IssueType.BLOCKED);
        when(reportRepository.findByTypeAndStatusIn(eq(ReportType.OBSTACLE), anyList()))
                .thenReturn(List.of(stairsReport, blockedSidewalk));

        ObjectNode polygons = obstacleService.buildAvoidPolygons(Set.of());

        assertNotNull(polygons);
        assertEquals(2, polygons.get("coordinates").size(),
                "Empty constraints must preserve baseline (every VERIFIED obstacle becomes a polygon)");
    }

    @Test
    void buildAvoidPolygons_withConstraintMatchingOnlyOneReport_filtersOutTheRest() {
        Report stairsReport = obstacleAt(41.0850, 29.0451, ObjectType.STAIR, IssueType.MISSING_HANDRAIL);
        Report blockedSidewalk = obstacleAt(41.0851, 29.0452, ObjectType.SIDEWALK, IssueType.BLOCKED);
        when(reportRepository.findByTypeAndStatusIn(eq(ReportType.OBSTACLE), anyList()))
                .thenReturn(List.of(stairsReport, blockedSidewalk));

        // Only AVOID_BLOCKED_OR_MISSING_SIDEWALKS should match the blocked sidewalk; the stairs
        // report's hazards aren't in this constraint's set.
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                EnumSet.of(RoutingConstraint.AVOID_BLOCKED_OR_MISSING_SIDEWALKS));

        assertNotNull(polygons);
        assertEquals(1, polygons.get("coordinates").size(),
                "Constraint should narrow the avoid set to reports whose objects match a hazard");
    }

    @Test
    void buildAvoidPolygons_withConstraintMatchingNoReports_returnsNull() {
        Report blockedSidewalk = obstacleAt(41.0851, 29.0452, ObjectType.SIDEWALK, IssueType.BLOCKED);
        when(reportRepository.findByTypeAndStatusIn(eq(ReportType.OBSTACLE), anyList()))
                .thenReturn(List.of(blockedSidewalk));

        // REQUIRE_HANDRAILS only cares about (RAMP|STAIR, MISSING_HANDRAIL); blocked sidewalks
        // don't match. With no surviving reports the polygon set should collapse to null
        // (which lets ORS skip the avoidance call entirely).
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                EnumSet.of(RoutingConstraint.REQUIRE_HANDRAILS));

        assertNull(polygons);
    }

    @Test
    void buildAvoidPolygons_withWheelchairUserPresetConstraints_filtersBlindOnlyHazardsOut() {
        // A blind-user-relevant report: tactile paving missing
        Report tactileReport = obstacleAt(41.0850, 29.0451, ObjectType.SIDEWALK, IssueType.NO_TACTILE_PAVING);
        // A wheelchair-user-relevant report: missing ramp
        Report rampReport = obstacleAt(41.0852, 29.0453, ObjectType.RAMP, IssueType.MISSING);
        when(reportRepository.findByTypeAndStatusIn(eq(ReportType.OBSTACLE), anyList()))
                .thenReturn(List.of(tactileReport, rampReport));

        // Apply the WHEELCHAIR_USER preset's constraints. Only the ramp report should
        // contribute a polygon — tactile paving isn't in this preset's hazard set.
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                com.bounswe2026group1.backend.model.RoutingPreset.WHEELCHAIR_USER.getConstraints());

        assertNotNull(polygons);
        assertEquals(1, polygons.get("coordinates").size());
    }

    // -------------------------------------------------------------------------
    // null constraints — explicit "skip avoidance" path (issue #544)
    // -------------------------------------------------------------------------

    @Test
    void buildAvoidPolygons_nullConstraints_skipsAvoidanceWithoutHittingRepository() {
        // Signed-in users with RoutingPreset.NONE pass null to opt out of
        // avoidance entirely. The method must short-circuit before any DB call
        // so the route service can cheaply skip the Accessible Route alternative.
        ObjectNode polygons = obstacleService.buildAvoidPolygons((Set<RoutingConstraint>) null);

        assertNull(polygons,
                "null constraints must produce no avoid polygons (issue #544)");
        verify(reportRepository, never()).findByTypeAndStatusIn(any(), any());
    }

    @Test
    void findObstaclesOnPath_nullConstraints_returnsEmptyWithoutHittingRepository() {
        // Same null-as-opt-out contract for the on-path check that drives the
        // hasObstacles flag — a NONE-preset user should never see warnings.
        List<Report> obstacles = obstacleService.findObstaclesOnPath(
                List.of(START, END), (Set<RoutingConstraint>) null);

        assertTrue(obstacles.isEmpty(),
                "null constraints must yield no on-path obstacles (issue #544)");
        verify(reportRepository, never()).findReportsInBoundingBoxWithStatuses(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList());
    }

    @Test
    void buildAvoidPolygons_constraintsCannotWidenSetPastBaseline() {
        // The baseline includes every VERIFIED obstacle. Constraints can only narrow,
        // never widen — so the count with constraints applied must always be ≤ baseline.
        Report stairsReport = obstacleAt(41.0850, 29.0451, ObjectType.STAIR, IssueType.MISSING_HANDRAIL);
        Report tactileReport = obstacleAt(41.0851, 29.0452, ObjectType.SIDEWALK, IssueType.NO_TACTILE_PAVING);
        Report rampReport = obstacleAt(41.0852, 29.0453, ObjectType.RAMP, IssueType.TOO_STEEP);
        when(reportRepository.findByTypeAndStatusIn(eq(ReportType.OBSTACLE), anyList()))
                .thenReturn(List.of(stairsReport, tactileReport, rampReport));

        int baseline = obstacleService.buildAvoidPolygons(Set.of()).get("coordinates").size();
        int withConstraints = obstacleService.buildAvoidPolygons(
                EnumSet.of(RoutingConstraint.AVOID_STAIRS)).get("coordinates").size();

        assertEquals(3, baseline);
        assertTrue(withConstraints <= baseline,
                "Applying a constraint must never widen the avoid set past the baseline");
        assertEquals(1, withConstraints,
                "AVOID_STAIRS should match only the stair report");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Report rampNear(double entryLat, double entryLon,
                                   double exitLat,  double exitLon) {
        Report r = new Report();
        r.setReportType(ReportType.FEATURE);
        r.setEntryPoint(new Location(entryLat, entryLon));
        r.setExitPoint(new Location(exitLat, exitLon));
        r.setStatus(ReportStatus.VERIFIED);
        r.getObjects().add(new ReportObject(r, ObjectType.RAMP, Set.of(), null));
        return r;
    }

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static Report obstacleAt(double lat, double lon, ObjectType objectType, IssueType issue) {
        Report r = new Report();
        r.setReportType(ReportType.OBSTACLE);
        r.setStatus(ReportStatus.VERIFIED);
        r.setLocation(GEOMETRY_FACTORY.createPoint(new org.locationtech.jts.geom.Coordinate(lon, lat)));
        r.getObjects().add(new ReportObject(r, objectType, EnumSet.of(issue), null));
        return r;
    }
}
