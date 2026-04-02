package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final OrsRoutingClient orsRoutingClient;
    private final ObstacleAvoidanceService obstacleAvoidanceService;

    public List<RouteResponse> getRouteOptions(RouteRequest request) {
        Location start = new Location(request.getStartLat(), request.getStartLon());
        Location end = new Location(request.getEndLat(), request.getEndLon());

        ObjectNode avoidPolygons = obstacleAvoidanceService.buildAvoidPolygons();
        RoutingDirectionsResult result =
                orsRoutingClient.fetchDirections(start, end, request.getMode(), avoidPolygons);

        RouteResponse route = RouteResponse.builder()
                .routeLabel("Recommended Route")
                .distanceMeters(result.getDistanceMeters())
                .durationSeconds(result.getDurationSeconds())
                .mode(request.getMode())
                .geometry(result.getGeometry())
                .steps(result.getSteps())
                .build();

        return List.of(route);
    }
}
