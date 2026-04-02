package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.routing.RouteRequest;
import com.bounswe2026group1.backend.dto.routing.RouteResponse;
import com.bounswe2026group1.backend.dto.routing.RoutingDirectionsResult;
import com.bounswe2026group1.backend.model.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final OrsRoutingClient orsRoutingClient;
    private final ObstacleService obstacleService;

    public List<RouteResponse> getRouteOptions(RouteRequest request) {
        Location start = new Location(request.getStartLat(), request.getStartLon());
        Location end = new Location(request.getEndLat(), request.getEndLon());

        // 1. Fetch fastest route — no obstacle avoidance
        RoutingDirectionsResult result =
                orsRoutingClient.fetchDirections(start, end, request.getMode(), null);

        // 2. Decode the polyline and check for negative reports on the path
        List<Location> pathPoints = PolylineDecoder.decode(result.getGeometry());
        boolean hasObstacles = !obstacleService.findObstaclesOnPath(pathPoints).isEmpty();

        RouteResponse route = RouteResponse.builder()
                .routeLabel("Fastest Route")
                .distanceMeters(result.getDistanceMeters())
                .durationSeconds(result.getDurationSeconds())
                .mode(request.getMode())
                .geometry(result.getGeometry())
                .steps(result.getSteps())
                .hasObstacles(hasObstacles)
                .build();

        return List.of(route);
    }
}
