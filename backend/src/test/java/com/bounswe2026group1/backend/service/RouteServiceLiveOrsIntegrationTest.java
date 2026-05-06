package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.model.*;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.support.AbstractPostgisIntegrationTest;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live end-to-end integration test for RouteService with real ORS calls.
 *
 * Scenario:
 * 1) Baseline route check with no reports,
 * 2) Add a negative report and verify walking route behavior changes,
 * 3) Replace with a ramp report and verify ramp-assisted route behavior.
 *
 * This test runs only when ORS_API_KEY is present in the environment.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ORS_API_KEY", matches = "(?s).+")
class RouteServiceLiveOrsIntegrationTest extends AbstractPostgisIntegrationTest {

    private static final double START_LAT = 41.085520;
    private static final double START_LON = 29.044523;
    private static final double END_LAT = 41.086452;
    private static final double END_LON = 29.044751;

    private static final double NEGATIVE_LAT = 41.085784;
    private static final double NEGATIVE_LON = 29.044526;

    private static final double RAMP_LAT = 41.085689;
    private static final double RAMP_LON = 29.044544;

    @Autowired
    private RouteService routeService;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    private RegisteredUser testUser;

    @BeforeEach
    void cleanStateAndPrepareUser() {
        reportRepository.deleteAll();

        testUser = new RegisteredUser();
        testUser.setName("Route Tester");
        testUser.setEmail("route.tester+" + UUID.randomUUID() + "@example.com");
        testUser.setPassword("StrongP@ss1");
        testUser.setRole(UserRole.USER);
        testUser = registeredUserRepository.save(testUser);
    }

    @Test
    void routeScenario_withNegativeThenRampReport_behavesAsExpected() {
        RouteRequest request = new RouteRequest(START_LAT, START_LON, END_LAT, END_LON, null);

        // 1) Baseline: no reports
        List<RouteResponse> baselineRoutes = routeService.getRouteOptions(request);
        RouteResponse baselineWalking = byLabel(baselineRoutes, "Fastest Route");
        RouteResponse baselineWheelchair = byLabel(baselineRoutes, "Wheelchair Route");

        assertNotNull(baselineWalking, "Baseline walking route should exist");
        assertNotNull(baselineWheelchair, "Baseline wheelchair route should exist");
        assertNotEquals(
                baselineWalking.getDurationSeconds(),
                baselineWheelchair.getDurationSeconds(),
                "Walking and wheelchair durations should be different when there are no reports");

        // 2) Add negative report and ask route again
        Report negativeReport = new Report(
                testUser,
                GeoUtils.point4326(NEGATIVE_LAT, NEGATIVE_LON),
                "Test negative obstacle near path",
                ReportType.OBSTACLE,
                ReportEnvironment.OUTDOOR);
        negativeReport.setStatus(ReportStatus.VERIFIED);
        reportRepository.save(negativeReport);

        List<RouteResponse> negativeRoutes = routeService.getRouteOptions(request);
        RouteResponse negativeWalking = byLabel(negativeRoutes, "Fastest Route");
        RouteResponse accessibleAfterNegative = byLabel(negativeRoutes, "Accessible Route");

        assertNotNull(negativeWalking, "Walking route should still exist after negative report");
        boolean walkingChanged = accessibleAfterNegative != null
                || !safeGeometry(negativeWalking).equals(safeGeometry(baselineWalking));
        assertTrue(
                walkingChanged,
                "After adding the negative report, walking path should change (or an accessible walking route should appear)");

        // 3) Remove negative report, add ramp report, ask route again
        reportRepository.deleteById(negativeReport.getReportId());

        Report rampReport = new Report(
                testUser,
                GeoUtils.point4326(RAMP_LAT, RAMP_LON),
                "Test ramp near path",
                ReportType.FEATURE,
                ReportEnvironment.OUTDOOR);
        rampReport.setStatus(ReportStatus.VERIFIED);
        rampReport.setEntryPoint(new Location(41.085700, 29.044550));
        rampReport.setExitPoint(new Location(41.085650, 29.044500));
        reportRepository.save(rampReport);

        List<RouteResponse> rampRoutes = routeService.getRouteOptions(request);
        RouteResponse rampAssisted = byLabel(rampRoutes, "Ramp-Assisted Route");
        RouteResponse wheelchairAfterRamp = byLabel(rampRoutes, "Wheelchair Route");

        assertNotNull(rampAssisted, "Ramp-assisted route should be generated when a ramp report exists");
        assertNotNull(wheelchairAfterRamp, "Wheelchair route should still be generated");
        assertTrue(
                rampAssisted.getDurationSeconds() < wheelchairAfterRamp.getDurationSeconds(),
                "Ramp-assisted route duration should be shorter than wheelchair route duration");
    }

    private static RouteResponse byLabel(List<RouteResponse> routes, String label) {
        return routes.stream()
                .filter(r -> label.equals(r.getRouteLabel()))
                .findFirst()
                .orElse(null);
    }

    private static String safeGeometry(RouteResponse route) {
        return route.getGeometry() == null ? "" : route.getGeometry();
    }
}
