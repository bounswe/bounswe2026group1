package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.Report;
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
