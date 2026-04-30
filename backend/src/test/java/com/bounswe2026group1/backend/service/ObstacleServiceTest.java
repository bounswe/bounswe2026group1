package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RampReport;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.repository.RampReportRepository;
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
    @Mock
    private RampReportRepository rampReportRepository;

    private ObstacleService obstacleService;

    @BeforeEach
    void setUp() {
        obstacleService = new ObstacleService(reportRepository, rampReportRepository,
                JsonMapper.builder().build());
    }

    // -------------------------------------------------------------------------
    // findClosestRampInBoundingBox — optimal ramp selection
    // -------------------------------------------------------------------------

    @Test
    void findClosestRamp_returnsNull_whenNoCandidates() {
        when(rampReportRepository.findRampsInBoundingBoxWithStatuses(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of());

        assertNull(obstacleService.findClosestRampInBoundingBox(START, END));
    }

    @Test
    void findClosestRamp_returnsSingleRamp_whenOnlyOneCandidate() {
        RampReport ramp = rampNear(41.0840, 29.0460, 41.0838, 29.0465);
        when(rampReportRepository.findRampsInBoundingBoxWithStatuses(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(ramp));

        assertSame(ramp, obstacleService.findClosestRampInBoundingBox(START, END));
    }

    @Test
    void findClosestRamp_picksRampWithLowerTotalRouteEstimate_notJustNearestToStart() {
        // rampA is very close to START but far from END
        RampReport rampA = rampNear(41.0849, 29.0451,  // entry ≈ 11 m from START
                                    41.0700, 29.0200);  // exit far from END

        // rampB is slightly farther from START but its exit is close to END
        RampReport rampB = rampNear(41.0845, 29.0455,  // entry ≈ 60 m from START
                                    41.0821, 29.0499);  // exit ≈ 14 m from END

        when(rampReportRepository.findRampsInBoundingBoxWithStatuses(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(rampA, rampB));

        RampReport best = obstacleService.findClosestRampInBoundingBox(START, END);

        // rampA: start→entry ≈ 11m + exit→end is huge (≈ 3+ km)
        // rampB: start→entry ≈ 60m + exit→end ≈ 14m → far smaller total
        assertSame(rampB, best);
    }

    @Test
    void findClosestRamp_skipsRampsWithNullEntryOrExit() {
        RampReport bad = new RampReport();  // no entry/exit set
        RampReport good = rampNear(41.0840, 29.0460, 41.0838, 29.0465);

        when(rampReportRepository.findRampsInBoundingBoxWithStatuses(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyList()))
                .thenReturn(List.of(bad, good));

        assertSame(good, obstacleService.findClosestRampInBoundingBox(START, END));
    }

    // -------------------------------------------------------------------------
    // buildAvoidPolygons — geodesic buffer
    // -------------------------------------------------------------------------

    @Test
    void buildAvoidPolygons_returnsNull_whenNoObstacles() {
        when(reportRepository.findByTagInAndStatusIn(anyList(), anyList()))
                .thenReturn(List.of());

        assertNull(obstacleService.buildAvoidPolygons());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static RampReport rampNear(double entryLat, double entryLon,
                                       double exitLat,  double exitLon) {
        RampReport r = new RampReport();
        r.setEntryPoint(new Location(entryLat, entryLon));
        r.setExitPoint(new Location(exitLat, exitLon));
        r.setStatus(ReportStatus.VERIFIED);
        return r;
    }
}
