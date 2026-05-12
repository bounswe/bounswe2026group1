package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.TravelMode;

import java.util.EnumSet;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private OrsRoutingClient orsRoutingClient;
    @Mock
    private ObstacleService obstacleService;

    @InjectMocks
    private RouteService routeService;

    @Test
    void accessibleRoute_isComputed_whenAvoidPolygonsExist_evenIfFastestRouteHasNoObstacles() {
        // Avoid polygons exist (some obstacles are reported in the area)
        ObjectNode avoidPolygons = JsonMapper.builder().build().createObjectNode();
        when(obstacleService.buildAvoidPolygons(any())).thenReturn(avoidPolygons);

        // But the fastest route happens not to cross any of them
        when(obstacleService.findObstaclesOnPath(any(), any())).thenReturn(List.of());
        when(obstacleService.findRampOnPath(any())).thenReturn(null);

        RoutingDirectionsResult anyRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0)
                .durationSeconds(60.0)
                .geometry("")
                .steps(List.of())
                .build();
        when(orsRoutingClient.fetchDirections(any(), any(), any(), any(), any())).thenReturn(anyRoute);

        List<RouteResponse> routes = routeService.getRouteOptions(
                new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING));

        boolean hasAccessibleRoute = routes.stream()
                .anyMatch(r -> "Accessible Route".equals(r.getRouteLabel()));
        assertTrue(hasAccessibleRoute,
                "Accessible Route should be returned whenever avoid polygons exist, "
                        + "even if the fastest route has no obstacles on it. Routes: " + routes);
    }

    @Test
    void accessibleRoute_isEmitted_whenAvoidStairsActiveEvenWithoutPolygons() {
        // User opted into AVOID_STAIRS but no obstacle reports nearby — buildAvoidPolygons
        // returns null (Mockito default). Pre-fix the Accessible Route was suppressed
        // because the gate only checked avoidPolygons. With ORS-native avoid_features in
        // play, the Accessible Route IS meaningfully different from Fastest and must
        // be emitted.
        RoutingDirectionsResult anyRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0)
                .durationSeconds(60.0)
                .geometry("")
                .steps(List.of())
                .build();
        when(orsRoutingClient.fetchDirections(any(), any(), any(), any(), any())).thenReturn(anyRoute);

        List<RouteResponse> routes = routeService.getRouteOptions(
                new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING),
                EnumSet.of(RoutingConstraint.AVOID_STAIRS),
                TravelMode.WALKING);

        boolean hasAccessibleRoute = routes.stream()
                .anyMatch(r -> "Accessible Route".equals(r.getRouteLabel()));
        assertTrue(hasAccessibleRoute,
                "Accessible Route must be emitted when AVOID_STAIRS is active even without polygons. "
                        + "Routes: " + routes);
    }

    @Test
    void walkingUserWithAvoidStairs_takesRampAssistedRoute_whenItIsShorterThanDirect() {
        // Setup: walking user, AVOID_STAIRS active. The fastest walking route exists,
        // a FEATURE ramp report covers part of it, and routing through the ramp
        // (legs 3×100m = 300m) beats the direct stair-avoiding accessible route (500m).
        Report ramp = new Report();
        ramp.setEntryPoint(new Location(41.0840, 29.0460));
        ramp.setExitPoint(new Location(41.0830, 29.0480));
        when(obstacleService.findRampOnPath(any())).thenReturn(ramp);

        // Direct accessible WALKING route — long detour around the stairs
        RoutingDirectionsResult directAccessible = RoutingDirectionsResult.builder()
                .distanceMeters(500.0).durationSeconds(360.0).geometry("").steps(List.of()).build();
        // Each ramp-leg — sum is 300m, beats the direct
        RoutingDirectionsResult legRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0).durationSeconds(60.0).geometry("").steps(List.of()).build();
        // Fastest walking route (drives findRampOnPath input) — geometry must decode to ≥ 2 pts
        RoutingDirectionsResult fastest = RoutingDirectionsResult.builder()
                .distanceMeters(800.0).durationSeconds(540.0).geometry("_p~iG~psG_p~iG~psG").steps(List.of()).build();

        // 1st call: Fastest WALKING (no constraints) → fastest
        // 2nd call: Direct Accessible WALKING (with AVOID_STAIRS) → directAccessible
        // 3rd call: leg1 WALKING (with AVOID_STAIRS) → legRoute
        // 4th call: leg2 WALKING (unfiltered) → legRoute
        // 5th call: leg3 WALKING (with AVOID_STAIRS) → legRoute
        when(orsRoutingClient.fetchDirections(any(), any(), eq(TravelMode.WALKING), any(), any()))
                .thenReturn(fastest)
                .thenReturn(directAccessible)
                .thenReturn(legRoute)
                .thenReturn(legRoute)
                .thenReturn(legRoute);

        List<RouteResponse> routes = routeService.getRouteOptions(
                new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING),
                EnumSet.of(RoutingConstraint.AVOID_STAIRS),
                TravelMode.WALKING);

        RouteResponse accessible = routes.stream()
                .filter(r -> "Accessible Route".equals(r.getRouteLabel()))
                .findFirst()
                .orElse(null);
        assertTrue(accessible != null,
                "Accessible Route must be emitted. Routes: " + routes);
        // Ramp-assisted (300m) wins over direct accessible (500m)
        org.junit.jupiter.api.Assertions.assertEquals(300.0, accessible.getDistanceMeters(), 0.01,
                "Accessible Route must be the ramp-assisted multi-leg (300m), not the direct (500m)");
    }

    @Test
    void walkingUserWithRequireRamps_takesRampAssistedRoute_whenItIsShorter() {
        // REQUIRE_RAMPS is the constraint whose label literally promises "Prefer routes
        // with ramps over steps" — ramp-assisted routing should fire on it too, not
        // only on AVOID_STAIRS. (Otherwise MOBILITY_LIMITED preset users — which include
        // REQUIRE_RAMPS but not AVOID_STAIRS — never get positive ramp routing.)
        Report ramp = new Report();
        ramp.setEntryPoint(new Location(41.0840, 29.0460));
        ramp.setExitPoint(new Location(41.0830, 29.0480));
        when(obstacleService.findRampOnPath(any())).thenReturn(ramp);

        // Avoid polygons must exist so the Accessible Route branch is entered at all
        // (REQUIRE_RAMPS produces no native filters today, only polygons via reports).
        ObjectNode avoidPolygons = JsonMapper.builder().build().createObjectNode();
        when(obstacleService.buildAvoidPolygons(any())).thenReturn(avoidPolygons);

        RoutingDirectionsResult directAccessible = RoutingDirectionsResult.builder()
                .distanceMeters(500.0).durationSeconds(360.0).geometry("").steps(List.of()).build();
        RoutingDirectionsResult legRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0).durationSeconds(60.0).geometry("").steps(List.of()).build();
        RoutingDirectionsResult fastest = RoutingDirectionsResult.builder()
                .distanceMeters(800.0).durationSeconds(540.0).geometry("_p~iG~psG_p~iG~psG").steps(List.of()).build();

        when(orsRoutingClient.fetchDirections(any(), any(), eq(TravelMode.WALKING), any(), any()))
                .thenReturn(fastest)         // 1. Fastest
                .thenReturn(directAccessible)// 2. Direct Accessible
                .thenReturn(legRoute)        // 3. leg1
                .thenReturn(legRoute)        // 4. leg2
                .thenReturn(legRoute);       // 5. leg3

        List<RouteResponse> routes = routeService.getRouteOptions(
                new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING),
                EnumSet.of(RoutingConstraint.REQUIRE_RAMPS),
                TravelMode.WALKING);

        RouteResponse accessible = routes.stream()
                .filter(r -> "Accessible Route".equals(r.getRouteLabel()))
                .findFirst()
                .orElse(null);
        assertTrue(accessible != null,
                "Accessible Route must be emitted for REQUIRE_RAMPS users. Routes: " + routes);
        org.junit.jupiter.api.Assertions.assertEquals(300.0, accessible.getDistanceMeters(), 0.01,
                "Accessible Route must be the ramp-assisted multi-leg (300m), not the direct (500m)");
    }

    @Test
    void walkingUserWithAvoidStairs_keepsDirectAccessibleRoute_whenNoRampReportFound() {
        // No ramp reported on the fastest path → buildRampAssistedRoute returns null,
        // so the Accessible Route stays the direct foot-walking + avoid_features result.
        when(obstacleService.findRampOnPath(any())).thenReturn(null);

        RoutingDirectionsResult anyRoute = RoutingDirectionsResult.builder()
                .distanceMeters(250.0).durationSeconds(180.0).geometry("_p~iG~psG_p~iG~psG").steps(List.of()).build();
        when(orsRoutingClient.fetchDirections(any(), any(), any(), any(), any())).thenReturn(anyRoute);

        List<RouteResponse> routes = routeService.getRouteOptions(
                new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING),
                EnumSet.of(RoutingConstraint.AVOID_STAIRS),
                TravelMode.WALKING);

        RouteResponse accessible = routes.stream()
                .filter(r -> "Accessible Route".equals(r.getRouteLabel()))
                .findFirst()
                .orElse(null);
        assertTrue(accessible != null && accessible.getDistanceMeters() == 250.0,
                "Accessible Route must be the direct result (250m) when no ramp report exists. Routes: " + routes);
    }

    @Test
    void rampAssistedRoute_isSkippedGracefully_whenLeg2WalkingCallFails() {
        // A ramp candidate exists, so the multi-leg branch is entered
        Report ramp = new Report();
        ramp.setEntryPoint(new Location(41.0840, 29.0460));
        ramp.setExitPoint(new Location(41.0830, 29.0480));
        when(obstacleService.findRampOnPath(any())).thenReturn(ramp);

        // Avoid polygons exist so the WHEELCHAIR calls receive a non-null arg
        ObjectNode avoidPolygons = JsonMapper.builder().build().createObjectNode();
        when(obstacleService.buildAvoidPolygons(any())).thenReturn(avoidPolygons);

        // Direct wheelchair + leg1 + leg3 (all WHEELCHAIR) succeed
        RoutingDirectionsResult wheelchairRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0)
                .durationSeconds(60.0)
                .geometry("")
                .steps(List.of())
                .build();
        when(orsRoutingClient.fetchDirections(any(), any(), eq(TravelMode.WHEELCHAIR), any(), any()))
                .thenReturn(wheelchairRoute);

        // Fastest WALKING route must succeed so the ramp-on-path search is triggered.
        // Geometry "_p~iG~psG_p~iG~psG" decodes to 2 points, satisfying pathPoints.size() >= 2.
        RoutingDirectionsResult walkingRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0)
                .durationSeconds(60.0)
                .geometry("_p~iG~psG_p~iG~psG")
                .steps(List.of())
                .build();

        // 1st call: Fastest Route (WALKING) -> succeeds
        // 2nd call: Accessible Route (WALKING) -> succeeds
        // 3rd call: Leg 2 of Wheelchair (WALKING) -> fails
        when(orsRoutingClient.fetchDirections(any(), any(), eq(TravelMode.WALKING), any(), any()))
                .thenReturn(walkingRoute)
                .thenReturn(walkingRoute)
                .thenThrow(new RuntimeException("ors down"));

        // Should not NPE on leg2.getDistanceMeters() inside the ramp branch
        List<RouteResponse> routes = assertDoesNotThrow(() ->
                routeService.getRouteOptions(
                        new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING)));

        // The Wheelchair Route should still be present (from the direct wheelchair candidate),
        // proving the multi-leg candidate was dropped silently rather than crashing the request.
        boolean hasRampAssistedRoute = routes.stream()
                .anyMatch(r -> "Wheelchair Route".equals(r.getRouteLabel()));
        assertTrue(hasRampAssistedRoute,
                "Direct wheelchair Wheelchair Route should remain when leg 2 fails. Routes: " + routes);
    }
}
