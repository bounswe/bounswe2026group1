package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RampReport;
import com.bounswe2026group1.backend.model.TravelMode;
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
        when(obstacleService.buildAvoidPolygons()).thenReturn(avoidPolygons);

        // But the fastest route happens not to cross any of them
        when(obstacleService.findObstaclesOnPath(any())).thenReturn(List.of());
        when(obstacleService.findClosestRampInBoundingBox(any(), any())).thenReturn(null);

        RoutingDirectionsResult anyRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0)
                .durationSeconds(60.0)
                .geometry("")
                .steps(List.of())
                .build();
        when(orsRoutingClient.fetchDirections(any(), any(), any(), any())).thenReturn(anyRoute);

        List<RouteResponse> routes = routeService.getRouteOptions(
                new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING));

        boolean hasAccessibleRoute = routes.stream()
                .anyMatch(r -> "Accessible Route".equals(r.getRouteLabel()));
        assertTrue(hasAccessibleRoute,
                "Accessible Route should be returned whenever avoid polygons exist, "
                        + "even if the fastest route has no obstacles on it. Routes: " + routes);
    }

    @Test
    void rampAssistedRoute_isSkippedGracefully_whenLeg2WalkingCallFails() {
        // A ramp candidate exists, so the multi-leg branch is entered
        RampReport ramp = new RampReport();
        ramp.setEntryPoint(new Location(41.0840, 29.0460));
        ramp.setExitPoint(new Location(41.0830, 29.0480));
        when(obstacleService.findClosestRampInBoundingBox(any(), any())).thenReturn(ramp);

        // Avoid polygons exist so the WHEELCHAIR calls receive a non-null arg
        ObjectNode avoidPolygons = JsonMapper.builder().build().createObjectNode();
        when(obstacleService.buildAvoidPolygons()).thenReturn(avoidPolygons);

        // Direct wheelchair + leg1 + leg3 (all WHEELCHAIR) succeed
        RoutingDirectionsResult wheelchairRoute = RoutingDirectionsResult.builder()
                .distanceMeters(100.0)
                .durationSeconds(60.0)
                .geometry("")
                .steps(List.of())
                .build();
        when(orsRoutingClient.fetchDirections(any(), any(), eq(TravelMode.WHEELCHAIR), any()))
                .thenReturn(wheelchairRoute);

        // All WALKING calls fail — fetchOrNull catches and returns null. The one we care about
        // is leg 2 (rampEntry → rampExit); the fastest/accessible WALKING calls also failing is
        // incidental and doesn't affect what we assert.
        when(orsRoutingClient.fetchDirections(any(), any(), eq(TravelMode.WALKING), any()))
                .thenThrow(new RuntimeException("ors down"));

        // Should not NPE on leg2.getDistanceMeters() inside the ramp branch
        List<RouteResponse> routes = assertDoesNotThrow(() ->
                routeService.getRouteOptions(
                        new RouteRequest(41.0850, 29.0450, 41.0820, 29.0500, TravelMode.WALKING)));

        // The Ramp-Assisted Route should still be present (from the direct wheelchair candidate),
        // proving the multi-leg candidate was dropped silently rather than crashing the request.
        boolean hasRampAssistedRoute = routes.stream()
                .anyMatch(r -> "Ramp-Assisted Route".equals(r.getRouteLabel()));
        assertTrue(hasRampAssistedRoute,
                "Direct wheelchair Ramp-Assisted Route should remain when leg 2 fails. Routes: " + routes);
    }
}
