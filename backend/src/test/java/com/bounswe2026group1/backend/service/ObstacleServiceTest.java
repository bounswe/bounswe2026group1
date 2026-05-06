package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportCategory;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.repository.ReportRepository;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
                eq(ReportType.FEATURE),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of());

        assertNull(obstacleService.findClosestRampInBoundingBox(START, END));
    }

    @Test
    void findClosestRamp_returnsSingleRamp_whenOnlyOneCandidate() {
        Report ramp = rampNear(41.0840, 29.0460, 41.0838, 29.0465);
        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE),
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
                eq(ReportType.FEATURE),
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
        bad.setCategory(wheelchairFeatureCategory());
        Report good = rampNear(41.0840, 29.0460, 41.0838, 29.0465);

        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(bad, good));

        assertSame(good, obstacleService.findClosestRampInBoundingBox(START, END));
    }

    @Test
    void findClosestRamp_skipsRampsWhoseCategoryDoesNotAffectWheelchair() {
        Report wheelchairRamp = rampNear(41.0840, 29.0460, 41.0838, 29.0465);
        Report visualOnlyFeature = rampNear(41.0841, 29.0461, 41.0839, 29.0466);
        ReportCategory visualOnly = new ReportCategory();
        visualOnly.setType(ReportType.FEATURE);
        visualOnly.setAffectedProfiles("[\"VISUAL_IMPAIRED\"]");
        visualOnlyFeature.setCategory(visualOnly);

        when(reportRepository.findByTypeInBoundingBoxWithStatuses(
                eq(ReportType.FEATURE),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(visualOnlyFeature, wheelchairRamp));

        assertSame(wheelchairRamp, obstacleService.findClosestRampInBoundingBox(START, END));
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
    // Helpers
    // -------------------------------------------------------------------------

    private static Report rampNear(double entryLat, double entryLon,
                                   double exitLat,  double exitLon) {
        Report r = new Report();
        r.setEntryPoint(new Location(entryLat, entryLon));
        r.setExitPoint(new Location(exitLat, exitLon));
        r.setStatus(ReportStatus.VERIFIED);
        r.setCategory(wheelchairFeatureCategory());
        return r;
    }

    private static ReportCategory wheelchairFeatureCategory() {
        ReportCategory c = new ReportCategory();
        c.setType(ReportType.FEATURE);
        // affectedProfiles is a JSON-string column; the service does a substring
        // contains("WHEELCHAIR") check on it.
        c.setAffectedProfiles("[\"WHEELCHAIR\",\"STROLLER\"]");
        return c;
    }
}
